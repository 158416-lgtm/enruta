const BASE = import.meta.env.VITE_API_URL ?? ''
const WS_BASE = import.meta.env.VITE_WS_URL ?? 'ws://localhost:8084'

async function request(path, { token, method = 'GET', body } = {}) {
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
      /* respuesta sin cuerpo */
    }
    const error = new Error(detail)
    error.status = response.status
    throw error
  }
  return response.status === 204 ? null : response.json()
}

export const api = {
  login: (email, password) =>
    request('/api/v1/auth/login', { method: 'POST', body: { email, password } }),

  listShipments: (token, size = 8) =>
    request(`/api/v1/shipments?page=0&size=${size}`, { token }),

  getShipment: (token, id) => request(`/api/v1/shipments/${id}`, { token }),

  trackingState: (token, id) => request(`/api/v1/tracking/shipments/${id}`, { token }),

  pendingOffers: (token, driverId) =>
    request(`/api/v1/drivers/${driverId}/offers`, { token })
}

/** Crea el envio con el header de idempotencia (el endpoint lo exige). */
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
