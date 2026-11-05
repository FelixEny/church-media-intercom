# Architecture

## Topology

A **star**, hosted on the director's phone.

- The director holds one `PeerConnection` per member (max 9 in the MVP target).
- A member holds exactly one `PeerConnection`, back to the director.
- Members never connect to each other. That is what makes private replies private
  and keeps the mesh problem (45 connections for 10 people) away.

The director's phone does three jobs: signalling server, session bookkeeper, and
audio mixer/gate. It is **not** a media relay — audio is peer-to-peer SRTP.

## Runtime components

```
SessionController            app-scoped state machine, survives Activity recreation
 ├─ SettingsStore            DataStore: name, NS toggle, volume, last host
 ├─ SessionService           foreground service (type: microphone) + notification
 ├─ DirectorSession          host role
 │   ├─ SignalingServer      Ktor CIO WebSocket server on :47811
 │   ├─ Roster               membership, mic state, liveness
 │   ├─ FloorController      single-token floor authority
 │   └─ DirectorAudioEngine  N PeerConnections, recipient routing, inbound gating
 ├─ MemberSession            member role
 │   ├─ SignalingClient      Ktor CIO WebSocket client, auto-reconnect
 │   └─ MemberAudioEngine    1 PeerConnection
 ├─ WebRtcCore               shared PeerConnectionFactory + AudioDeviceModule
 └─ AudioRouteManager        MODE_IN_COMMUNICATION, device selection, SCO
```

`SessionController` lives in `CommsApp` (not in the Activity) so a configuration
change or a trip to the background never tears down the call. The foreground
service exists to keep the process alive and visible to the user.

## Session flow (director)

1. Director taps **Start session**. `SessionService` starts in the foreground.
2. `WebRtcCore` initialises `PeerConnectionFactory` with a `JavaAudioDeviceModule`
   (hardware AEC where available, hardware NS off so the app owns the toggle).
3. `SignalingServer` binds port 47811; the UI shows the LAN address and a session
   code.
4. A member connects and sends `hello` with the code.
5. The director replies `welcome`, then builds a `PeerConnection` for that member
   and sends an `offer`.
6. Member answers; trickle ICE flows both ways over the same WebSocket.
7. Audio flows. The director's mic is gated by `Encoding.active` per connection,
   so only selected recipients receive encoded audio.

## Session flow (member)

1. Enter host address + code, tap **Join**.
2. `SignalingClient` connects, sends `hello`.
3. On `welcome` the member stores the roster, then waits for an `offer`.
4. On `offer`: `setRemoteDescription`, attach the local audio track to the
   existing audio transceiver, set it `SEND_RECV`, `createAnswer`, send it.

## Recipient routing

The director captures the microphone **once** (`AudioSource`) and creates one
local `AudioTrack` per member connection. Selecting recipients sets
`RtpParameters.Encoding.active` on each `RtpSender`:

- `active = true` → this member hears the director
- `active = false` → no encoding happens at all (privacy *and* CPU savings)

This is why 10 listeners do not cost 10 encoders when the director is talking to
one person.

## Floor control

One token. Held by exactly one of: the director, a member, or nobody.

| Event | Result |
|---|---|
| Member requests, floor free | grant to that member |
| Member requests, director holds | denied (director is talking) |
| Member requests, another member holds | denied (busy) |
| Director presses mic | grant to director, revoking any member |
| Holder releases / disconnects | floor becomes free |

Enforcement is deliberately doubled: the member stops sending when it does not
hold the floor, *and* the director disables that member's inbound `AudioTrack`.
A buggy or modified client still cannot talk over the director.

## Audio pipeline

```
Mic ─► AudioRecord (VOICE_COMMUNICATION)
        └─ hardware AEC (if the device has it)
             └─ WebRTC APM (AEC3 + NS + AGC)
                  └─ Opus 48 kHz mono, 20 ms frames, ~32 kbps
                       └─ SRTP ──► peer
```

Inbound audio is decoded by libwebrtc's NetEq (jitter buffer + PLC) and played by
`JavaAudioDeviceModule`'s `AudioTrack` in `MODE_IN_COMMUNICATION`.

### Noise suppression toggle

Two suppression systems exist and only one may be active:

1. **Platform/hardware NS** — `JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported()`,
   toggled at runtime with `setNoiseSuppressorEnabled(Boolean)`. Device dependent.
2. **WebRTC software NS** — part of APM, configured per recording session via
   `requestStartRecording(AudioProcessingOptions)`.

The app prefers the hardware path when the device reports support, because it can
be toggled without glitching audio. The settings screen shows the *effective*
state reported by the SDK rather than trusting the switch position.

## Threading

- WebRTC callbacks arrive on libwebrtc's signalling/network threads. Engine code
  never touches UI state directly; it posts immutable snapshots to `StateFlow`.
- Signalling runs on `Dispatchers.IO`.
- Compose collects state with `collectAsStateWithLifecycle()`.

## Android 16+ Local Network Protection (critical)

On API 37 the OS refuses LAN traffic from an app that has not been granted
`ACCESS_LOCAL_NETWORK`. The failure is silent: sockets stay in `SYN-SENT`, no
`ConnectException`, no ICMP — nothing to catch. `adb shell` is exempt, so shell
probes always succeed and can mislead debugging.

Therefore, in this order, before any socket exists:

1. `ACCESS_LOCAL_NETWORK` is granted (requested alongside microphone access).
2. `LanNetworkBinder.bindToWifi()` binds the process to the Wi-Fi `Network`, so
   traffic does not fall back to mobile data when the LAN has no internet.
3. Only then is the signalling server started or the WebSocket client created.

`endSession()` unbinds. Anything added later that touches the LAN (mDNS, UDP
beacon, QR join) must run behind the same gate.

## Failure handling
| Failure | Behaviour |
|---|---|
| Wi-Fi blip, WebSocket drops | client retries with backoff, re-sends `hello`; host rebuilds that member's peer connection |
| ICE fails on an existing link | host calls `restartIce()` and re-offers |
| Member force-quit | host drops the connection, removes the member from the roster |
| Director quits | members receive `bye` / socket close and return to Join |
| AP isolation enabled | no TCP connection is possible; the app reports the specific cause |
