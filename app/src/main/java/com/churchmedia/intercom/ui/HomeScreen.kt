package com.churchmedia.intercom.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.churchmedia.intercom.ui.components.SectionCard
import com.churchmedia.intercom.ui.components.StatusPill
import com.churchmedia.intercom.ui.components.Tone
import com.churchmedia.intercom.ui.components.VerticalSpacer
import com.churchmedia.intercom.ui.theme.CommsColors

@Composable
fun HomeScreen(
    initialName: String,
    localAddress: String,
    onStartHosting: (String) -> Unit,
    onJoin: () -> Unit,
    onSettings: () -> Unit,
) {
    var showHostDialog by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf(initialName) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusPill("Local network only", Tone.Accent)
            IconButton(onClick = onSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = "Audio settings", tint = CommsColors.TextSecondary)
            }
        }

        VerticalSpacer(28)
        Text("Comms", style = MaterialTheme.typography.displaySmall, color = CommsColors.TextPrimary)
        VerticalSpacer(8)
        Text(
            "Private voice intercom for the media team. Nothing leaves this Wi-Fi.",
            style = MaterialTheme.typography.bodyLarge,
            color = CommsColors.TextSecondary,
        )

        VerticalSpacer(28)

        SectionCard(title = "Director") {
            Text(
                "Host the session, choose who hears you, and talk to one person or everyone.",
                style = MaterialTheme.typography.bodyMedium,
                color = CommsColors.TextSecondary,
            )
            VerticalSpacer(14)
            Button(
                onClick = { showHostDialog = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Rounded.Podcasts, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text("Start hosting", style = MaterialTheme.typography.labelLarge)
            }
        }

        VerticalSpacer(14)

        SectionCard(title = "Team member") {
            Text(
                "Join the director's session and reply privately when you need to.",
                style = MaterialTheme.typography.bodyMedium,
                color = CommsColors.TextSecondary,
            )
            VerticalSpacer(14)
            Button(
                onClick = onJoin,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CommsColors.SurfaceHigh,
                    contentColor = CommsColors.TextPrimary,
                ),
            ) {
                Text("Join a session", style = MaterialTheme.typography.labelLarge)
            }
        }

        VerticalSpacer(28)

        Text(
            if (localAddress.isNotBlank()) "This device · $localAddress" else "Not on Wi-Fi right now",
            style = MaterialTheme.typography.bodyMedium,
            color = CommsColors.TextSecondary,
        )
        VerticalSpacer(4)
        Text(
            "Audio is end-to-end encrypted between phones and never routed through a server.",
            style = MaterialTheme.typography.labelMedium,
            color = CommsColors.TextSecondary,
        )
    }

    if (showHostDialog) {
        AlertDialog(
            onDismissRequest = { showHostDialog = false },
            title = { Text("Start a session") },
            text = {
                Column {
                    Text(
                        "Your name appears on every member's screen.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CommsColors.TextSecondary,
                    )
                    VerticalSpacer(12)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(24) },
                        label = { Text("Your name") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showHostDialog = false
                        onStartHosting(name.trim().ifBlank { "Director" })
                    },
                ) { Text("Start") }
            },
            dismissButton = {
                TextButton(onClick = { showHostDialog = false }) { Text("Cancel") }
            },
        )
    }
}
