import { useState } from 'react'
import ClientView from './ClientView.jsx'
import DriverView from './DriverView.jsx'
import { api, DRIVERS } from './api.js'

const STORAGE_KEY = 'enruta.session'

export default function App() {
  const [session, setSession] = useState(() => {
    try {
      return JSON.parse(sessionStorage.getItem(STORAGE_KEY))
    } catch {
      return null
    }
  })
  const [email, setEmail] = useState('cliente@enruta.test')
  const [password, setPassword] = useState('Cliente1234!')
  const [driverNumber, setDriverNumber] = useState(1)
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const save = (next) => {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(next))
    setSession(next)
  }

  const loginClient = async (event) => {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const result = await api.login(email, password)
      save({ role: 'client', token: result.accessToken, name: result.user.fullName })
    } catch (loginError) {
      setError(loginError.message)
    } finally {
      setBusy(false)
    }
  }

  const loginDriver = async () => {
    setBusy(true)
    setError(null)
    try {
      const result = await api.loginDriver(driverNumber)
      save({
        role: 'driver',
        token: result.accessToken,
        driverNumber,
        name: result.user.fullName
      })
    } catch (loginError) {
      setError(loginError.message)
    } finally {
      setBusy(false)
    }
  }

  const logout = () => {
    sessionStorage.removeItem(STORAGE_KEY)
    setSession(null)
  }

  if (!session) {
    return (
      <div className="login-screen">
        <div className="login-grid">
          <form className="login-card" onSubmit={loginClient}>
            <h1>
              En<span>Ruta</span>
            </h1>
            <p className="muted">Entra como cliente y pide un envío.</p>
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
            <button disabled={busy}>{busy ? 'Entrando…' : 'Entrar como cliente'}</button>
            {error && <div className="error">{error}</div>}
          </form>

          <div className="login-card">
            <h2 className="role-title">Soy repartidor</h2>
            <p className="muted">
              Elige quién eres. Verás las ofertas que te lleguen y decidirás si las aceptas.
            </p>
            <label>
              Repartidor
              <select
                value={driverNumber}
                onChange={(event) => setDriverNumber(Number(event.target.value))}
              >
                {DRIVERS.map((driver) => (
                  <option key={driver.number} value={driver.number}>
                    {driver.name} (driver{driver.number})
                  </option>
                ))}
              </select>
            </label>
            <button type="button" className="primary" onClick={loginDriver} disabled={busy}>
              {busy ? 'Entrando…' : 'Entrar como repartidor'}
            </button>
            <p className="hint">Nadie acepta por ti: la oferta espera tu decisión.</p>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          En<span>Ruta</span>
          <span className={`role-chip role-${session.role}`}>
            {session.role === 'client' ? 'CLIENTE' : `REPARTIDOR ${driverLabel(session)}`}
          </span>
        </div>
        <div className="topbar-right">
          <span className="muted">{session.name}</span>
          <button className="ghost" onClick={logout}>
            Salir
          </button>
        </div>
      </header>

      {session.role === 'client' ? (
        <ClientView token={session.token} />
      ) : (
        <DriverView session={session} />
      )}
    </div>
  )
}

function driverLabel(session) {
  const driver = DRIVERS.find((item) => item.number === session.driverNumber)
  return driver ? driver.name : ''
}
