# Roadmap

Status as of the first vertical slice. See
[spike-results.md](spike-results.md) for what has been proven on hardware.

## Phase 0 — Risk spike

| Item | Status |
|---|---|
| Project skeleton, Gradle, Compose, dark theme | ✅ |
| Public repo, licence, docs | ✅ |
| Signalling protocol + JSON codec | ✅ |
| WebSocket server/client on LAN | ✅ |
| Director hosts, member joins, roster syncs | ✅ |
| 1:1 WebRTC audio (media path) | 🚧 signalling and SDP verified; audio needs a second phone |
| Noise-suppression toggle verified on device | 🚧 UI + runtime API wired; effect not yet measured |
| Latency measurement recorded | ⬜ |

## Phase 1 — Join flow

- [x] Session code + manual address join
- [x] Foreground service lifecycle
- [ ] mDNS discovery (`NsdManager`) with UDP broadcast fallback
- [ ] QR code on the director screen; QR scan on join
- [ ] "Copy session info" / share sheet

## Phase 2 — Star audio

- [x] Director N-peer engine with per-recipient `Encoding.active`
- [x] Floor control end to end (host authoritative, member mirrors)
- [x] Director and member dashboards, roster status
- [ ] Reconnect + ICE restart verified on hardware
- [ ] Per-member connection quality indicator

## Phase 3 — Audio settings

- [x] Noise suppression toggle + effective-state display
- [x] Incoming volume (per-remote-track gain, 20–200 %)
- [x] Output device selection (speaker / earpiece / USB-C / BT-SCO)
- [ ] Software APM noise-suppression toggle (capture restart) if hardware NS is absent
- [ ] Microphone test meter
- [ ] Limiter for >100 % boost

## Phase 4 — Field readiness

- [ ] Load client for 6–10 simulated members
- [ ] 30-minute soak, battery/thermal pass
- [ ] 5–10 physical device test at a live service
- [ ] Signed release APK + install guide

## Deferred / out of scope for MVP

iOS, video, text chat, accounts, cloud relay, multi-church, recordings, analytics.
