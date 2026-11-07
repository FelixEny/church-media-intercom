package com.churchmedia.intercom.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.churchmedia.intercom.ui.theme.CommsColors
import kotlinx.coroutines.delay

enum class Tone { Neutral, Accent, Live, Warning, Danger }

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: Int = 8) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
fun StatusPill(text: String, tone: Tone = Tone.Neutral, modifier: Modifier = Modifier) {
    val (fg, bg) = when (tone) {
        Tone.Neutral -> CommsColors.TextSecondary to CommsColors.SurfaceHigh
        Tone.Accent -> CommsColors.Accent to CommsColors.AccentDeep
        Tone.Live -> CommsColors.Live to CommsColors.LiveDeep
        Tone.Warning -> CommsColors.Warning to Color(0xFF2E2314)
        Tone.Danger -> CommsColors.Danger to CommsColors.DangerDeep
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatusDot(fg, size = 6)
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CommsColors.Surface)
            .border(1.dp, CommsColors.Outline, RoundedCornerShape(20.dp))
            .padding(18.dp),
    ) {
        if (title != null) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = CommsColors.TextSecondary,
            )
            Spacer(Modifier.height(12.dp))
        }
        content()
    }
}

/**
 * The one control that must never be ambiguous. Size, colour, and motion all change
 * with state; the label under it states the same thing in words.
 */
@Composable
fun BigMicButton(
    live: Boolean,
    enabled: Boolean,
    caption: String,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "mic-pulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    val ringColor = when {
        live -> CommsColors.Live.copy(alpha = 0.35f)
        enabled -> CommsColors.Accent.copy(alpha = 0.18f)
        else -> Color.Transparent
    }
    val fillColor = when {
        live -> CommsColors.Live
        enabled -> CommsColors.Accent
        else -> CommsColors.SurfaceHigh
    }
    val iconTint = when {
        live -> CommsColors.Base
        enabled -> CommsColors.AccentInk
        else -> CommsColors.TextSecondary
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(if (live) (168 * pulse).dp else 168.dp)
                    .clip(CircleShape)
                    .background(ringColor),
            )
            Surface(
                shape = CircleShape,
                color = fillColor,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .alpha(if (enabled || live) 1f else 0.6f),
                onClick = { if (enabled || live) onTap() },
                enabled = enabled || live,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (live) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                        contentDescription = if (live) "Stop transmitting" else "Start transmitting",
                        tint = iconTint,
                        modifier = Modifier.size(56.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = caption,
            style = MaterialTheme.typography.titleMedium,
            color = if (live) CommsColors.Live else CommsColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Thumbs-up button that acknowledges the tap locally, so the sender knows the
 * reaction left the phone. There is no delivery ACK in the protocol; this confirms
 * "sent", not "seen".
 */
@Composable
fun ThumbsUpButton(
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = CommsColors.TextSecondary,
    onSend: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var sendCount by remember { mutableIntStateOf(0) }
    val sent = sendCount > 0

    LaunchedEffect(sendCount) {
        if (sendCount > 0) {
            delay(1_500)
            sendCount = 0
        }
    }

    val background by animateColorAsState(
        targetValue = if (sent) CommsColors.AccentDeep else Color.Transparent,
        label = "thumbs-up-background",
    )
    val iconTint by animateColorAsState(
        targetValue = if (sent) CommsColors.Accent else tint,
        label = "thumbs-up-tint",
    )

    IconButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            // Bump the counter (not a boolean) so a second tap restarts the window.
            sendCount++
            onSend()
        },
        enabled = enabled,
        modifier = modifier
            .clip(CircleShape)
            .background(background),
    ) {
        Icon(
            imageVector = Icons.Rounded.ThumbUp,
            contentDescription = if (sent) "Thumbs-up sent" else contentDescription,
            tint = iconTint,
        )
    }
}

@Composable
fun Divider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(CommsColors.Outline),
    )
}

@Composable
fun RowSpacer(width: Int = 8) = Spacer(Modifier.width(width.dp))

/**
 * Live level meter for captured or decoded audio.
 *
 * It exists because "I can't hear anything" is otherwise untestable: the two meters
 * separate "nothing is being sent", "my mic is not capturing" and "it is arriving but
 * routed somewhere inaudible".
 */
@Composable
fun LevelMeter(label: String, level: Float, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = CommsColors.TextSecondary,
            modifier = Modifier.width(72.dp),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(CommsColors.SurfaceHigh),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(level.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(CommsColors.Accent),
            )
        }
    }
}

@Composable
fun VerticalSpacer(height: Int = 8) = Spacer(Modifier.height(height.dp))

/** Used by both dashboards so the "who is talking" line looks identical. */
@Composable
fun FloorBanner(text: String, tone: Tone) {    val (fg, bg) = when (tone) {
        Tone.Live -> CommsColors.Live to CommsColors.LiveDeep
        Tone.Accent -> CommsColors.Accent to CommsColors.AccentDeep
        Tone.Warning -> CommsColors.Warning to Color(0xFF2E2314)
        Tone.Danger -> CommsColors.Danger to CommsColors.DangerDeep
        Tone.Neutral -> CommsColors.TextSecondary to CommsColors.SurfaceHigh
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatusDot(fg, size = 8)
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = fg,
        )
    }
}
