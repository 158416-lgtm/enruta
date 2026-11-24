import { useEffect, useRef } from 'react'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

const CENTER = [4.3449, -74.3674]

// Iconos por divIcon: evita el problema clasico de los assets de Leaflet con bundlers.
const driverIcon = L.divIcon({
  className: '',
  html: '<div class="pin pin-driver">🛵</div>',
  iconSize: [34, 34],
  iconAnchor: [17, 17]
})

const pickupIcon = L.divIcon({
  className: '',
  html: '<div class="pin pin-pickup">A</div>',
  iconSize: [26, 26],
  iconAnchor: [13, 13]
})

const dropoffIcon = L.divIcon({
  className: '',
  html: '<div class="pin pin-dropoff">B</div>',
  iconSize: [26, 26],
  iconAnchor: [13, 13]
})

export default function MapView({ pickup, dropoff, position, trail }) {
  const containerRef = useRef(null)
  const mapRef = useRef(null)
  const driverRef = useRef(null)
  const pickupRef = useRef(null)
  const dropoffRef = useRef(null)
  const trailRef = useRef(null)
  const lineRef = useRef(null)
  const centeredRef = useRef(false)

  useEffect(() => {
    const map = L.map(containerRef.current, { zoomControl: true }).setView(CENTER, 15)
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; OpenStreetMap · EnRuta',
      maxZoom: 19
    }).addTo(map)
    mapRef.current = map
    return () => {
      map.remove()
      mapRef.current = null
    }
  }, [])

  useEffect(() => {
    const map = mapRef.current
    if (!map || !pickup || !dropoff) return
    if (pickupRef.current) pickupRef.current.remove()
    if (dropoffRef.current) dropoffRef.current.remove()
    if (lineRef.current) lineRef.current.remove()

    pickupRef.current = L.marker([pickup.lat, pickup.lng], { icon: pickupIcon })
      .addTo(map)
      .bindPopup(`Origen: ${pickup.address ?? ''}`)
    dropoffRef.current = L.marker([dropoff.lat, dropoff.lng], { icon: dropoffIcon })
      .addTo(map)
      .bindPopup(`Destino: ${dropoff.address ?? ''}`)
    lineRef.current = L.polyline(
      [
        [pickup.lat, pickup.lng],
        [dropoff.lat, dropoff.lng]
      ],
      { color: '#64748b', weight: 2, dashArray: '6 6', opacity: 0.6 }
    ).addTo(map)

    if (!centeredRef.current) {
      map.fitBounds(lineRef.current.getBounds(), { padding: [60, 60] })
      centeredRef.current = true
    }
  }, [pickup, dropoff])

  useEffect(() => {
    const map = mapRef.current
    if (!map || !position) return
    const point = [position.lat, position.lng]
    if (!driverRef.current) {
      driverRef.current = L.marker(point, { icon: driverIcon }).addTo(map)
      map.setView(point, 16, { animate: true })
    } else {
      driverRef.current.setLatLng(point)
    }
    driverRef.current.bindPopup(
      `Conductor en vivo<br/>${position.lat.toFixed(5)}, ${position.lng.toFixed(5)}`
    )
  }, [position])

  useEffect(() => {
    const map = mapRef.current
    if (!map || !trail || trail.length < 2) return
    if (!trailRef.current) {
      trailRef.current = L.polyline(trail, { color: '#2563eb', weight: 4, opacity: 0.85 }).addTo(
        map
      )
    } else {
      trailRef.current.setLatLngs(trail)
    }
  }, [trail])

  return <div ref={containerRef} className="map" />
}
