import { useCallback, useEffect, useRef, useState } from 'react'
import MapView from './Map.jsx'
import { api, createShipment, openTrackingSocket } from './api.js'

const PRESETS = [
  {
    label: 'Parque Principal → Terminal',
    pickup: { lat: 4.3449, lng: -74.3674, address: 'Parque Principal, Fusagasugá' },
    dropoff: { lat: 4.3374, lng: -74.36, address: 'Terminal de Transportes' }
  },
  {
    label: 'Balmoral → Unisur',
    pickup: { lat: 4.348, lng: -74.37, address: 'Balmoral' },
    dropoff: { lat: 4.335, lng: -74.365, address: 'Unisur' }
  },
  {
    label: 'Centro → Hospital',
    pickup: { lat: 4.3415, lng: -74.3655, address: 'Centro' },
    dropoff: { lat: 4.3468, lng: -74.3712, address: 'Hospital San Rafael' }
  }
]

const STATUS_LABEL = {
  CREATED: 'Buscando conductor',
  ASSIGNED: 'Conductor asignado',
  PICKED_UP: 'Recogido',
  IN_TRANSIT: 'En camino',
  DELIVERED: 'Entregado',
  FAILED: 'Fallido',
  CANCELLED: 'Cancelado',
  NO_DRIVER: 'Sin conductor'
}

