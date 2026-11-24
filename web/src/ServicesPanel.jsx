import { useEffect, useState } from 'react'
import { stats } from './api.js'
import { STATUS_LABEL } from './labels.js'

const CHARACTERS = [
  { key: 'gateway', name: 'El portero', tech: 'api-gateway', color: '#7c3aed', role: 'abre la puerta y reparte' },
  { key: 'auth', name: 'El recepcionista', tech: 'auth-service', color: '#2563eb', role: 'da los carnés' },
  { key: 'shipment', name: 'El jefe de pedidos', tech: 'shipment-service', color: '#16a34a', role: 'guarda la libreta' },
  { key: 'dispatch', name: 'El coordinador', tech: 'dispatch-service', color: '#ea580c', role: 'busca repartidor' },
  { key: 'tracking', name: 'El rastreador', tech: 'tracking-service', color: '#0891b2', role: 'mira el GPS' },
  { key: 'routing', name: 'El navegador', tech: 'routing-eta-service', color: '#db2777', role: 'calcula la ruta' },
  { key: 'notification', name: 'El cartero', tech: 'notification-service', color: '#ca8a04', role: 'manda el correo' }
]

const RESPONSIBLE = {
  CREATED: 'Jefe de pedidos',
  ASSIGNED: 'Coordinador → Jefe de pedidos',
  PICKED_UP: 'Rastreador → Jefe de pedidos',
  IN_TRANSIT: 'Rastreador → Jefe de pedidos',
  DELIVERED: 'Rastreador → Jefe de pedidos',
  NO_DRIVER: 'Coordinador → Jefe de pedidos',
  CANCELLED: 'Jefe de pedidos'
}

function secondsAgo(iso) {
  if (!iso) return null
  const diff = (Date.now() - new Date(iso).getTime()) / 1000
  if (diff < 2) return 'ahora mismo'
  if (diff < 60) return `hace ${Math.round(diff)} s`
  return `hace ${Math.round(diff / 60)} min`
}

export default function ServicesPanel({ shipment, dispatch, tracking, notifications }) {
  const [, tick] = useState(0)
  useEffect(() => {
    const timer = setInterval(() => tick((value) => value + 1), 1000)
    return () => clearInterval(timer)
  }, [])

  const lastPosition = tracking?.lastPosition
  const notification = notifications?.[0]
  const history = shipment?.history ?? []
  const pendingOffers = dispatch?.pendingOffers ?? []
  const offerSeconds = pendingOffers.length
    ? Math.max(0, Math.round((new Date(pendingOffers[0].expiresAt).getTime() - Date.now()) / 1000))
    : null
  const offerNames = pendingOffers.map((offer) => offer.driverName.split(' ')[0]).join(' y ')

  const detail = {
    gateway: `${stats.gateway} peticiones reenviadas`,
    auth: stats.auth > 0 ? `sesión activa · ${stats.auth} llamadas` : 'sin llamadas en esta sesión',
    shipment: shipment
      ? `${STATUS_LABEL[shipment.status] ?? shipment.status} · ${history.length} cambios`
      : 'sin envío seleccionado',
    dispatch: pendingOffers.length
      ? `Oferta a ${offerNames} · quedan ${offerSeconds} s`
      : dispatch
        ? `fase ${dispatch.phase} · ronda ${dispatch.round}`
        : 'sin actividad de despacho',
    tracking: lastPosition
      ? `última posición ${secondsAgo(lastPosition.at)} · ${tracking?.subscribers ?? 0} mirando`
      : 'esperando el primer ping',
    routing:
      tracking?.etaSeconds != null
        ? `ETA ${Math.round(tracking.etaSeconds / 60)} min · ${tracking.distanceToDropoffMeters ?? '?'} m`
        : 'sin consulta de ruta todavía',
    notification: notification
      ? `${notification.status} · intento ${notification.attempts} · ${notification.recipient}`
      : 'sin correos para este envío'
  }

  const state = {
    gateway: 'activo',
    auth: 'activo',
    shipment: shipment?.status ?? 'en espera',
    dispatch: dispatch?.phase ?? 'en espera',
    tracking: lastPosition ? 'recibiendo GPS' : 'en espera',
    routing: tracking?.etaSeconds != null ? 'calculando' : 'en espera',
    notification: notification?.status ?? 'en espera'
  }

  const working = {
    gateway: true,
    auth: true,
    shipment: Boolean(shipment),
    dispatch:
      (dispatch && dispatch.phase !== 'COMPLETED' && dispatch.phase !== 'CANCELLED') ||
      pendingOffers.length > 0,
    tracking: Boolean(lastPosition),
    routing: tracking?.etaSeconds != null,
    notification: notification?.status === 'SENT'
  }

  return (
    <div className="services-panel">
      <p className="muted">
        Esto es lo que está haciendo cada servicio <b>ahora mismo</b> con el envío seleccionado.
      </p>

      {CHARACTERS.map((character) => (
        <div
          key={character.key}
          className={`character ${working[character.key] ? 'working' : ''}`}
          style={{ borderLeftColor: character.color }}
        >
          <div className="character-head">
            <span className="dot" style={{ background: character.color }} />
            <strong>{character.name}</strong>
            <span className="character-state">{state[character.key]}</span>
          </div>
          <div className="character-tech">
            {character.tech} · {character.role}
          </div>
          <div className="character-detail">{detail[character.key]}</div>
        </div>
      ))}

      {history.length > 0 && (
        <div className="chain">
          <h3>Quién hizo qué</h3>
          <ol>
            {history.map((step, index) => (
              <li key={`${step.to}-${index}`}>
                <span className={`chip chip-${step.to.toLowerCase()}`}>
                  {STATUS_LABEL[step.to] ?? step.to}
                </span>
                <span className="chain-who">{RESPONSIBLE[step.to] ?? '—'}</span>
                <time>{new Date(step.occurredAt).toLocaleTimeString()}</time>
              </li>
            ))}
            {notification && (
              <li>
                <span className="chip chip-delivered">Correo</span>
                <span className="chain-who">Cartero</span>
                <time>{new Date(notification.createdAt).toLocaleTimeString()}</time>
              </li>
            )}
          </ol>
        </div>
      )}
    </div>
  )
}
