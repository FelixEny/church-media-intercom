package com.churchmedia.intercom.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.churchmedia.intercom.session.AppState
import com.churchmedia.intercom.ui.components.SectionCard
import com.churchmedia.intercom.ui.components.StatusPill
import com.churchmedia.intercom.ui.components.Tone
import com.churchmedia.intercom.ui.components.VerticalSpacer
import com.churchmedia.intercom.ui.theme.CommsColors

@Composable
fun SettingsScreen(
    state: AppState,
    onToggleNoiseSuppression: (Boolean) -> Unit,
    onVolume: (Float) -> Unit,
    onSelectOutput: (Int) -> Unit,
    onRefreshOutputs: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDone) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = CommsColors.TextSecondary)
            }
            Spacer(Modifier.size(4.dp))
            Text("Audio", style = MaterialTheme.typography.headlineMedium)
        }

        VerticalSpacer(16)

        SectionCard(title = "Noise suppression") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Suppress room noise", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Cuts music, hum, and side conversation before your voice leaves the phone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CommsColors.TextSecondary,
                    )
                }
                Switch(
                    checked = state.noiseSuppression,
                    onCheckedChange = onToggleNoiseSuppression,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            VerticalSpacer(10)
            StatusPill(
                text = if (state.processingSummary.isBlank()) "Not measured yet" else state.processingSummary,
                tone = Tone.Neutral,
            )
            VerticalSpacer(6)
            Text(
                "The line above is what the phone reports it is actually running — not just the switch position.",
                style = MaterialTheme.typography.labelMedium,
                color = CommsColors.TextSecondary,
            )
        }

        VerticalSpacer(12)

        SectionCard(title = "Incoming volume") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = state.volume,
                    onValueChange = onVolume,
                    valueRange = 0.2f..2f,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${(state.volume * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = CommsColors.TextSecondary,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Text(
                "Above 100% the app amplifies digitally. It may distort on a quiet phone speaker.",
                style = MaterialTheme.typography.labelMedium,
                color = CommsColors.TextSecondary,
            )
        }

        VerticalSpacer(12)

        SectionCard(title = "Audio output") {
            Text(
                "Microphone: ${state.inputLabel ?: "Phone microphone"}",
                style = MaterialTheme.typography.bodyMedium,
                color = CommsColors.TextSecondary,
            )
            VerticalSpacer(8)
            if (state.outputs.isEmpty()) {
                Text(
                    "No outputs detected. Connect a headset or refresh.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CommsColors.TextSecondary,
                )
            } else {
                state.outputs.forEach { device ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = state.outputId == device.id,
                            onClick = { onSelectOutput(device.id) },
                        )
                        Text(
                            device.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            VerticalSpacer(6)
            TextButton(onClick = onRefreshOutputs) { Text("Refresh devices") }
        }

        VerticalSpacer(12)

        SectionCard(title = "Connection") {
            SettingRow("Role", if (state.mode.name.lowercase() == "idle") "Not in a session" else state.mode.name.lowercase())
            SettingRow("Session code", state.sessionCode.ifBlank { "—" })
            SettingRow(
                "Address",
                if (state.mode.name.lowercase() == "director") {
                    "${state.localAddress}:${state.port}"
                } else {
                    state.hostAddress.ifBlank { "—" }
                },
            )
        }

        VerticalSpacer(20)

        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) { Text("Done", style = MaterialTheme.typography.labelLarge) }
    }
}

@Composable
private fun SettingRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = CommsColors.TextSecondary)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