export default function App() {
  const [token, setToken] = useState(() => localStorage.getItem('enruta.token'))
  const [email, setEmail] = useState('cliente@enruta.test')
  const [password, setPassword] = useState('Cliente1234!')
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const [shipments, setShipments] = useState([])
  const [selectedId, setSelectedId] = useState(null)
  const [shipment, setShipment] = useState(null)
  const [position, setPosition] = useState(null)
  const [trail, setTrail] = useState([])
  const [eta, setEta] = useState(null)
  const [distance, setDistance] = useState(null)
  const [socketState, setSocketState] = useState('off')

  const socketRef = useRef(null)
  const selectedRef = useRef(null)
  selectedRef.current = selectedId

  const loadList = useCallback(async (authToken) => {
    try {
      const page = await api.listShipments(authToken)
      setShipments(page.content ?? [])
      return page.content ?? []
    } catch (loadError) {
      if (loadError.status === 401) {
        localStorage.removeItem('enruta.token')
        setToken(null)
      }
      return []
    }
  }, [])

  useEffect(() => {
    if (token) loadList(token)
  }, [token, loadList])

  // Sondeo del estado: el WebSocket trae posiciones, el estado viene por REST.
  useEffect(() => {
    if (!token || !selectedId) return
    let cancelled = false

    const poll = async () => {
      try {
        const current = await api.getShipment(token, selectedId)
        if (cancelled) return
        setShipment(current)
        try {
          const tracking = await api.trackingState(token, selectedId)
          if (!cancelled) {
            setEta(tracking.etaSeconds ?? null)
            setDistance(tracking.distanceToDropoffMeters ?? null)
          }
        } catch {
          /* tracking puede no tener al conductor aun */
        }
      } catch {
        /* reintenta en el siguiente tick */
      }
    }

    poll()
    const timer = setInterval(poll, 2000)
    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [token, selectedId])

  // WebSocket: posiciones en vivo con reconexion simple.
  useEffect(() => {
    if (!token || !selectedId) return
    let closed = false
    let reconnectTimer = null

    const connect = () => {
      if (closed) return
      const socket = openTrackingSocket(selectedId, token, {
        onOpen: () => setSocketState('on'),
        onClose: () => {
          setSocketState('off')
          if (!closed) reconnectTimer = setTimeout(connect, 3000)
        },
        onMessage: (message) => {
          if (message.type === 'position') {
            const point = { lat: message.lat, lng: message.lng }
            setPosition(point)
            setTrail((current) => [...current.slice(-400), [message.lat, message.lng]])
          }
        }
      })
      socketRef.current = socket
    }

    setTrail([])
    setPosition(null)
    connect()

    return () => {
      closed = true
      if (reconnectTimer) clearTimeout(reconnectTimer)
      socketRef.current?.close()
    }
  }, [token, selectedId])

  const handleLogin = async (event) => {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const session = await api.login(email, password)
      localStorage.setItem('enruta.token', session.accessToken)
      setToken(session.accessToken)
      const list = await loadList(session.accessToken)
      if (list.length > 0) setSelectedId(list[0].id)
    } catch (loginError) {
      setError(loginError.message)
    } finally {
      setBusy(false)
    }
  }

  const handleCreate = async (preset) => {
    setBusy(true)
    setError(null)
    try {
      const created = await createShipment(
        token,
        preset.pickup,
        preset.dropoff,
        'Envio creado desde la demo'
      )
      await loadList(token)
      setSelectedId(created.id)
    } catch (createError) {
      setError(createError.message)
    } finally {
      setBusy(false)
    }
  }

  const handleLogout = () => {
    localStorage.removeItem('enruta.token')
    setToken(null)
    setSelectedId(null)
    setShipment(null)
    setShipments([])
  }

  if (!token) {
    return (
      <div className="login-screen">
        <form className="login-card" onSubmit={handleLogin}>
          <h1>
            En<span>Ruta</span>
          </h1>
          <p className="muted">Última milla en vivo · Fusagasugá</p>
          <label>
            Correo
            <input value={email} onChange={(event) => setEmail(event.target.value)} />
          </label>
          <label>
            Contraseña
            <input
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
            />
          </label>
          {error && <div className="error">{error}</div>}
          <button disabled={busy}>{busy ? 'Entrando…' : 'Entrar'}</button>
          <p className="hint">Demo: cliente@enruta.test / Cliente1234!</p>
        </form>
      </div>
    )
  }

  const status = shipment?.status
  const driver = shipment?.driverId

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          En<span>Ruta</span>
        </div>
        <div className="topbar-right">
          <span className={`socket socket-${socketState}`}>
            {socketState === 'on' ? '● en vivo' : '○ reconectando'}
          </span>
          <button className="ghost" onClick={handleLogout}>
            Salir
          </button>
        </div>
      </header>

      <aside className="sidebar">
        <section className="panel">
          <h2>Nuevo envío</h2>
          {PRESETS.map((preset) => (
            <button
              key={preset.label}
              className="preset"
              disabled={busy}
              onClick={() => handleCreate(preset)}
            >
              {preset.label}
            </button>
          ))}
          {error && <div className="error">{error}</div>}
        </section>

        <section className="panel">
          <h2>Envíos recientes</h2>
          <ul className="shipment-list">
            {shipments.map((item) => (
              <li key={item.id}>
                <button
                  className={item.id === selectedId ? 'active' : ''}
                  onClick={() => setSelectedId(item.id)}
                >
                  <span className={`chip chip-${item.status.toLowerCase()}`}>
                    {STATUS_LABEL[item.status] ?? item.status}
                  </span>
                  <span className="shipment-id">#{item.id.slice(0, 8)}</span>
                </button>
              </li>
            ))}
            {shipments.length === 0 && <li className="muted">Todavía no hay envíos.</li>}
          </ul>
        </section>

        {shipment && (
          <section className="panel">
            <h2>Seguimiento</h2>
            <div className={`status status-${status?.toLowerCase()}`}>
              {STATUS_LABEL[status] ?? status}
            </div>
            <dl className="facts">
              <dt>Conductor</dt>
              <dd>{driver ? driver.slice(0, 8) : '—'}</dd>
              <dt>Distancia</dt>
              <dd>{distance != null ? `${distance} m` : '—'}</dd>
              <dt>ETA</dt>
              <dd>{eta != null ? `${Math.round(eta / 60)} min` : '—'}</dd>
            </dl>
            <ol className="timeline">
              {(shipment.history ?? []).map((step, index) => (
                <li key={`${step.to}-${index}`} className={index === 0 ? '' : 'past'}>
                  <span>{STATUS_LABEL[step.to] ?? step.to}</span>
                  <time>{new Date(step.occurredAt).toLocaleTimeString()}</time>
                </li>
              ))}
            </ol>
          </section>
        )}
      </aside>

      <main className="map-area">
        <MapView
          pickup={shipment?.pickup}
          dropoff={shipment?.dropoff}
          position={position}
          trail={trail}
        />
        {!shipment && (
          <div className="map-hint">Crea un envío o elige uno de la lista para seguirlo.</div>
        )}
      </main>
    </div>
  )
}
