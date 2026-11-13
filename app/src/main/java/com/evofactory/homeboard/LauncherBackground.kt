package com.evofactory.homeboard

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.tv.material3.MaterialTheme
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * What sits behind every screen: nothing (the launcher's own dark background), one colour, or one image
 * from the TV's storage, drawn stretched, filled, fitted, tiled or centred.
 *
 * Every surface above it is dark and drawn over it, with a scrim in between whose strength is a setting,
 * so a bright picture can never leave white text on white. The launcher has one scheme, dark, and this
 * is decoration behind it — not a light theme.
 */
@Composable
fun LauncherBackgroundLayer(prefs: LauncherPrefs) {
    val kind = prefs.backgroundKind
    val scrim = prefs.backgroundScrim
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.backgroundBase(kind, prefs))) {
        when (kind) {
            BACKGROUND_COLOUR -> Box(Modifier.fillMaxSize().background(Color(prefs.backgroundColour)))
            BACKGROUND_IMAGE -> BackgroundImage(prefs.backgroundImage, prefs.backgroundFit)
        }
        // Readable text whatever is behind it
        if (scrim > 0) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = scrim / 100f)))
    }
}

/** The base colour under everything: the launcher's own background unless a colour was chosen */
private fun androidx.tv.material3.ColorScheme.backgroundBase(kind: String, prefs: LauncherPrefs): Color =
    if (kind == BACKGROUND_COLOUR) Color(prefs.backgroundColour) else background

/** The image, with the file read once per path and the fit applied as chosen */
@Composable
private fun BackgroundImage(path: String, fit: String) {
    val picture by produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            if (path.isEmpty()) return@withContext null
            val file = File(path)
            if (!file.exists()) return@withContext null
            runCatching { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }.getOrNull()
        }
    }
    val image = picture ?: return
    when (fit) {
        BACKGROUND_TILE -> Canvas(Modifier.fillMaxSize()) {
            // A tile of the picture, repeated: the one fit Compose has no ContentScale for
            val bitmap = image.asAndroidBitmap()
            val shader = android.graphics.BitmapShader(bitmap, android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT)
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawPaint(
                    android.graphics.Paint().apply { this.shader = shader },
                )
            }
        }
        else -> Image(
            bitmap = image,
            contentDescription = null,
            contentScale = when (fit) {
                BACKGROUND_STRETCH -> ContentScale.FillBounds
                BACKGROUND_FIT -> ContentScale.Fit
                BACKGROUND_CENTRE -> ContentScale.None
                else -> ContentScale.Crop
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** The colours the background picker offers: dark first, so a slip of the remote stays readable */
val BackgroundColours = listOf(
    "Black" to 0xFF000000.toInt(),
    "Charcoal" to 0xFF1C1B1F.toInt(),
    "Slate" to 0xFF1F2A37.toInt(),
    "Navy" to 0xFF101C33.toInt(),
    "Forest" to 0xFF12251A.toInt(),
    "Plum" to 0xFF24122B.toInt(),
    "Maroon" to 0xFF2B1216.toInt(),
    "Teal" to 0xFF0E2A2A.toInt(),
    "Grey" to 0xFF3A3A3F.toInt(),
    "Sand" to 0xFFD8C9A8.toInt(),
    "Sky" to 0xFFBBD7E8.toInt(),
    "Bone" to 0xFFEDE8DE.toInt(),
)
