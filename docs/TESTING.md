# Testing

## Hardware available today

| Device | Android | Role |
|---|---|---|
| Pixel 8 Pro | 17 (API 37) | director or member |
| Second Android phone (TBD) | TBD | the other side |

Two devices verify the critical path (director ↔ member, floor, private reply,
noise suppression, routing, latency). They **cannot** verify 6–10 concurrent
members; that is simulated with the load client until more phones are available.

## Automated

```powershell
./gradlew :app:testDebugUnitTest        # floor state machine, routing diff, protocol codec
./gradlew :app:connectedDebugAndroidTest # ADM/NS toggle, service lifecycle
```

## Latency measurement (two phones)

Mouth-to-ear, not a ping.

1. Place phone A (director) and phone B (member) 1 m apart in the room, both on
   speaker at normal service volume.
2. On phone A, play a click track (or tap the screen in time with a metronome).
3. Record on phone B with any recorder, then on the host PC:
   `ffmpeg -i b.wav -af "silencedetect=n=-35dB:d=0.05" -f null -` to get click
   timestamps, and compare against a loopback reference of A's own output.
4. Repeat 20 times; report p50 and p95. A single best-case number is not a result.

Target: ≤150 ms p50 one-way on 5 GHz. Bluetooth adds 150–300 ms and will miss it —
that is expected and documented rather than hidden.

## Noise suppression verification

The point of the test is that the toggle *does something*, not that it exists.

1. Play music at conversational level in the room; run the mic test screen.
2. Toggle NS off → record 10 s. Toggle on → record 10 s.
3. Compute spectral flatness / high-band energy on both clips and confirm a
   difference; also A/B by ear with a second listener.
4. Repeat with HVAC hum and with side conversation.
5. Record the SDK's reported effective implementation from
   `getPlatformAudioProcessingState()` so we know whether hardware or software NS
   was actually active.

## Network abuse

| Test | Expected |
|---|---|
| Router AP isolation on | join fails with an explicit "this network blocks device-to-device traffic" message |
| Airplane mode for 5 s on a member | reconnect + audio resumes within ~10 s |
| Wi-Fi off for 60 s on a member | member returns to Join with a clear error |
| Director screen off for 30 min | session stays up, audio still flows |
| Member force-stop | removed from roster; director keeps talking to everyone else |
| Router reboot | all members reconnect after the AP returns |

## Soak

30 minutes continuous, 3 talkers rotating. Log every 10 s: CPU %, RSS, battery %,
thermal status, packet loss, and the count of unintended reconnects. Pass criteria:
no crash, no audio dead-air longer than 1 s, battery < 10 %/hr.

## Known gaps

- 6–10 simultaneous physical members: **not tested yet**.
- Cross-AP roaming with multiple members: **not tested yet**.
- Acoustic echo on very loud PA / speaker feedback: needs a real service test.
