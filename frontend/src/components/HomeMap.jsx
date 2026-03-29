import { useEffect, useMemo, useRef, useState } from 'react'
import { MapContainer, TileLayer, CircleMarker, Polyline, Tooltip } from 'react-leaflet'

const DEMO_TRIPS = [
  { id: 'd1', originLabel: 'Paris',      originLat: 48.8566, originLng:  2.3522, destinationLabel: 'Lyon',      destLat: 45.7640, destLng:  4.8357 },
  { id: 'd2', originLabel: 'Paris',      originLat: 48.8566, originLng:  2.3522, destinationLabel: 'Marseille', destLat: 43.2965, destLng:  5.3698 },
  { id: 'd3', originLabel: 'Lyon',       originLat: 45.7640, originLng:  4.8357, destinationLabel: 'Nice',      destLat: 43.7102, destLng:  7.2620 },
  { id: 'd4', originLabel: 'Lyon',       originLat: 45.7640, originLng:  4.8357, destinationLabel: 'Grenoble',  destLat: 45.1885, destLng:  5.7245 },
  { id: 'd5', originLabel: 'Bordeaux',   originLat: 44.8378, originLng: -0.5792, destinationLabel: 'Toulouse',  destLat: 43.6047, destLng:  1.4442 },
  { id: 'd6', originLabel: 'Nantes',     originLat: 47.2184, originLng: -1.5536, destinationLabel: 'Paris',     destLat: 48.8566, destLng:  2.3522 },
  { id: 'd7', originLabel: 'Strasbourg', originLat: 48.5734, originLng:  7.7521, destinationLabel: 'Lyon',      destLat: 45.7640, destLng:  4.8357 },
  { id: 'd8', originLabel: 'Lille',      originLat: 50.6292, originLng:  3.0573, destinationLabel: 'Paris',     destLat: 48.8566, destLng:  2.3522 },
  { id: 'd9', originLabel: 'Rennes',     originLat: 48.1173, originLng: -1.6778, destinationLabel: 'Nantes',    destLat: 47.2184, destLng: -1.5536 },
]

function groupByOrigin(trips) {
  const map = {}
  for (const trip of trips) {
    const key = trip.originLabel
    if (!map[key]) {
      map[key] = {
        label: trip.originLabel,
        lat: Number(trip.originLat),
        lng: Number(trip.originLng),
        destinations: [],
        tripCount: 0,
      }
    }
    map[key].tripCount++
    if (!map[key].destinations.some((d) => d.label === trip.destinationLabel)) {
      map[key].destinations.push({
        label: trip.destinationLabel,
        lat: Number(trip.destLat),
        lng: Number(trip.destLng),
      })
    }
  }
  return Object.values(map)
}

/**
 * Fetches a road-following route from the public OSRM demo server.
 * Returns [[lat, lng], ...] for Leaflet, or null on failure.
 */
async function fetchRoadRoute(fromLat, fromLng, toLat, toLng) {
  try {
    const url =
      `https://router.project-osrm.org/route/v1/driving/` +
      `${fromLng},${fromLat};${toLng},${toLat}` +
      `?overview=full&geometries=geojson`
    const res = await fetch(url)
    if (!res.ok) return null
    const data = await res.json()
    const coords = data.routes?.[0]?.geometry?.coordinates
    if (!coords?.length) return null
    // GeoJSON uses [lng, lat] — Leaflet needs [lat, lng]
    return coords.map(([lng, lat]) => [lat, lng])
  } catch {
    return null
  }
}

export default function HomeMap({ trips, selectedCity, onCitySelect }) {
  const [hoveredCity, setHoveredCity] = useState(null)
  // routeCache: { "CityA|||CityB": [[lat,lng], ...] }
  const [routeCache, setRouteCache] = useState({})
  // Tracks in-flight requests so we don't double-fetch
  const fetching = useRef(new Set())

  const source = trips.length > 0 ? trips : DEMO_TRIPS
  const isDemo = trips.length === 0

  const cities = useMemo(() => groupByOrigin(source), [source])

  // When a city is hovered, fetch road routes for all its destinations
  useEffect(() => {
    if (!hoveredCity) return
    const city = cities.find((c) => c.label === hoveredCity)
    if (!city) return

    city.destinations.forEach((dest) => {
      const key = `${city.label}|||${dest.label}`
      if (routeCache[key] !== undefined || fetching.current.has(key)) return

      fetching.current.add(key)
      fetchRoadRoute(city.lat, city.lng, dest.lat, dest.lng).then((route) => {
        fetching.current.delete(key)
        // Fall back to a straight line if OSRM returned nothing
        const positions = route ?? [[city.lat, city.lng], [dest.lat, dest.lng]]
        setRouteCache((prev) => ({ ...prev, [key]: positions }))
      })
    })
  }, [hoveredCity, cities]) // eslint-disable-line react-hooks/exhaustive-deps

  const mapElements = cities.flatMap((city) => {
    const isSelected = selectedCity === city.label
    const isHovered  = hoveredCity  === city.label
    const elements   = []

    if (isHovered) {
      city.destinations.forEach((dest, i) => {
        const key = `${city.label}|||${dest.label}`
        // Use cached road route; while loading show a straight line
        const positions = routeCache[key] ?? [[city.lat, city.lng], [dest.lat, dest.lng]]

        elements.push(
          <Polyline
            key={`line-${city.label}-${i}`}
            positions={positions}
            pathOptions={{
              color: isSelected ? '#10b981' : '#3b82f6',
              weight: 3,
              opacity: 0.75,
            }}
          />
        )

        // Destination dot
        elements.push(
          <CircleMarker
            key={`dest-${city.label}-${i}`}
            center={[dest.lat, dest.lng]}
            radius={4}
            pathOptions={{ fillColor: '#94a3b8', color: '#fff', weight: 1.5, fillOpacity: 0.9 }}
          >
            <Tooltip direction="top" offset={[0, -6]} opacity={0.9}>
              {dest.label}
            </Tooltip>
          </CircleMarker>
        )
      })
    }

    elements.push(
      <CircleMarker
        key={`marker-${city.label}`}
        center={[city.lat, city.lng]}
        radius={isSelected ? 11 : isHovered ? 9 : 7}
        pathOptions={{
          fillColor: isDemo ? '#94a3b8' : isSelected ? '#10b981' : '#3b82f6',
          color: '#ffffff',
          weight: 2.5,
          fillOpacity: isDemo ? 0.6 : 0.95,
        }}
        eventHandlers={
          isDemo
            ? {}
            : {
                mouseover: () => setHoveredCity(city.label),
                mouseout:  () => setHoveredCity(null),
                click:     () => onCitySelect(isSelected ? null : city.label),
              }
        }
      >
        <Tooltip direction="top" offset={[0, -10]} opacity={0.9}>
          {isDemo
            ? `${city.label} — example`
            : `${city.label}  ·  ${city.tripCount} trip${city.tripCount !== 1 ? 's' : ''} available`}
        </Tooltip>
      </CircleMarker>
    )

    return elements
  })

  return (
    <MapContainer
      center={[46.603354, 1.888334]}
      zoom={6}
      scrollWheelZoom={false}
      className="w-full h-full"
    >
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      {mapElements}
    </MapContainer>
  )
}
