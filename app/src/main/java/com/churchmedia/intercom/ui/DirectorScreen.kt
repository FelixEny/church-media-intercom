package com.churchmedia.intercom.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.churchmedia.intercom.core.model.FloorState
import com.churchmedia.intercom.core.model.Member
import com.churchmedia.intercom.session.AppState
import com.churchmedia.intercom.ui.components.BigMicButton
import com.churchmedia.intercom.ui.components.FloorBanner
import com.churchmedia.intercom.ui.components.LevelMeter
import com.churchmedia.intercom.ui.components.StatusPill
import com.churchmedia.intercom.ui.components.Tone
import com.churchmedia.intercom.ui.components.VerticalSpacer
import com.churchmedia.intercom.ui.theme.CommsColors

/**
 * Two-part layout: session info and the member list scroll, the mic button stays put.
 */
@Composable
fun DirectorScreen(
    state: AppState,
    onToggleMic: () -> Unit,
    onToggleRecipient: (String) -> Unit,
    onEveryone: () -> Unit,
    onSendThumbsUp: (String) -> Unit,
    onEnd: () -> Unit,
    onSettings: () -> Unit,
) {
    val listeners = state.listeners
    val selectedCount = if (state.everyone) listeners.size else state.recipients.count { id ->
        listeners.any { it.id == id }
    }

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
                    Text("SESSION CODE", style = MaterialTheme.typography.labelMedium, color = CommsColors.TextSecondary)
                    Text(state.sessionCode, style = MaterialTheme.typography.headlineMedium)
                }
                Spacer(Modifier.size(8.dp))
                IconButton(onClick = onSettings) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Audio settings", tint = CommsColors.TextSecondary)
                }
                TextButton(onClick = onEnd) {
                    Text("End", color = CommsColors.Danger, style = MaterialTheme.typography.labelLarge)
                }
            }

            Text(
                "${state.localAddress}:${state.port} · ${listeners.size} member${if (listeners.size == 1) "" else "s"}",
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

            VerticalSpacer(16)

            val banner = when {
                state.directorMicLive -> "You are live" to Tone.Live
                state.floor is FloorState.Member -> "${state.holderName} is replying privately" to Tone.Accent
                else -> "Nobody is talking" to Tone.Neutral
            }
            FloorBanner(banner.first, banner.second)

            VerticalSpacer(10)
            LevelMeter("Mic", state.micLevel)

            VerticalSpacer(18)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Speaking to", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                StatusPill(
                    if (state.everyone) "Everyone" else "$selectedCount selected",
                    if (state.everyone) Tone.Accent else Tone.Neutral,
                )
            }

            VerticalSpacer(10)

            FilterChip(
                selected = state.everyone,
                onClick = onEveryone,
                label = { Text("Everyone") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CommsColors.AccentDeep,
                    selectedLabelColor = CommsColors.Accent,
                ),
            )

            VerticalSpacer(8)

            if (listeners.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("No one has joined yet", style = MaterialTheme.typography.titleMedium)
                    VerticalSpacer(6)
                    Text(
                        "Team members: open Comms → Join a session → ${state.localAddress} · ${state.sessionCode}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = CommsColors.TextSecondary,
                    )
                }
            } else {
                listeners.forEach { member ->
                    MemberSelectRow(
                        member = member,
                        selected = state.everyone || state.recipients.contains(member.id),
                        hasReaction = state.reactions.any { r ->
                            r.fromId == member.id && r.toId == "director"
                        },
                        onToggle = { onToggleRecipient(member.id) },
                        onSendThumbsUp = { onSendThumbsUp(member.id) },
                    )
                }
            }
        }

        BigMicButton(
            live = state.directorMicLive,
            enabled = true,
            caption = when {
                state.directorMicLive -> "Tap to stop"
                state.everyone -> "Tap to talk to everyone"
                selectedCount == 1 -> "Tap to talk to 1 member"
                else -> "Tap to talk to $selectedCount members"
            },
            modifier = Modifier.fillMaxWidth(),
            onTap = onToggleMic,
        )
    }
}

@Composable
private fun MemberSelectRow(
    member: Member,
    selected: Boolean,
    hasReaction: Boolean,
    onToggle: () -> Unit,
    onSendThumbsUp: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle)
            .background(if (selected) CommsColors.Surface else CommsColors.Surface.copy(alpha = 0.4f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = selected, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    member.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (hasReaction) {
                    Text("👍", style = MaterialTheme.typography.titleMedium)
                }
            }
            Text(
                when {
                    member.micLive -> "Replying to you"
                    member.connected -> "Connected"
                    else -> "Disconnected"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (member.micLive) CommsColors.Accent else CommsColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (member.micLive) StatusPill("Live", Tone.Accent)
        IconButton(onClick = onSendThumbsUp) {
            Icon(Icons.Rounded.ThumbUp, contentDescription = "Send thumbs up to ${member.name}", tint = CommsColors.TextSecondary)
        }
    }
}
