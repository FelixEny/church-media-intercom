# Church Media Intercom

Low-latency, **internet-free** voice intercom for a church media team. The director's
phone hosts the session; team members join over the existing Wi-Fi and talk over
direct WebRTC audio. No cloud, no accounts, no subscription.

> **Status: early development (0.1.0).**
> The project currently builds and installs, but the audio pipeline is still being
> wired up. See [docs/ROADMAP.md](docs/ROADMAP.md) for what works today.

## Why this exists

During a service the media team needs short, private, reliable instructions
("wider shot", "mic 3 is dead", "hold the lower third"). Radios are one-way,
phone calls are clumsy, and cloud talk apps depend on the church's internet link.
This app keeps everything on the local network.

## Design in one paragraph

The director's phone runs a small WebSocket signalling server on the LAN and holds
one WebRTC peer connection per team member — a star, not a mesh. Audio is Opus over
DTLS-SRTP, so members can only ever hear the director, and a private reply is just
the member's own star link back. A single-token "floor" prevents two people talking
at once. No STUN or TURN servers are configured: on a LAN, host ICE candidates are
enough, and nothing needs to reach the internet.

```
              Director phone (host + mixer)
        ┌──────────────────────────────────────────┐
        │  signalling server (:47811, WebSocket)   │
        │  one PeerConnection per member           │
        └──────┬───────────────┬───────────────────┘
          SRTP │           SRTP│
        ┌──────▼─────┐   ┌─────▼──────┐
        │  Member A  │   │  Member B  │
        └────────────┘   └────────────┘
```

## Requirements

- Android 8.0 (API 26) or newer, `arm64-v8a` or `armeabi-v7a`
- All devices on the same Wi-Fi network, with **AP/client isolation disabled**
- Wired (USB-C) earbuds recommended for the director; Bluetooth works but adds latency

## Build

```powershell
./gradlew :app:assembleDebug
# APKs land in app/build/outputs/apk/debug/
```

Requires JDK 17 and an Android SDK with `platforms;android-37.0` and
`build-tools;37.0.0` installed. `local.properties` points Gradle at the SDK.

```powershell
./gradlew :app:installDebug      # install on the connected device
./gradlew :app:testDebugUnitTest # JVM tests
```

## Documentation

| Document | Contents |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Components, threading, and the audio pipeline |
| [docs/PROTOCOL.md](docs/PROTOCOL.md) | Signalling message reference |
| [docs/TESTING.md](docs/TESTING.md) | Device matrix, latency method, known gaps |
| [docs/ROADMAP.md](docs/ROADMAP.md) | Phases and current status |

## Licence

Source is MIT licensed ([LICENSE](LICENSE)). The bundled WebRTC build is BSD-3;
see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
