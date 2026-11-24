// Escucha el canal en vivo de un envio (Node 22 trae WebSocket global).
// Uso: node scripts/ws-watch.mjs <shipmentId> <token>
const [shipmentId, token] = process.argv.slice(2)
if (!shipmentId || !token) {
  console.error('uso: node scripts/ws-watch.mjs <shipmentId> <token>')
  process.exit(2)
}

const url = `${process.env.WS_URL || 'ws://localhost:8084'}/ws/tracking?shipmentId=${shipmentId}&token=${encodeURIComponent(token)}`
const ws = new WebSocket(url)
const started = Date.now()
let count = 0

ws.onopen = () => console.log('conectado al canal en vivo (Ctrl+C para salir)')
ws.onmessage = (event) => {
  const message = JSON.parse(event.data)
  const at = ((Date.now() - started) / 1000).toFixed(1)
  if (message.type === 'position') {
    count += 1
    console.log(
      `+${at}s  ${message.lat.toFixed(5)},${message.lng.toFixed(5)}${message.snapshot ? '  (snapshot)' : ''}`
    )
  } else {
    console.log(`+${at}s  ${JSON.stringify(message)}`)
  }
}
ws.onclose = () => console.log(`cerrado (${count} posiciones recibidas)`)
ws.onerror = (error) => console.error('error de WebSocket', error.message ?? '')
