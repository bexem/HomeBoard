package com.evofactory.homeboard

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Where a settings file goes, and where one comes from.
 *
 * The system picker is the one to prefer: it can read and write Downloads whatever the app's own
 * storage access is, and it survives a reinstall (an app's own folder is wiped with the app, and a
 * file written by a previous install is no longer visible to the next one).
 *
 * "This TV's folders" is the launcher's own browser, which works on TVs whose picker is a stub.
 * "App folder" needs no interaction at all: it is where `adb push` can always write.
 */
@Composable
fun importSettings(onImport: (SettingsFile) -> Unit, onBrowseFiles: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) {
            onDismiss()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val file = withContext(Dispatchers.IO) { runCatching { readSettings(context, uri) }.onFailure { AppLog.w("Settings", "Can't read the settings file", it) }.getOrNull() }
            if (file != null) onImport(file) else Toast.makeText(context, "That file isn't a settings file", Toast.LENGTH_LONG).show()
            onDismiss()
        }
    }

    TopDialog(title = "Import settings", onDismiss = onDismiss) {
        Text(
            "A settings file holds this launcher's setup: cameras, Home Assistant, Frigate, what to show and every setting.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp),
        )
        TransferRow("Choose a file", "The TV's own file picker, where Downloads and USB keys are") {
            picker.launch(arrayOf("application/json", "text/plain", "*/*"))
        }
        TransferRow("Browse this TV's folders", "For TVs whose file picker is only a stub") { onBrowseFiles() }
        TransferRow("From the app folder", "For a file pushed to this app's folder with adb") {
            scope.launch {
                val file = withContext(Dispatchers.IO) { newestSettingsFile(context)?.let { readSettings(context, it) } }
                if (file != null) onImport(file) else Toast.makeText(context, "No settings file in the app folder", Toast.LENGTH_LONG).show()
                onDismiss()
            }
        }
    }
}

@Composable
fun exportSettings(
    file: SettingsFile,
    onBrowseFiles: () -> Unit,
    onDismiss: () -> Unit,
    // Saving closes the whole flow, not just this step
    onSaved: () -> Unit = onDismiss,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val suggested = remember { settingsFileName() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) {
            onDismiss()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { writeSettings(context, uri, file.toJson()) }.onFailure { AppLog.w("Settings", "Can't export the settings", it) }.isSuccess }
            Toast.makeText(context, if (ok) "Settings saved" else "Can't save there", Toast.LENGTH_LONG).show()
            if (ok) onSaved() else onDismiss()
        }
    }

    TopDialog(title = "Export settings", onDismiss = onDismiss) {
        Text(
            "The file isn't encrypted: it holds camera addresses with their passwords, the Home Assistant login " +
                "and the Frigate password. Keep it safe and delete it when you're done.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp),
        )
        TransferRow("Save with the file picker", "Downloads, a USB key, anywhere the TV can write") {
            picker.launch(suggested)
        }
        TransferRow("Browse this TV's folders", "For TVs whose file picker is only a stub") { onBrowseFiles() }
        TransferRow("Save to the app folder", "Ready for `adb pull`; no file picker at all") {
            scope.launch {
                val path = withContext(Dispatchers.IO) { runCatching { writeSettingsToAppFolder(context, suggested, file.toJson()) }.getOrNull() }
                Toast.makeText(context, path?.let { "Saved to $it" } ?: "Can't save to the app folder", Toast.LENGTH_LONG).show()
                if (path != null) onSaved() else onDismiss()
            }
        }
    }
}

@Composable
private fun TransferRow(title: String, description: String, onClick: () -> Unit) {
    ListItem(
        selected = false,
        onClick = onClick,
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
    )
}

private fun settingsFileName() =
    "homeboard-settings-${java.text.SimpleDateFormat("yyyy-MM-dd-HHmmss", java.util.Locale.ROOT).format(java.util.Date())}.json"

private fun appFolder(context: Context): File? = context.getExternalFilesDir("settings")?.let { dir ->
    if (dir.isDirectory || dir.mkdirs()) dir else null
}

private fun newestSettingsFile(context: Context): File? =
    appFolder(context)?.listFiles()
        ?.filter { it.isFile && it.name.endsWith(".json", ignoreCase = true) }
        ?.maxByOrNull { it.lastModified() }

private fun readSettings(context: Context, uri: Uri): SettingsFile? =
    context.contentResolver.openInputStream(uri)?.use { input ->
        input.readBytes().decodeToString().let(::settingsFromJson)
    }

private fun readSettings(context: Context, file: File): SettingsFile? =
    runCatching { settingsFromJson(file.readText()) }.getOrNull()

private fun writeSettings(context: Context, uri: Uri, json: String) {
    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
        ?: error("The picker returned a file that can't be written")
}

private fun writeSettingsToAppFolder(context: Context, name: String, json: String): String? {
    val dir = appFolder(context) ?: return null
    val file = File(dir, name)
    file.writeText(json)
    return file.path
}
