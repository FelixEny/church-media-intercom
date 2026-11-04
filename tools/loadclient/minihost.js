#!/usr/bin/env node
/*
 * Dev-only stub host. Speaks just enough of the protocol to let a real phone run
 * the member path without a second device.
 *
 *   node minihost.js --port 47811
 *
 * Then, on the phone:  adb reverse tcp:47811 tcp:47811
 * and join 127.0.0.1 with any 4-digit code.
 */

const { WebSocketServer } = require('ws')

const portArg = process.argv.indexOf('--port')
const port = portArg === -1 ? 47811 : Number(process.argv[portArg + 1])

const wss = new WebSocketServer({ port, path: '/ws' })
console.log(`stub host listening on :${port}/ws`)

wss.on('connection', (socket, request) => {
  const peer = request.socket.remoteAddress
  console.log(`+ connection from ${peer}`)

  socket.on('message', (raw) => {
    let msg
    try {
      msg = JSON.parse(raw.toString())
    } catch {
      console.log('  (unparseable frame)')
      return
    }
    console.log('  ←', msg.t, msg.t === 'hello' ? `code=${msg.code} name=${msg.displayName}` : '')

    switch (msg.t) {
      case 'hello':
        socket.send(JSON.stringify({
          t: 'welcome',
          youId: 'm-stub',
          sessionCode: msg.code,
          hostName: 'Stub Director',
          members: [
            { id: 'director', name: 'Stub Director', role: 'director', micLive: false, connected: true },
            { id: 'm-stub', name: msg.displayName || 'Phone', role: 'member', micLive: false, connected: true },
          ],
          floor: { holder: null },
        }))
        break
      case 'ping':
        socket.send(JSON.stringify({ t: 'pong', sentAt: msg.sentAt, serverAt: Date.now() }))
        break
      case 'floor.request':
        socket.send(JSON.stringify({ t: 'floor', floor: { holder: 'm-stub' } }))
        break
      case 'floor.release':
        socket.send(JSON.stringify({ t: 'floor', floor: { holder: null } }))
        break
      case 'bye':
        console.log('  bye')
        break
      default:
        break
    }
  })

  socket.on('close', () => console.log('- connection closed'))
})

wss.on('error', (error) => {
  console.error('server error:', error.message)
  process.exit(1)
})
