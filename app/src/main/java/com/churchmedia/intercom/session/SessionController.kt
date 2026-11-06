package com.churchmedia.intercom.session

import android.content.Context
import com.churchmedia.intercom.core.audio.AudioRouteManager
import com.churchmedia.intercom.core.audio.WebRtcCore
import com.churchmedia.intercom.core.model.DEFAULT_PORT
import com.churchmedia.intercom.core.model.FloorState
import com.churchmedia.intercom.core.model.LinkState
import com.churchmedia.intercom.core.model.Member
import com.churchmedia.intercom.core.model.Reaction
import com.churchmedia.intercom.core.model.ReactionList
import com.churchmedia.intercom.core.model.Mode
import com.churchmedia.intercom.core.net.LanNetwork
import com.churchmedia.intercom.core.net.LanNetworkBinder
import com.churchmedia.intercom.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * App-scoped session state machine. Lives in [com.churchmedia.intercom.CommsApp] so a
 * rotation or a trip to the background cannot tear the call down.
 */
class SessionController(
    private val context: Context,
    private val settingsStore: SettingsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val core = WebRtcCore(context)
    private val audioRoute = AudioRouteManager(context)
    private val lanBinder = LanNetworkBinder(context)

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    @Volatile
    private var director: DirectorSession? = null

    @Volatile
    private var member: MemberSession? = null

    init {
        audioRoute.onActiveDeviceChanged = { id, label ->
            _state.update {
                it.copy(
                    outputId = id ?: it.outputId,
                    outputLabel = label ?: it.outputLabel,
                    inputLabel = audioRoute.currentInputLabel(),
                    outputs = audioRoute.outputs(),
                )
            }
        }
        core.onMicLevel = { level ->
            _state.update { it.copy(micLevel = level) }
        }
        scope.launch {
            val settings = settingsStore.current()
            _state.update {
                it.copy(
                    displayName = settings.displayName,
                    lastHost = settings.lastHost,
                    lastCode = settings.lastCode,
                    noiseSuppression = settings.noiseSuppression,
                    volume = settings.volume,
                )
            }
        }
    }

    // ------------------------------------------------------------------- host

    fun startDirector(name: String, port: Int = DEFAULT_PORT) {
        if (_state.value.mode != Mode.IDLE) return
        val cleanName = name.trim().ifBlank { "Director" }

        // Must happen before any socket is created: see LanNetworkBinder.
        val wifi = lanBinder.bindToWifi()
        if (wifi == null) {
            _state.update {
                it.copy(error = "Connect to Wi-Fi first. This app never uses mobile data.")
            }
            return
        }

        _state.update {
            it.copy(
                mode = Mode.DIRECTOR,
                connecting = true,
                displayName = cleanName,
                error = null,
                notice = null,
                floor = FloorState.Free,
                members = emptyList(),
                port = port,
            )
        }
        SessionService.start(context)

        val session = DirectorSession(
            core = core,
            audioRoute = audioRoute,
            code = LanNetwork.newSessionCode(),
            hostName = cleanName,
            port = port,
            callbacks = object : DirectorSession.Callbacks {
                override fun onRoster(members: List<Member>, floor: FloorState) {
                    _state.update { it.copy(members = members, floor = floor) }
                }

                override fun onNotice(message: String) {
                    _state.update { it.copy(notice = message) }
                }

                override fun onReaction(reaction: Reaction) {
                    _state.update { it.copy(reactions = ReactionList.add(it.reactions, reaction)) }
                }

                override fun onReactionExpired(id: String) {
                    _state.update { it.copy(reactions = it.reactions.filter { r -> r.id != id }) }
                }

                override fun onEnded(reason: String) {
                    endSession(reason)
                }
            },
        )
        director = session

        runCatching { session.start() }
            .onSuccess {
                _state.update {
                    it.copy(
                        connecting = false,
                        link = LinkState.CONNECTED,
                        sessionCode = session.code,
                        hostName = cleanName,
                        localAddress = session.localAddress,
                        outputs = audioRoute.outputs(),
                        outputId = audioRoute.currentOutputId(),
                        processingSummary = core.describeProcessing(),
                    )
                }
                refreshProcessing()
            }
            .onFailure { error ->
                _state.update {
                    it.copy(error = "Could not start hosting on port $port: ${error.message}")
                }
                endSession()
            }

        scope.launch { settingsStore.saveDisplayName(cleanName) }
    }

    // ----------------------------------------------------------------- member

    fun join(hostInput: String, codeInput: String, nameInput: String) {
        if (_state.value.mode != Mode.IDLE) return

        val host = LanNetwork.normaliseHost(hostInput)
        val code = codeInput.trim()
        val name = nameInput.trim().ifBlank { "Member" }

        if (host.isBlank() || !host.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))) {
            _state.update { it.copy(error = "Enter the director's address, for example 192.168.1.42") }
            return
        }
        if (code.length != 4 || code.any { !it.isDigit() }) {
            _state.update { it.copy(error = "Enter the 4-digit session code") }
            return
        }

        // Bind before the WebSocket is created: see LanNetworkBinder.
        val wifi = lanBinder.bindToWifi()
        if (wifi == null) {
            _state.update {
                it.copy(error = "Connect to Wi-Fi first. This app never uses mobile data.")
            }
            return
        }

        _state.update {
            it.copy(
                mode = Mode.MEMBER,
                connecting = true,
                link = LinkState.CONNECTING,
                hostAddress = host,
                sessionCode = code,
                displayName = name,
                error = null,
                notice = null,
                floor = FloorState.Free,
                members = emptyList(),
            )
        }
        SessionService.start(context)

        val session = MemberSession(
            scope = scope,
            core = core,
            audioRoute = audioRoute,
            url = "ws://$host:$DEFAULT_PORT/ws",
            code = code,
            displayName = name,
            callbacks = object : MemberSession.Callbacks {
                override fun onLink(state: LinkState, detail: String?) {
                    _state.update {
                        it.copy(
                            link = state,
                            connecting = state == LinkState.CONNECTING,
                            linkDetail = if (state == LinkState.CONNECTED) null else detail,
                        )
                    }
                }

                override fun onRoster(members: List<Member>, floor: FloorState) {
                    _state.update { it.copy(members = members, floor = floor) }
                }

                override fun onWelcome(hostName: String, myId: String) {
                    _state.update {
                        it.copy(
                            hostName = hostName,
                            myId = myId,
                            link = LinkState.CONNECTED,
                            connecting = false,
                            linkDetail = null,
                        )
                    }
                    refreshProcessing()
                }

                override fun onRemoteLevel(level: Float) {
                    _state.update { it.copy(remoteLevel = level) }
                }

                override fun onReaction(reaction: Reaction) {
                    _state.update { it.copy(reactions = ReactionList.add(it.reactions, reaction)) }
                }

                override fun onReactionExpired(id: String) {
                    _state.update { it.copy(reactions = it.reactions.filter { r -> r.id != id }) }
                }

                override fun onNotice(message: String) {
                    _state.update { it.copy(notice = message) }
                }

                override fun onEnded(reason: String) {
                    endSession(reason)
                }
            },
        )
        member = session
        session.start()

        scope.launch {
            settingsStore.saveDisplayName(name)
            settingsStore.saveLastSession(host, code)
        }
    }

    // --------------------------------------------------------------- controls

    fun endSession(reason: String? = null) {
        val directorSession = director
        val memberSession = member
        director = null
        member = null

        runCatching { memberSession?.stop() }
        // The director's stop waits on the signalling server's grace period, so it runs
        // off the main thread to keep the UI responsive.
        if (directorSession != null) {
            scope.launch { runCatching { directorSession.stop(reason ?: "Session ended") } }
        }
        runCatching { audioRoute.exitCommunicationMode() }
        runCatching { lanBinder.unbind() }
        SessionService.stop(context)

        _state.update { current ->
            AppState(
                displayName = current.displayName,
                lastHost = current.lastHost,
                lastCode = current.lastCode,
                noiseSuppression = current.noiseSuppression,
                volume = current.volume,
                outputs = current.outputs,
                outputId = current.outputId,
                error = reason,
            )
        }
    }

    fun toggleDirectorMic() {
        val session = director ?: return
        session.setDirectorMicLive(!_state.value.directorMicLive)
    }

    fun toggleMemberMic() {
        member?.toggleMic()
    }

    /** Director sends a thumbs-up to a single participant. */
    fun sendThumbsUp(memberId: String) {
        director?.sendThumbsUp(memberId)
    }

    /** Participant sends a thumbs-up to the director. */
    fun sendThumbsUp() {
        member?.sendThumbsUp()
    }

    /** Participant retries after the director was unreachable; keeps them in the session. */
    fun retryMemberConnection() {
        member?.retry()
    }

    fun setRecipients(ids: Set<String>) {
        _state.update { it.copy(recipients = ids, everyone = false) }
        director?.setRecipients(ids, everyone = false)
    }

    fun setEveryone() {
        _state.update { it.copy(recipients = emptySet(), everyone = true) }
        director?.setRecipients(emptySet(), everyone = true)
    }

    fun toggleRecipient(memberId: String) {
        val current = _state.value
        val next = if (current.everyone) {
            // Leaving "everyone" means: keep only this member selected.
            setOf(memberId)
        } else {
            if (current.recipients.contains(memberId)) current.recipients - memberId else current.recipients + memberId
        }
        if (next.isEmpty()) {
            // Never allow a silent "speaking to nobody" state.
            setEveryone()
        } else {
            setRecipients(next)
        }
    }

    fun setNoiseSuppression(enabled: Boolean) {
        _state.update { it.copy(noiseSuppression = enabled) }
        val applied = runCatching { core.setNoiseSuppression(enabled) }.getOrDefault(false)
        refreshProcessing()
        if (enabled && !applied) {
            _state.update {
                it.copy(
                    notice = "This phone has no hardware noise suppressor; WebRTC software suppression stays on",
                )
            }
        }
        scope.launch { settingsStore.saveNoiseSuppression(enabled) }
    }

    fun setVolume(value: Float) {
        val clamped = value.coerceIn(0.2f, 2f)
        _state.update { it.copy(volume = clamped) }
        director?.setVolume(clamped)
        member?.setVolume(clamped)
        scope.launch { settingsStore.saveVolume(clamped) }
    }

    fun refreshOutputs() {
        val outputs = audioRoute.outputs()
        _state.update {
            it.copy(
                outputs = outputs,
                outputId = audioRoute.currentOutputId() ?: it.outputId,
                inputLabel = audioRoute.currentInputLabel(),
            )
        }
    }

    fun selectOutput(deviceId: Int) {
        val ok = audioRoute.select(deviceId)
        _state.update {
            if (ok) it.copy(outputId = deviceId)
            else it.copy(notice = "This phone refused to switch to that output")
        }
    }

    fun refreshProcessing() {
        val summary = runCatching { core.describeProcessing() }.getOrDefault("")
        if (summary.isNotBlank()) {
            _state.update { it.copy(processingSummary = summary) }
        }
    }

    fun saveDisplayName(name: String) {
        _state.update { it.copy(displayName = name.trim()) }
        scope.launch { settingsStore.saveDisplayName(name.trim()) }
    }

    fun reportError(message: String) {
        _state.update { it.copy(error = message) }
    }

    fun clearMessages() {
        _state.update { it.copy(error = null, notice = null) }
    }

    fun clearNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
