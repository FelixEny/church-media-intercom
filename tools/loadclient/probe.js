#!/usr/bin/env node
/*
 * Dev-only probe for the Comms signalling server.
 *
 *   node probe.js --host 192.168.1.42 --code 4821 [--name Ada] [--seconds 8]
 *
 * It performs the member handshake and prints every message the host sends, which
 * is enough to verify the server, the protocol, and the SDP offer without a second
 * phone. It does not send audio.
 */

const WebSocket = require('ws')

function arg(name, fallback) {
  const index = process.argv.indexOf(`--${name}`)
  if (index === -1) return fallback
  return process.argv[index + 1] ?? fallback
}

const host = arg('host', '')
const code = arg('code', '')
const name = arg('name', 'Probe')
const seconds = Number(arg('seconds', '8'))
const port = Number(arg('port', '47811'))

if (!host || !code) {
  console.error('usage: node probe.js --host <lan-ip> --code <4 digits> [--name X] [--seconds 8]')
  process.exit(2)
}

const url = `ws://${host}:${port}/ws`
console.log(`→ ${url} as "${name}"`)

const ws = new WebSocket(url)
const seen = new Set()

function send(message) {
  ws.send(JSON.stringify(message))
}

ws.on('open', () => {
  send({ t: 'hello', protocol: 1, code, displayName: name, role: 'member' })
})

ws.on('message', (raw) => {
  const text = raw.toString()
  let msg
  try {
    msg = JSON.parse(text)
  } catch {
    console.log('← (unparseable)', text.slice(0, 120))
    return
  }
  seen.add(msg.t)

  switch (msg.t) {
    case 'welcome':
      console.log(`← welcome  youId=${msg.youId} host="${msg.hostName}" code=${msg.sessionCode}`)
      console.log(`  roster: ${msg.members.map((m) => `${m.name}(${m.role})`).join(', ')}`)
      break
    case 'offer':
      console.log(`← offer    ${msg.sdp.length} bytes`)
      console.log(`  ${firstLine(msg.sdp)}`)
      // Acknowledge so the host stops waiting, using the same SDP (no audio).
      break
    case 'roster':
      console.log(`← roster   ${msg.members.length} member(s), floor=${JSON.stringify(msg.floor)}`)
      break
    case 'floor':
      console.log(`← floor    ${JSON.stringify(msg.floor)}`)
      break
    case 'error':
      console.log(`← error    ${msg.message}`)
      break
    case 'reject':
      console.log(`← reject   ${msg.reason}`)
      break
    case 'bye':
      console.log(`← bye      ${msg.reason}`)
      break
    default:
      console.log(`← ${msg.t}  ${text.length > 160 ? text.slice(0, 160) + '…' : text}`)
  }
})

ws.on('close', () => {
  console.log(`closed. message types seen: ${[...seen].join(', ') || 'none'}`)
  process.exit(seen.has('welcome') && seen.has('offer') ? 0 : 1)
})

ws.on('error', (error) => {
  console.error('error:', error.message)
  process.exit(1)
})

function firstLine(sdp) {
  return (sdp.split('\r\n').find((line) => line.startsWith('m=')) || '').trim()
}

setTimeout(() => {
  console.log('timeout — closing')
  ws.close()
}, seconds * 1000)
