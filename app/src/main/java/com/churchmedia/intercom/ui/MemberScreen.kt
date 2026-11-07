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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.churchmedia.intercom.core.model.FloorState
import com.churchmedia.intercom.core.model.LinkState
import com.churchmedia.intercom.session.AppState
import com.churchmedia.intercom.ui.components.BigMicButton
import com.churchmedia.intercom.ui.components.FloorBanner
import com.churchmedia.intercom.ui.components.LevelMeter
import com.churchmedia.intercom.ui.components.SectionCard
import com.churchmedia.intercom.ui.components.StatusPill
import com.churchmedia.intercom.ui.components.ThumbsUpButton
import com.churchmedia.intercom.ui.components.Tone
import com.churchmedia.intercom.ui.components.VerticalSpacer
import com.churchmedia.intercom.ui.theme.CommsColors

/**
 * Two-part layout so the primary control never scrolls away: everything informational
 * scrolls in the top section, the mic button and volume stay pinned at the bottom.
 */
@Composable
fun MemberScreen(
    state: AppState,
    onToggleMic: () -> Unit,
    onSendThumbsUp: () -> Unit,
    onVolume: (Float) -> Unit,
    onRetry: () -> Unit,
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
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        hostName,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "Session ${state.sessionCode}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CommsColors.TextSecondary,
                    )
                    Text(
                        "Output: ${state.outputLabel ?: "Speaker"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CommsColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "Mic: ${state.inputLabel ?: "Phone microphone"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CommsColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
                ThumbsUpButton(
                    contentDescription = "Send thumbs up to the director",
                    enabled = connected,
                    tint = CommsColors.Accent,
                    onSend = onSendThumbsUp,
                )
                IconButton(onClick = onSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Audio settings", tint = CommsColors.TextSecondary)
                }
            }

            VerticalSpacer(16)

            val banner = FloorPresentation.banner(state)
            FloorBanner(banner.first, banner.second)

            if (!FloorPresentation.canSpeak(state) && connected) {
                VerticalSpacer(6)
                Text(
                    "You can speak when the microphone becomes available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CommsColors.TextSecondary,
                )
            }

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
        }

        if (state.link == LinkState.FAILED) {
            SectionCard(title = "Connection lost") {
                Text(
                    "The director is offline. The session may have ended.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CommsColors.TextSecondary,
                )
                VerticalSpacer(12)
                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Try again", style = MaterialTheme.typography.labelLarge) }
            }
        } else {
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
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp),
            )
        }

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

        Button(
            onClick = onEnd,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("Leave session", color = CommsColors.Danger, style = MaterialTheme.typography.labelLarge)
        }
    }
}
