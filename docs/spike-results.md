# Spike results

Living log of what has actually been proven on hardware, as opposed to what is
implemented. Update this file whenever a claim is verified or disproven.

## 2026-11-04 — first vertical slice, Pixel 8 Pro (Android 17 / API 37)

**Build:** `app-arm64-v8a-debug.apk` (AGP 9.5.0, Gradle 9.8.2, Kotlin 2.4.21,
libwebrtc 150.7871.04, compileSdk/targetSdk 37, minSdk 26).

### Verified

| Check | Outcome |
|---|---|
| App installs and launches on API 37 | ✅ no crash, no ANR |
| Dark home screen, permissions flow | ✅ mic / notifications / nearby-devices requested and granted |
| Director session starts | ✅ foreground service (`type=microphone`) starts, notification posted |
| LAN address detection | ✅ `192.168.0.101` shown, matches `wlan0` |
| WebSocket server bound on 47811 | ✅ `/proc/net/tcp6` LISTEN, `/ws` returns `101 Switching Protocols` |
| Protocol handshake | ✅ `hello` → `welcome` (roster + code) → `roster` |
| SDP offer produced | ✅ 1 176 bytes, `m=audio ... RTP/SAVPF 111 63 9 0 8 13 110 126` (Opus 111) |
| ICE gathering | ✅ host candidate `192.168.0.101:44116` (and `:37865` on a second run) |
| Director mic toggle drives the floor | ✅ member received `{"t":"floor","holder":"director"}` |
| Member appears in the dashboard, selected by default | ✅ |
| Settings screen | ✅ renders; shows *effective* processing report, not the switch state |

The handshake test was run with `tools/loadclient/probe.js` through
`adb forward tcp:47811 tcp:47811`, because of the PC↔phone issue below.

### Not yet verified — needs a second phone

- Actual audio in both directions (DTLS-SRTP media path, Opus, jitter buffer).
- One-way latency measurement (target ≤150 ms p50).
- Whether the noise-suppression toggle audibly changes the captured signal.
- Echo behaviour on speakerphone.
- Reconnect after a Wi-Fi blip.

### Environment finding (important for test setup)

From this dev PC the phone accepted a TCP connection but **no application data
flowed in either direction over the LAN** (HTTP and raw echo both timed out),
while `ping` worked both ways and the phone could reach the PC. The same request
over `adb forward` worked perfectly. Most likely the Surfshark VPN adapter /
Windows packet filter on the PC intercepting LAN traffic — not an app defect.
It must be re-checked on the church Wi-Fi with two phones, where no PC is
involved.

### Observations to act on

- Pixel 8 Pro reports **no platform (hardware) AEC/NS** active, so WebRTC
  software AEC3 + NS are doing the work. `setNoiseSuppressorEnabled()` therefore
  probably returns `false` here — the software APM path (restarting capture with
  `AudioProcessingOptions`) will be needed for a genuine runtime NS toggle.
  Confirm during the two-phone test.
- The settings screen initially showed two identically-labelled "Pixel 8 Pro"
  outputs (speaker and earpiece). Fixed by suffixing the device kind.

### Bugs found and fixed while testing the member path (0.1.1)

Both of these made the member hang forever on "Connecting" with no explanation —
the exact symptom reported from the second phone.

1. **Cleartext WebSocket blocked.** `ws://` is cleartext HTTP and Android blocks
   it by default since API 28. The client died before sending anything, so the
   host saw zero connections. Fixed with `android:usesCleartextTraffic="true"`
   (signalling only; media remains DTLS-SRTP encrypted).
2. **`maxFrameSize` is unsupported by the Ktor OkHttp engine.** Setting it threw
   `Max frame size switch is not supported in OkHttp engine` during connect.
   Removed; the default frame limit is far above our ~1.2 KB SDP frames.

The member screen now shows the address it is dialling and the raw failure
reason in amber, which is how both bugs were identified in one pass.

### Member path verified (stub host)

Using `tools/loadclient/minihost.js` plus `adb reverse` on a single phone:

| Check | Outcome |
|---|---|
| Member connects and sends `hello` | ✅ `code=1111 name=Test` received by the stub |
| Member UI shows Connected + host name | ✅ "Stub Director · Connected" |
| Mic button enabled, floor actions sent | ✅ |

Still outstanding for the real two-phone test: audio in both directions, latency,
NS effect, echo, reconnect.

## Next measurements to record

1. One-way mouth-to-ear latency, p50/p95, wired headset and speaker.
2. NS ON vs OFF spectral difference with music in the room.
3. CPU and battery over a 30-minute session.
4. Two members plus director: routing, floor contention, private reply.
