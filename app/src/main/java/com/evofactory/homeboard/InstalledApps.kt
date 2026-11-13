package com.evofactory.homeboard

import android.graphics.BitmapFactory
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import android.provider.Settings
import android.util.LruCache
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import org.json.JSONArray
import org.json.JSONObject

data class InstalledApp(
    val label: String,
    val component: ComponentName,
    val isTvApp: Boolean,
) {
    /** Package name: stable across app updates, and there's one tile per package */
    val key: String get() = component.packageName
}

fun installedAppsToJson(apps: List<InstalledApp>): String = JSONArray(
    apps.map {
        JSONObject().put("label", it.label).put("package", it.component.packageName)
            .put("activity", it.component.className).put("tv", it.isTvApp)
    },
).toString()

fun installedAppsFromJson(json: String): List<InstalledApp> {
    if (json.isEmpty()) return emptyList()
    val array = JSONArray(json)
    return List(array.length()) { i ->
        array.getJSONObject(i).let {
            InstalledApp(it.getString("label"), ComponentName(it.getString("package"), it.getString("activity")), it.getBoolean("tv"))
        }
    }
}

/** A 16:9 TV banner when the app has one, otherwise its square launcher icon. */
class TileImage(val bitmap: ImageBitmap, val isBanner: Boolean)

/**
 * Lists every launchable app: TV apps (LEANBACK_LAUNCHER) first-class, plus phone apps
 * (LAUNCHER) so sideloaded apps such as Home Assistant also show up.
 * Only labels are read here; images are loaded per tile by [loadTileImage].
 */
fun loadInstalledApps(context: Context): List<InstalledApp> {
    val pm = context.packageManager

    fun query(category: String): List<ResolveInfo> =
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(category), 0)

    val tvActivities = query(Intent.CATEGORY_LEANBACK_LAUNCHER)
    val tvPackages = tvActivities.map { it.activityInfo.packageName }.toSet()
    val phoneActivities = query(Intent.CATEGORY_LAUNCHER)
        .filter { it.activityInfo.packageName !in tvPackages }

    return (tvActivities.map { it to true } + phoneActivities.map { it to false })
        .filter { (info, _) -> info.activityInfo.packageName != context.packageName }
        .distinctBy { (info, _) -> info.activityInfo.packageName }
        .map { (info, isTv) ->
            val activity = info.activityInfo
            InstalledApp(
                label = info.loadLabel(pm).toString(),
                component = ComponentName(activity.packageName, activity.name),
                isTvApp = isTv,
            )
        }
        .sortedBy { it.label.lowercase() }
}

// Decoded tile images survive list refreshes, so returning to the launcher is instant
private val tileImageCache = LruCache<String, TileImage>(100)

/**
 * Icons the owner chose for an app, by package, as "package=file" entries. Set once when the launcher
 * starts and whenever one is picked, so the tiles keep loading through the same path they always did.
 */
var appIconOverrides: Map<String, String> = emptyMap()

/** Forget a decoded icon, so the app's own art shows again after a reset */
fun tileImageCacheInvalidate(key: String) {
    tileImageCache.remove(key)
    tileImageCache.remove("override:$key")
}

/** A chosen icon, decoded and cached beside the app's own images */
fun appIconOverride(app: InstalledApp): TileImage? = appIconOverrides[app.key]?.let { path ->
    tileImageCache.get("override:" + app.key) ?: runCatching {
        BitmapFactory.decodeFile(path)?.let { bitmap ->
            // Scaled down once: a phone photo as an icon would cost memory in every row
            val tall = 288f / bitmap.height
            val wide = 512f / bitmap.width
            val scale = maxOf(tall, wide)
            val scaled = if (scale >= 1f) bitmap else android.graphics.Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true,
            )
            TileImage(scaled.asImageBitmap(), isBanner = true)
        }?.also { tileImageCache.put("override:" + app.key, it) }
    }.onFailure { AppLog.w("Icons", "Can't decode the chosen icon for ${app.key}", it) }.getOrNull()
}

fun cachedTileImage(app: InstalledApp): TileImage? = appIconOverride(app) ?: tileImageCache[app.key]

/** Slow (decodes resources from the other app's APK): call off the main thread. */
fun loadTileImage(context: Context, app: InstalledApp): TileImage? {
    cachedTileImage(app)?.let { return it }
    val pm = context.packageManager
    val activity = runCatching { pm.getActivityInfo(app.component, 0) }.getOrNull() ?: return null
    // ComponentInfo.loadBanner falls back to the application's banner
    val image = activity.loadBanner(pm)
        ?.let { TileImage(it.toBitmap(width = 320, height = 180).asImageBitmap(), isBanner = true) }
        ?: TileImage(activity.loadIcon(pm).toBitmap(width = 160, height = 160).asImageBitmap(), isBanner = false)
    tileImageCache.put(app.key, image)
    return image
}

fun Context.launch(app: InstalledApp) {
    val category = if (app.isTvApp) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER
    try {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(category)
                .setComponent(app.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: RuntimeException) {
        AppLog.w("InstalledApps", "Couldn't open ${app.key}", e)
        // e.g. being updated or just removed: the row hasn't caught up yet
        Toast.makeText(this, "Couldn't open ${app.label}", Toast.LENGTH_LONG).show()
    }
}

/** Built-in apps can't be uninstalled, only their updates, from their info screen */
fun Context.canUninstall(app: InstalledApp): Boolean = runCatching {
    packageManager.getApplicationInfo(app.key, 0).flags and ApplicationInfo.FLAG_SYSTEM == 0
}.getOrDefault(false)

/** Android's own screen for the app: force stop, clear data and so on */
fun Context.openAppInfo(app: InstalledApp) = startForApp(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app)

/** Android asks for confirmation first */
fun Context.uninstall(app: InstalledApp) = startForApp(Intent.ACTION_DELETE, app)

private fun Context.startForApp(action: String, app: InstalledApp) {
    try {
        startActivity(Intent(action, Uri.parse("package:${app.key}")))
    } catch (e: RuntimeException) {
        AppLog.w("InstalledApps", "Couldn't open $action for ${app.key}", e)
        Toast.makeText(this, "Couldn't open this for ${app.label}", Toast.LENGTH_LONG).show()
    }
}
