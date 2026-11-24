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

export const STATUS_LABEL = {
  CREATED: 'Buscando conductor',
  ASSIGNED: 'Conductor asignado',
  PICKED_UP: 'Recogido',
  IN_TRANSIT: 'En camino',
  DELIVERED: 'Entregado',
  FAILED: 'Fallido',
  CANCELLED: 'Cancelado',
  NO_DRIVER: 'Sin conductor'
}

export default function ClientView({ token }) {
  const [shipments, setShipments] = useState([])
  const [selectedId, setSelectedId] = useState(null)
  const [shipment, setShipment] = useState(null)
  const [position, setPosition] = useState(null)
  const [trail, setTrail] = useState([])
  const [eta, setEta] = useState(null)
  const [distance, setDistance] = useState(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const socketRef = useRef(null)

  const loadList = useCallback(
    async (authToken) => {
      try {
        const page = await api.listShipments(authToken)
        setShipments(page.content ?? [])
        return page.content ?? []
      } catch {
        return []
      }
    },
    []
  )

  useEffect(() => {
    if (token) loadList(token)
  }, [token, loadList])

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
          /* aun sin conductor */
        }
      } catch {
        /* reintenta */
      }
    }
    poll()
    const timer = setInterval(poll, 2000)
    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [token, selectedId])

  useEffect(() => {
    if (!token || !selectedId) return
    let closed = false
    let reconnect = null
    const connect = () => {
      if (closed) return
      socketRef.current = openTrackingSocket(selectedId, token, {
        onMessage: (message) => {
          if (message.type === 'position') {
            setPosition({ lat: message.lat, lng: message.lng })
            setTrail((current) => [...current.slice(-400), [message.lat, message.lng]])
          }
        },
        onClose: () => {
          if (!closed) reconnect = setTimeout(connect, 3000)
        }
      })
    }
    setTrail([])
    setPosition(null)
    connect()
    return () => {
      closed = true
      if (reconnect) clearTimeout(reconnect)
      socketRef.current?.close()
    }
  }, [token, selectedId])

  const handleCreate = async (preset) => {
    setBusy(true)
    setError(null)
    try {
      const created = await createShipment(token, preset.pickup, preset.dropoff, 'Demo')
      await loadList(token)
      setSelectedId(created.id)
    } catch (createError) {
      setError(createError.message)
    } finally {
      setBusy(false)
    }
  }

  const status = shipment?.status

  return (
    <>
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
              <dd>{shipment.driverId ? shipment.driverId.slice(0, 8) : '—'}</dd>
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
    </>
  )
}
