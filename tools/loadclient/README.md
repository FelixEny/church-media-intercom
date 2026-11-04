# Load client / probe (dev only)

Not shipped in the APK. Used to exercise the host's signalling without burning a
second phone.

## probe.js

Connects once, performs the member handshake, and prints every message the host
sends. Verifies the server, roster, SDP offer, and ICE candidates.

```powershell
npm install
adb forward tcp:47811 tcp:47811    # if the PC cannot reach the phone directly
node probe.js --host 127.0.0.1 --code 4821 --name Ada
```

Exit code is `0` when both `welcome` and `offer` were received.

## Notes

- These clients never send audio, so a host that shows them as connected still
  has an unverified media path.
- `--host` is the phone's LAN IP when the network allows phone↔PC traffic, or
  `127.0.0.1` with `adb forward`.
