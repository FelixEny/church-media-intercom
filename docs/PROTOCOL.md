# Signalling protocol

Transport: WebSocket, `ws://<host-lan-ip>:47811/ws`, JSON text frames.
One socket per member. The director does not open a socket to itself.

- `protocol` is currently `1`. Mismatched versions are rejected with a clear error
  so an old APK cannot half-join a session.
- Encoding: UTF-8 JSON, `t` is the message discriminator
  (kotlinx.serialization `classDiscriminator = "t"`).
- Unknown keys are ignored and unknown message types are dropped, so a newer
  client can still talk to an older host.

## Handshake

```
member → host   {"t":"hello","protocol":1,"code":"4821","displayName":"Ada","role":"member"}
host → member   {"t":"welcome","youId":"m-7f3a","sessionCode":"4821","hostName":"Director",
                 "members":[...],"floor":{"holder":null}}
host → member   {"t":"offer","sdp":"v=0\r\n..."}
member → host   {"t":"answer","sdp":"v=0\r\n..."}
```

`hello` is always the first frame. Anything else closes the connection.

Failure responses:

| Case | Response |
|---|---|
| Wrong code | `{"t":"reject","reason":"Wrong session code"}` |
| Protocol mismatch | `{"t":"reject","reason":"Update the app: protocol 2 required"}` |
| Session full | `{"t":"reject","reason":"Session is full (10 members)"}` |
| Director not hosting | TCP connection refused |

## Member → host messages

| `t` | Fields | Meaning |
|---|---|---|
| `hello` | `protocol`, `code`, `displayName`, `role` | join request |
| `answer` | `sdp` | SDP answer for the offer |
| `ice` | `mid`, `mLine`, `candidate` | trickle ICE candidate |
| `floor.request` | — | asks for the talking token |
| `floor.release` | — | gives the token back |
| `mic` | `live` | local mic state change |
| `ping` | `sentAt` | latency probe, echoed back as `pong` |
| `bye` | — | intentional leave |

## Host → member messages

| `t` | Fields | Meaning |
|---|---|---|
| `welcome` | `youId`, `sessionCode`, `hostName`, `members`, `floor` | join accepted |
| `reject` | `reason` | join refused (connection then closes) |
| `roster` | `members`, `floor` | membership/status snapshot, sent on any change |
| `offer` | `sdp` | SDP offer (initial or ICE restart) |
| `ice` | `mid`, `mLine`, `candidate` | trickle ICE candidate |
| `floor` | `floor` | floor changed |
| `pong` | `sentAt`, `serverAt` | latency probe reply |
| `bye` | `reason` | director ended the session |

## Data shapes

```jsonc
// member
{ "id": "m-7f3a", "name": "Ada", "role": "member",
  "micLive": false, "connected": true, "rttMs": 12 }

// floor  — holder is null (free), "director", or a member id
{ "holder": "director" }
```

## Rules the host enforces

1. Only one floor holder at a time.
2. A member may only request the floor; it may never grant itself.
3. A member's audio is only mixed in while it holds the floor.
4. A member id is stable for the lifetime of its connection; reconnect gets a new
   id and a fresh peer connection.

## Latency measurement

`ping`/`pong` measures **signalling round-trip**, not audio path. It is used for
the connection indicator only. Audio latency is measured acoustically
(see [TESTING.md](TESTING.md)) because WebRTC's internal delay is not exposed.
