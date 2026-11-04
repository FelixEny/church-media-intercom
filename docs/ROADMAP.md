# Roadmap

Status as of this commit.

## Phase 0 — Risk spike

| Item | Status |
|---|---|
| Project skeleton, Gradle, Compose, dark theme | ✅ |
| Public repo, licence, docs | ✅ |
| Signalling protocol | 🚧 |
| WebSocket server/client on LAN | 🚧 |
| 1:1 WebRTC audio between two devices | 🚧 |
| Noise-suppression toggle verified on device | ⬜ |
| Latency measurement recorded in `docs/spike-results.md` | ⬜ |

## Phase 1 — Join flow

- [ ] Session code + manual address join
- [ ] mDNS discovery (`NsdManager`) with UDP broadcast fallback
- [ ] QR code on the director screen; QR scan on join
- [ ] Foreground service lifecycle across background/rotation

## Phase 2 — Star audio

- [ ] Director N-peer engine with per-recipient `Encoding.active`
- [ ] Floor control end to end
- [ ] Member dashboard, director dashboard, roster status
- [ ] Reconnect + ICE restart

## Phase 3 — Audio settings

- [ ] Noise suppression toggle + effective-state display
- [ ] Incoming volume slider, optional +12 dB limiter boost
- [ ] Output device selection (speaker / earpiece / USB-C / BT-SCO)
- [ ] Microphone test meter

## Phase 4 — Field readiness

- [ ] Load client (`tools/loadclient`, dev only) for 6–10 simulated members
- [ ] 30-minute soak, battery/thermal pass
- [ ] 5–10 physical device test at a live service
- [ ] Signed release APK + install guide

## Deferred / out of scope for MVP

iOS, video, text chat, accounts, cloud relay, multi-church, recordings, analytics.
