package com.churchmedia.intercom.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.churchmedia.intercom.core.model.FloorState
import com.churchmedia.intercom.core.model.LinkState
import com.churchmedia.intercom.session.AppState
import com.churchmedia.intercom.ui.components.BigMicButton
import com.churchmedia.intercom.ui.components.FloorBanner
import com.churchmedia.intercom.ui.components.LevelMeter
import com.churchmedia.intercom.ui.components.SectionCard
import com.churchmedia.intercom.ui.components.StatusPill
import com.churchmedia.intercom.ui.components.Tone
import com.churchmedia.intercom.ui.components.VerticalSpacer
import com.churchmedia.intercom.ui.theme.CommsColors

@Composable
fun MemberScreen(
    state: AppState,
    onToggleMic: () -> Unit,
    onSendThumbsUp: () -> Unit,
    onVolume: (Float) -> Unit,
    onEnd: () -> Unit,
    onSettings: () -> Unit,
) {
    val connected = state.link == LinkState.CONNECTED
    val hostName = state.director?.name ?: state.hostName.ifBlank { "Director" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(hostName, style = MaterialTheme.typography.titleLarge)
                Text(
                    "Session ${state.sessionCode}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CommsColors.TextSecondary,
                )
                Text(
                    "Output: ${state.outputLabel ?: "Speaker"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CommsColors.TextSecondary,
                )
                Text(
                    "Mic: ${state.inputLabel ?: "Phone microphone"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CommsColors.TextSecondary,
                )
            }
            Spacer(Modifier.weight(1f))
            StatusPill(
                text = when (state.link) {
                    LinkState.CONNECTED -> "Connected"
                    LinkState.CONNECTING -> "Connecting"
                    LinkState.RECONNECTING -> "Reconnecting"
                    LinkState.FAILED -> "Offline"
                    LinkState.IDLE -> "Idle"
                },
                tone = when (state.link) {
                    LinkState.CONNECTED -> Tone.Accent
                    LinkState.CONNECTING, LinkState.RECONNECTING -> Tone.Warning
                    LinkState.FAILED -> Tone.Danger
                    LinkState.IDLE -> Tone.Neutral
                },
            )
            IconButton(onClick = onSendThumbsUp, enabled = connected) {
                Icon(Icons.Rounded.ThumbUp, contentDescription = "Send thumbs up to the director", tint = CommsColors.Accent)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = "Audio settings", tint = CommsColors.TextSecondary)
            }
        }

        VerticalSpacer(16)

        val banner = FloorPresentation.banner(state)
        FloorBanner(banner.first, banner.second)

        val incomingReactions = state.reactions.filter { it.toId == state.myId || it.toId == null }
        if (incomingReactions.isNotEmpty()) {
            VerticalSpacer(10)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("👍", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "${incomingReactions.last().fromName} sent a thumbs-up",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CommsColors.Accent,
                )
            }
        }

        if (!FloorPresentation.canSpeak(state) && connected) {
            VerticalSpacer(6)
            Text(
                "You can speak when the microphone becomes available.",
                style = MaterialTheme.typography.bodyMedium,
                color = CommsColors.TextSecondary,
            )
        }

        if (connected || state.micLevel > 0f) {
            VerticalSpacer(12)
            LevelMeter("Incoming", state.remoteLevel)
            VerticalSpacer(6)
            LevelMeter("Mic", state.micLevel)
        }

        if (state.link != LinkState.CONNECTED) {
            VerticalSpacer(10)
            Text(
                text = buildString {
                    append("Trying ${state.hostAddress}:${state.port}")
                    state.linkDetail?.let { append(" · $it") }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.linkDetail != null) CommsColors.Warning else CommsColors.TextSecondary,
            )
        }

        Spacer(Modifier.weight(1f))

        BigMicButton(
            live = state.myMicLive,
            enabled = FloorPresentation.canSpeak(state),
            caption = FloorPresentation.micCaption(state),
            modifier = Modifier.fillMaxWidth(),
            onTap = onToggleMic,
        )

        Text(
            "Only the director hears you. Nobody else in the team is on this channel.",
            style = MaterialTheme.typography.bodyMedium,
            color = CommsColors.TextSecondary,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )

        Spacer(Modifier.weight(1f))

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
        }

        VerticalSpacer(12)

        TextButton(onClick = onEnd, modifier = Modifier.fillMaxWidth()) {
            Text("Leave session", color = CommsColors.Danger, style = MaterialTheme.typography.labelLarge)
        }
    }
}
