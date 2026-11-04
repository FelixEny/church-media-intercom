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

## Next measurements to record

1. One-way mouth-to-ear latency, p50/p95, wired headset and speaker.
2. NS ON vs OFF spectral difference with music in the room.
3. CPU and battery over a 30-minute session.
4. Two members plus director: routing, floor contention, private reply.
