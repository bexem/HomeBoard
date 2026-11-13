package com.evofactory.homeboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Services that have no entity of their own — `input_number.reload`, `automation.reload` — chosen from
 * the server's own list, never typed. The list is read when the dialog opens, so a service an
 * integration adds needs no rebuild. The rows are the shared picker's, so OK says what it does.
 */
@Composable
fun HaServicesDialog(
    server: HaServer,
    chosen: List<String>,
    onChange: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var found by remember { mutableStateOf<Map<String, List<String>>?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(server) {
        if (!server.isSet) {
            found = emptyMap()
            return@LaunchedEffect
        }
        try {
            found = haSession(server) { services() }
        } catch (e: HaException) {
            problem = e.message ?: "Home Assistant didn't answer"
            found = emptyMap()
        }
    }

    val services = found.orEmpty()
    // Every service, in the server's own order, named "domain.service": the domain as the label and
    // the service under it, so a long list still reads as one subject
    val rows = remember(services) {
        services.keys.sorted().flatMap { domain ->
            services.getValue(domain).map { service ->
                PickerRow("$domain.$service", domain, service)
            }
        }
    }
    EntityPickerDialog(
        title = "Custom services",
        noun = "service",
        rows = rows,
        chosen = chosen,
        includeLabel = "Add it",
        excludeLabel = "Leave it out",
        positionLabel = { "Position $it" },
        // The line under the list, and nothing chosen: the picker prints it under an empty list as the
        // reason there is nothing, and as the nought-chosen line when there is a list to choose from
        emptyText = when {
            problem != null -> "No services to choose: $problem"
            found == null -> "Reading the services from Home Assistant…"
            rows.isEmpty() -> "Home Assistant reports no services."
            else -> "Nothing chosen: no service button appears on the Assistant tab."
        },
        subtitle = { count -> "$count chosen, called from the Assistant tab's Services card" },
        onChange = onChange,
        onDismiss = onDismiss,
    )
}
