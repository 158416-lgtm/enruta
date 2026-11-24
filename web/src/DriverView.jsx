import { useEffect, useRef, useState } from 'react'
import MapView from './Map.jsx'
import { api, DRIVERS } from './api.js'
import { STATUS_LABEL } from './labels.js'

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

/** Distancia haversine en metros (para filtrar pings redundantes). */
function distanceMeters(a, b) {
  const R = 6371000
  const rad = (value) => (value * Math.PI) / 180
  const dLat = rad(b.lat - a.lat)
  const dLng = rad(b.lng - a.lng)
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLng / 2) ** 2
  return 2 * R * Math.asin(Math.sqrt(h))
}

export default function DriverView({ session }) {
  const driver = DRIVERS.find((item) => item.number === session.driverNumber)

  const [offer, setOffer] = useState(null)
  const [now, setNow] = useState(Date.now())
  const [shipment, setShipment] = useState(null)
  const [position, setPosition] = useState(null)
  const [trail, setTrail] = useState([])
  const [trip, setTrip] = useState('idle') // idle | running | done
  const [mode, setMode] = useState('gps') // gps | simulated
  const [accuracy, setAccuracy] = useState(null)
  const [message, setMessage] = useState(null)

  const cancelled = useRef(false)
  const lastPosition = useRef(null)
  const watchId = useRef(null)
  const wakeLock = useRef(null)
  const lastSent = useRef({ at: 0, lat: null, lng: null })

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 500)
    return () => clearInterval(timer)
  }, [])

  // Sin envio: escucha ofertas cada 2 s.
  useEffect(() => {
    if (shipment) return
    let stopped = false
    const poll = async () => {
      try {
        const offers = await api.driverOffers(session.token, driver.id)
        if (!stopped) setOffer(offers?.[0] ?? null)
      } catch {
        /* sin conexion momentanea */
      }
    }
    poll()
    const timer = setInterval(poll, 2000)
    return () => {
      stopped = true
      clearInterval(timer)
    }
  }, [shipment, session.token, driver.id])

  // Con envio: refresca estado y cierra el viaje al entregar.
  useEffect(() => {
    if (!shipment) return
    const timer = setInterval(async () => {
      try {
        const current = await api.getShipment(session.token, shipment.id)
        setShipment(current)
        if (current.status === 'DELIVERED') {
          stopTracking()
          setTrip('done')
          setMessage('Entrega completada. El cliente ya recibió el correo.')
        }
      } catch {
        /* reintenta */
      }
    }, 2000)
    return () => clearInterval(timer)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [shipment?.id, session.token])

  // Limpieza al salir de la pantalla.
  useEffect(() => () => stopTracking(), [])

  function stopTracking() {
    cancelled.current = true
    if (watchId.current != null && navigator.geolocation) {
      navigator.geolocation.clearWatch(watchId.current)
      watchId.current = null
    }
    if (wakeLock.current) {
      wakeLock.current.release?.().catch(() => {})
      wakeLock.current = null
    }
  }

  async function requestWakeLock() {
    try {
      wakeLock.current = await navigator.wakeLock?.request('screen')
    } catch {
      /* no soportado */
    }
  }

  const secondsLeft = offer
    ? Math.max(0, Math.round((new Date(offer.expiresAt).getTime() - now) / 1000))
    : null

  const accept = async () => {
    setMessage(null)
    try {
      const result = await api.acceptOffer(session.token, offer.id)
      let current = null
      for (let attempt = 0; attempt < 12; attempt++) {
        try {
          current = await api.getShipment(session.token, result.shipmentId)
          break
        } catch {
          await sleep(800)
        }
      }
      if (!current) throw new Error('El envío aún no aparece asignado a ti')
      setOffer(null)
      setShipment(current)
      setTrip('idle')
      setMessage('Oferta aceptada. Elige cómo conducir y pulsa iniciar.')
    } catch (error) {
      setMessage(error.message)
      setOffer(null)
    }
  }

  const reject = async () => {
    try {
      await api.rejectOffer(session.token, offer.id)
      setMessage('Oferta rechazada: el despacho probará con otro conductor.')
      setOffer(null)
    } catch (error) {
      setMessage(error.message)
    }
  }

  // ---------- modo simulado (boton) ----------
  const drive = async (points) => {
    const step = Math.max(1, Math.floor(points.length / 16))
    for (let index = 0; index < points.length; index += step) {
      if (cancelled.current) return
      const point = points[index]
      lastPosition.current = point
      setPosition(point)
      setTrail((current) => [...current.slice(-300), [point.lat, point.lng]])
      await api.postLocation(session.token, driver.id, point.lat, point.lng).catch(() => {})
      await sleep(900)
    }
  }

  const dwell = async (times) => {
    for (let index = 0; index < times; index++) {
      if (cancelled.current) return
      const point = lastPosition.current
      if (point) {
        await api.postLocation(session.token, driver.id, point.lat, point.lng).catch(() => {})
      }
      await sleep(900)
    }
  }

  const startSimulated = async () => {
    cancelled.current = false
    setTrip('running')
    setMessage('Conduciendo al origen…')
    try {
      const start = lastPosition.current ?? {
        lat: shipment.pickup.lat,
        lng: shipment.pickup.lng
      }
      const toPickup = await api.osrmRoute(start, shipment.pickup)
      await drive(toPickup)
      setMessage('Llegaste al origen: confirmando recogida…')
      await dwell(3)
      if (cancelled.current) return
      setMessage('Paquete recogido. Ahora al destino…')
      const toDropoff = await api.osrmRoute(shipment.pickup, shipment.dropoff)
      await drive(toDropoff)
      setMessage('Llegaste al destino: confirmando entrega…')
      await dwell(3)
    } catch (error) {
      setMessage(error.message)
      setTrip('idle')
    }
  }

  // ---------- modo GPS real ----------
  const startGps = () => {
    if (!('geolocation' in navigator)) {
      setMessage('Este navegador no expone GPS. Usa el modo simulado.')
      return
    }
    cancelled.current = false
    setTrip('running')
    setMessage('GPS activo. Camina y verás tu posición en el mapa.')

    watchId.current = navigator.geolocation.watchPosition(
      async (reading) => {
        const { latitude, longitude, accuracy: precision } = reading.coords
        const point = { lat: latitude, lng: longitude }
        const previous = lastSent.current
        const moved =
          previous.lat == null ? Number.POSITIVE_INFINITY : distanceMeters(previous, point)
        const elapsed = Date.now() - previous.at
        // Enviamos como mucho cada 2 s y solo si se movio al menos 5 m.
        if (elapsed < 2000 && moved < 5) return
        lastSent.current = { at: Date.now(), lat: latitude, lng: longitude }
        lastPosition.current = point
        setPosition(point)
        setTrail((current) => [...current.slice(-300), [latitude, longitude]])
        setAccuracy(Math.round(precision))
        await api.postLocation(session.token, driver.id, latitude, longitude).catch(() => {})
      },
      (error) => {
        setMessage('GPS: ' + error.message)
        setTrip('idle')
        stopTracking()
      },
      { enableHighAccuracy: true, maximumAge: 0, timeout: 15000 }
    )
    requestWakeLock()
  }

  const start = () => (mode === 'gps' ? startGps() : startSimulated())

  const stop = () => {
    stopTracking()
    setTrip('idle')
    setMessage('Seguimiento detenido.')
  }

  return (
    <>
      <aside className="sidebar">
        <section className="panel">
          <h2>Repartidor</h2>
          <div className="driver-badge">
            <span className="avatar">{driver.name[0]}</span>
            <div>
              <strong>{driver.name}</strong>
              <div className="muted">driver{driver.number}</div>
            </div>
          </div>
        </section>

        {!shipment && (
          <section className="panel">
            <h2>Ofertas para ti</h2>
            {!offer && (
              <p className="muted">Esperando ofertas… (se renuevan cada pocos segundos)</p>
            )}
            {offer && (
              <div className="offer-card">
                <div className="offer-head">
                  <span className="chip chip-assigned">Nueva oferta</span>
                  <span className={`countdown ${secondsLeft <= 10 ? 'warn' : ''}`}>
                    {secondsLeft}s
                  </span>
                </div>
                <p className="offer-distance">
                  Origen a <strong>{offer.distanceMeters} m</strong> de ti
                </p>
                <p className="muted">
                  Envío #{offer.shipmentId.slice(0, 8)} · ronda {offer.round}
                </p>
                <div className="offer-actions">
                  <button className="primary" onClick={accept}>
                    Aceptar
                  </button>
                  <button className="ghost" onClick={reject}>
                    Rechazar
                  </button>
                </div>
              </div>
            )}
          </section>
        )}

        {shipment && (
          <section className="panel">
            <h2>Tu envío</h2>
            <div className={`status status-${shipment.status.toLowerCase()}`}>
              {STATUS_LABEL[shipment.status] ?? shipment.status}
            </div>
            <dl className="facts">
              <dt>Origen</dt>
              <dd>{shipment.pickup.address}</dd>
              <dt>Destino</dt>
              <dd>{shipment.dropoff.address}</dd>
              <dt>PIN</dt>
              <dd>{shipment.pickupPin}</dd>
              {accuracy != null && (
                <>
                  <dt>Precisión GPS</dt>
                  <dd>{accuracy} m</dd>
                </>
              )}
            </dl>

            {trip !== 'running' && (
              <div className="mode-switch">
                <button
                  className={mode === 'gps' ? 'seg active' : 'seg'}
                  onClick={() => setMode('gps')}
                  disabled={trip === 'done'}
                >
                  GPS real
                </button>
                <button
                  className={mode === 'simulated' ? 'seg active' : 'seg'}
                  onClick={() => setMode('simulated')}
                  disabled={trip === 'done'}
                >
                  Simulado
                </button>
              </div>
            )}

            {trip !== 'running' ? (
              <button className="primary wide" onClick={start} disabled={trip === 'done'}>
                {trip === 'done' ? 'Recorrido completado' : '▶ Iniciar recorrido'}
              </button>
            ) : (
              <button className="ghost wide" onClick={stop}>
                ⏸ Detener
              </button>
            )}
          </section>
        )}

        {message && (
          <section className="panel">
            <p className="muted">{message}</p>
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
          <div className="map-hint">
            Cuando aceptes una oferta, aquí verás la ruta al origen y al destino.
          </div>
        )}
      </main>
    </>
  )
}
