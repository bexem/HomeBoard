package com.evofactory.homeboard

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

/**
 * Home Assistant as the camera source: for a TV that may reach Home Assistant but must not be given
 * the cameras' own addresses (the Frigate UI, go2rtc, the RTSP logins). Nothing here talks to a
 * camera: the TV asks Home Assistant, which does.
 *
 * A live picture is Home Assistant's HLS stream (`camera/stream`), one camera at a time; everything
 * else is Home Assistant's JPEG snapshot (`camera/proxy`), which is cheap and works for any camera
 * integration. That split is what makes six tiles possible over one connection.
 */
data class HaCamera(val entityId: String, val name: String)

/** Storage form: "entity|name", so the launcher has the name without reading the states again */
fun haCamerasToPrefs(cameras: List<HaCamera>): List<String> = cameras.map { "${it.entityId}|${it.name}" }

fun haCamerasFromPrefs(lines: List<String>): List<HaCamera> = lines.mapNotNull { line ->
    val id = line.substringBefore('|').trim()
    if (id.isEmpty()) null else HaCamera(id, line.substringAfter('|', id.substringAfter('.')))
}

/**
 * The HLS playlist for [entityId], as Home Assistant reports it, or null when it won't (an older
 * Home Assistant, a camera without stream support, a refused login).
 */
suspend fun haCameraStreamUrl(server: HaServer, entityId: String): String? = try {
    haSession(server) {
        val result = command("camera/stream") {
            put("entity_id", entityId)
            put("format", "hls")
        } as? JSONObject
        result?.optString("url")?.takeIf { it.isNotEmpty() }?.let { path ->
            AppLog.i("Camera", "Home Assistant stream for $entityId: ${path.substringBefore('?')}")
            if (path.startsWith("http")) path else "${server.url}$path"
        }
    }
} catch (e: HaException) {
    AppLog.w("Camera", "camera/stream for $entityId: ${e.message}")
    null
}

/**
 * Home Assistant's JPEG of [entityId], refreshed while this tile is on screen. This is the cheap path:
 * a still costs one small request, where a live HLS stream costs a transcode on Home Assistant and a
 * decoder on the TV — so a grid of six shows one live picture and five of these.
 */
@androidx.compose.runtime.Composable
fun rememberHaCameraStill(server: HaServer, entityId: String, refreshMs: Long = 10_000L): ImageBitmap? {
    val state = androidx.compose.runtime.produceState<ImageBitmap?>(initialValue = null, entityId, refreshMs) {
        while (true) {
            haCameraStill(server, entityId)?.let { value = it }
            kotlinx.coroutines.delay(refreshMs)
        }
    }
    return state.value
}

/** Home Assistant's JPEG of [entityId] right now, or null */
suspend fun haCameraStill(server: HaServer, entityId: String): ImageBitmap? = withContext(Dispatchers.IO) {
    val url = "${server.url}/api/camera_proxy/${URLEncoder.encode(entityId, "UTF-8")}"
    val request = Request.Builder()
        .url(url)
        .header("Authorization", "Bearer ${server.accessToken()}")
        .build()
    runCatching {
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            response.body.bytes().let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
        }
    }.getOrNull()
}
