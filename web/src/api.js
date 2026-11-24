const BASE = import.meta.env.VITE_API_URL ?? ''
// Por defecto usa el mismo host de la pagina: Vite hace de proxy hacia tracking.
// Asi funciona igual en localhost, en la LAN y detras de un tunel HTTPS (wss).
const WS_BASE =
  import.meta.env.VITE_WS_URL ??
  `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}`

export const DRIVERS = [
  { number: 1, id: '11111111-1111-1111-1111-111111111101', name: 'Ana' },
  { number: 2, id: '11111111-1111-1111-1111-111111111102', name: 'Beto' },
  { number: 3, id: '11111111-1111-1111-1111-111111111103', name: 'Carla' },
  { number: 4, id: '11111111-1111-1111-1111-111111111104', name: 'Diego' },
  { number: 5, id: '11111111-1111-1111-1111-111111111105', name: 'Elena' },
  { number: 6, id: '11111111-1111-1111-1111-111111111106', name: 'Fabio' }
]

/** Contadores en vivo: cuantas peticiones ha atendido cada personaje. */
export const stats = {
  gateway: 0,
  auth: 0,
  shipment: 0,
  dispatch: 0,
  tracking: 0,
  routing: 0,
  notification: 0
}

function countFor(path) {
  stats.gateway += 1
  if (path.startsWith('/api/v1/auth')) stats.auth += 1
  else if (path.startsWith('/api/v1/shipments')) stats.shipment += 1
  else if (path.startsWith('/api/v1/offers') || path.startsWith('/api/v1/drivers') || path.startsWith('/api/v1/dispatch')) stats.dispatch += 1
  else if (path.startsWith('/api/v1/tracking')) stats.tracking += 1
  else if (path.startsWith('/api/v1/eta')) stats.routing += 1
  else if (path.startsWith('/api/v1/notifications')) stats.notification += 1
}

async function request(path, { token, method = 'GET', body } = {}) {
  countFor(path)
  const response = await fetch(`${BASE}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: body ? JSON.stringify(body) : undefined
  })
  if (!response.ok) {
    let detail = response.statusText
    try {
      const problem = await response.json()
      detail = problem.detail ?? problem.code ?? detail
    } catch {
      /* sin cuerpo */
    }
    const error = new Error(detail)
    error.status = response.status
    throw error
  }
  if (response.status === 204 || response.status === 202) return null
  const text = await response.text()
  return text ? JSON.parse(text) : null
}

export const api = {
  login: (email, password) =>
    request('/api/v1/auth/login', { method: 'POST', body: { email, password } }),

  loginDriver: (number) =>
    request('/api/v1/auth/login', {
      method: 'POST',
      body: { email: `driver${number}@enruta.test`, password: 'Driver1234!' }
    }),

  listShipments: (token, size = 8) =>
    request(`/api/v1/shipments?page=0&size=${size}`, { token }),

  getShipment: (token, id) => request(`/api/v1/shipments/${id}`, { token }),

  trackingState: (token, id) => request(`/api/v1/tracking/shipments/${id}`, { token }),

  dispatchState: (token, id) => request(`/api/v1/dispatch/shipments/${id}`, { token }),

  notifications: (token, id) => request(`/api/v1/notifications?shipmentId=${id}`, { token }),

  driverOffers: (token, driverId) =>
    request(`/api/v1/drivers/${driverId}/offers`, { token }),

  acceptOffer: (token, offerId) =>
    request(`/api/v1/offers/${offerId}/accept`, { token, method: 'POST' }),

  rejectOffer: (token, offerId) =>
    request(`/api/v1/offers/${offerId}/reject`, { token, method: 'POST' }),

  postLocation: (token, driverId, lat, lng) =>
    request(`/api/v1/tracking/drivers/${driverId}/location`, {
      token,
      method: 'POST',
      body: { points: [{ lat, lng }] }
    }),

  /** Ruta real por calles; el proxy de Vite la reenvia a OSRM (sin CORS). */
  osrmRoute: async (from, to) => {
    const response = await fetch(
      `/osrm/route/v1/driving/${from.lng},${from.lat};${to.lng},${to.lat}?overview=full&geometries=geojson`
    )
    if (!response.ok) throw new Error('OSRM no disponible')
    const data = await response.json()
    if (data.code !== 'Ok') throw new Error('OSRM: ' + data.code)
    return data.routes[0].geometry.coordinates.map(([lng, lat]) => ({ lat, lng }))
  }
}

export async function createShipment(token, pickup, dropoff, note) {
  const response = await fetch(`${BASE}/api/v1/shipments`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
      'Idempotency-Key': crypto.randomUUID()
    },
    body: JSON.stringify({ pickup, dropoff, note })
  })
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}))
    throw new Error(problem.detail ?? problem.code ?? 'No se pudo crear el envio')
  }
  return response.json()
}

export function openTrackingSocket(shipmentId, token, handlers) {
  const socket = new WebSocket(
    `${WS_BASE}/ws/tracking?shipmentId=${shipmentId}&token=${encodeURIComponent(token)}`
  )
  socket.onmessage = (event) => {
    try {
      handlers.onMessage?.(JSON.parse(event.data))
    } catch {
      /* mensaje no JSON */
    }
  }
  socket.onopen = () => handlers.onOpen?.()
  socket.onclose = () => handlers.onClose?.()
  socket.onerror = () => handlers.onError?.()
  return socket
}
