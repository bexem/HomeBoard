package com.evofactory.homeboard

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * The address, and nothing else: the settings page keeps the connection in rows, so this is only where
 * the typing happens. Changing it ends the saved login, which belonged to the old address.
 */
@Composable
fun HaAddressDialog(prefs: LauncherPrefs, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf(prefs.haUrl) }
    val field = remember { FocusRequester() }
    val normalised = normalizeHaUrl(url)
    TopDialog(
        title = "Home Assistant address",
        onDismiss = onDismiss,
        buttons = listOf(
            DialogButton("Cancel", onClick = onDismiss),
            DialogButton("Save", primary = true, enabled = normalised != null && normalised != prefs.haUrl) {
                val address = normalised ?: return@DialogButton
                prefs.haUrl = address
                // The login belonged to the old address
                prefs.haToken = ""
                prefs.haRefreshToken = ""
                onDismiss()
            },
        ),
        contentFocus = { field },
    ) {
        TvTextField(
            value = url,
            onValueChange = { url = it },
            placeholder = "http://192.168.1.20:8123",
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Done,
            onImeAction = { normalised?.let { prefs.haUrl = it; prefs.haToken = ""; prefs.haRefreshToken = ""; onDismiss() } },
            modifier = Modifier.fillMaxWidth().padding(12.dp).focusRequester(field),
        )
        Text(
            if (normalised == null) "An address like 192.168.1.20 or http://homeassistant.local:8123" else "Will be saved as $normalised",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}
