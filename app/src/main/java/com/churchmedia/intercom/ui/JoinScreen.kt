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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.churchmedia.intercom.ui.components.VerticalSpacer
import com.churchmedia.intercom.ui.theme.CommsColors

@Composable
fun JoinScreen(
    initialAddress: String,
    initialCode: String,
    initialName: String,
    error: String?,
    connecting: Boolean,
    onJoin: (address: String, code: String, name: String) -> Unit,
    onBack: () -> Unit,
) {
    var address by rememberSaveable { mutableStateOf(initialAddress) }
    var code by rememberSaveable { mutableStateOf(initialCode) }
    var name by rememberSaveable { mutableStateOf(initialName) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = CommsColors.TextSecondary)
            }
            Spacer(Modifier.size(4.dp))
            Text("Join a session", style = MaterialTheme.typography.headlineMedium)
        }

        VerticalSpacer(24)

        Text(
            "The director's screen shows the address and a 4-digit code. Both phones must be on the same Wi-Fi.",
            style = MaterialTheme.typography.bodyMedium,
            color = CommsColors.TextSecondary,
        )

        VerticalSpacer(24)

        OutlinedTextField(
            value = address,
            onValueChange = { address = it.trim().take(64) },
            label = { Text("Director's address") },
            placeholder = { Text("192.168.1.42") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        VerticalSpacer(12)

        OutlinedTextField(
            value = code,
            onValueChange = { input -> code = input.filter { it.isDigit() }.take(4) },
            label = { Text("Session code") },
            placeholder = { Text("4821") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        VerticalSpacer(12)

        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(24) },
            label = { Text("Your name") },
            placeholder = { Text("Ada") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )

        if (error != null) {
            VerticalSpacer(14)
            Text(error, style = MaterialTheme.typography.bodyMedium, color = CommsColors.Danger)
        }

        VerticalSpacer(24)

        Button(
            onClick = { onJoin(address, code, name) },
            enabled = !connecting,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(if (connecting) "Connecting…" else "Join session", style = MaterialTheme.typography.labelLarge)
        }
    }
}
