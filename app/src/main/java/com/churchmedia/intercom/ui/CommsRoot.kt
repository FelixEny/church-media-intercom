package com.churchmedia.intercom.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.churchmedia.intercom.core.model.DEFAULT_PORT
import com.churchmedia.intercom.core.model.Mode
import com.churchmedia.intercom.core.net.LanNetwork
import com.churchmedia.intercom.session.SessionController
import com.churchmedia.intercom.ui.theme.CommsColors

@Composable
fun CommsRoot(controller: SessionController) {
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current

    var showJoin by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val action = pendingAction
        pendingAction = null
        if (result.values.all { it }) {
            action?.invoke()
        } else {
            controller.reportError("Microphone access is required to join a session")
        }
    }

    fun withPermissions(action: () -> Unit) {
        val missing = requiredPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            action()
        } else {
            pendingAction = action
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    val localAddress = remember(state.mode) { LanNetwork.ipv4().orEmpty() }

    LaunchedEffect(state.mode) {
        view.keepScreenOn = state.mode != Mode.IDLE
    }
    LaunchedEffect(showSettings) {
        if (showSettings) controller.refreshOutputs()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CommsColors.Base)
            .safeDrawingPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (state.notice != null) {
                NoticeBar(
                    text = state.notice.orEmpty(),
                    onDismiss = controller::clearNotice,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when {
            showSettings -> SettingsScreen(
                state = state,
                onToggleNoiseSuppression = controller::setNoiseSuppression,
                onVolume = controller::setVolume,
                onSelectOutput = controller::selectOutput,
                onRefreshOutputs = controller::refreshOutputs,
                onDone = { showSettings = false },
            )

            state.mode == Mode.DIRECTOR -> DirectorScreen(
                state = state,
                onToggleMic = controller::toggleDirectorMic,
                onToggleRecipient = controller::toggleRecipient,
                onEveryone = controller::setEveryone,
                onEnd = {
                    showSettings = false
                    controller.endSession()
                },
                onSettings = { showSettings = true },
            )

            state.mode == Mode.MEMBER -> MemberScreen(
                state = state,
                onToggleMic = controller::toggleMemberMic,
                onVolume = controller::setVolume,
                onEnd = {
                    showSettings = false
                    controller.endSession()
                    showJoin = false
                },
                onSettings = { showSettings = true },
            )

            showJoin -> JoinScreen(
                initialAddress = state.lastHost.ifBlank {
                    state.localAddress.ifBlank { state.hostAddress }
                },
                initialCode = state.lastCode,
                initialName = state.displayName,
                error = state.error,
                connecting = state.connecting,
                onJoin = { address, code, name ->
                    withPermissions { controller.join(address, code, name) }
                },
                onBack = {
                    controller.clearError()
                    showJoin = false
                },
            )

            else -> HomeScreen(
                initialName = state.displayName,
                localAddress = localAddress,
                onStartHosting = { name -> withPermissions { controller.startDirector(name, DEFAULT_PORT) } },
                onJoin = {
                    controller.clearError()
                    showJoin = true
                },
                onSettings = { showSettings = true },
            )
                }
            }
        }
    }

    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = controller::clearError,
            title = { Text("Heads up") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = controller::clearError) { Text("OK") }
            },
        )
    }
}

@Composable
private fun NoticeBar(text: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CommsColors.SurfaceHigh)
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = CommsColors.TextPrimary,
            modifier = Modifier.weight(1f, fill = false),
        )
        TextButton(onClick = onDismiss) { Text("OK") }
    }
}

private fun requiredPermissions(): List<String> = buildList {
    add(Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_CONNECT)
    }
}
