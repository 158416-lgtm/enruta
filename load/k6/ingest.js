// Prueba de carga de la ingesta GPS.
//
// k6 no viene instalado; ver docs/operacion.md para instalarlo.
//
//   MAX_BATCHES=100 k6 run load/k6/ingest.js
//
// El limite por conductor es 3 lotes/s en operacion normal. Para medir capacidad del
// pipeline se eleva con enruta.tracking.ingest.max-batches-per-second y se arranca el
// servicio con esa propiedad.
import http from 'k6/http'
import { check, sleep } from 'k6'
import { Rate, Trend } from 'k6/metrics'

const BASE = __ENV.BASE_URL || 'http://localhost:8084'
const DRIVERS = [
  '11111111-1111-1111-1111-111111111101',
  '11111111-1111-1111-1111-111111111102',
  '11111111-1111-1111-1111-111111111103',
  '11111111-1111-1111-1111-111111111104',
  '11111111-1111-1111-1111-111111111105',
  '11111111-1111-1111-1111-111111111106'
]
const CENTER = { lat: 4.3449, lng: -74.3674 }

const accepted = new Rate('gps_accepted')
const ingestLatency = new Trend('gps_ingest_ms')

export const options = {
  scenarios: {
    gps: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 300),
      timeUnit: '1s',
      duration: __ENV.DURATION || '60s',
      preAllocatedVUs: 60,
      maxVUs: 300
    }
  },
  thresholds: {
    gps_accepted: ['rate>0.99'],
    gps_ingest_ms: ['p(95)<200'],
    http_req_failed: ['rate<0.01']
  }
}

export function setup() {
  const tokens = DRIVERS.map((id, index) => {
    const response = http.post(
      `${__ENV.AUTH_URL || 'http://localhost:18081'}/api/v1/auth/login`,
      JSON.stringify({ email: `driver${index + 1}@enruta.test`, password: 'Driver1234!' }),
      { headers: { 'Content-Type': 'application/json' } }
    )
    return JSON.parse(response.body).accessToken
  })
  return { tokens }
}

export default function (data) {
  const index = (__VU + __ITER) % DRIVERS.length
  const id = DRIVERS[index]
  const jitter = (Math.random() - 0.5) * 0.01
  const payload = JSON.stringify({
    points: [{ lat: CENTER.lat + jitter, lng: CENTER.lng + jitter }]
  })

  const response = http.post(`${BASE}/api/v1/tracking/drivers/${id}/location`, payload, {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${data.tokens[index]}`
    },
    tags: { endpoint: 'gps' }
  })

  accepted.add(response.status === 202)
  ingestLatency.add(response.timings.duration)
  check(response, { 'ingesta aceptada (202)': (r) => r.status === 202 })
  sleep(0.05)
}
