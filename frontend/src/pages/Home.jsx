import { useEffect, useMemo, useState } from 'react'

const CARDS_PER_PAGE = 12 // 4 columns × 3 rows
import Navbar from '../components/Navbar'
import Footer from '../components/Footer'
import HomeMap from '../components/HomeMap'
import SearchBar from '../components/SearchBar'
import TripCard from '../components/TripCard'
import { fetchMapTrips, searchTrips } from '../api/trips'

export default function Home() {
  const [allTrips, setAllTrips]       = useState([])
  const [mapTrips, setMapTrips]       = useState([])
  const [loading, setLoading]         = useState(true)

  // Live values in the search inputs
  const [inputFrom, setInputFrom] = useState('')
  const [inputTo,   setInputTo]   = useState('')

  // Applied filters (set when "Go" is clicked)
  const [activeFrom, setActiveFrom] = useState('')
  const [activeTo,   setActiveTo]   = useState('')

  // City selected by clicking a map pin
  const [selectedCity, setSelectedCity] = useState(null)
  const [page, setPage] = useState(0)

  useEffect(() => {
    async function load() {
      try {
        const [tripsData, mapData] = await Promise.all([
          searchTrips(),
          fetchMapTrips(),
        ])
        // searchTrips returns a Spring Page — extract content array
        setAllTrips(tripsData?.content ?? (Array.isArray(tripsData) ? tripsData : []))
        setMapTrips(mapData?.content ?? (Array.isArray(mapData) ? mapData : []))
      } catch (err) {
        console.warn('Failed to load trips:', err)
      } finally {
        setLoading(false)
      }
    }
    load()
  }, [])

  // Filter cards in-place without extra network calls
  const filteredTrips = useMemo(() => {
    return allTrips.filter((trip) => {
      if (selectedCity && trip.originLabel !== selectedCity) return false
      if (activeFrom   && !trip.originLabel.toLowerCase().includes(activeFrom.toLowerCase())) return false
      if (activeTo     && !trip.destinationLabel.toLowerCase().includes(activeTo.toLowerCase())) return false
      return true
    })
  }, [allTrips, selectedCity, activeFrom, activeTo])

  // Reset to first page whenever the visible set changes
  useEffect(() => { setPage(0) }, [filteredTrips])

  const totalPages  = Math.ceil(filteredTrips.length / CARDS_PER_PAGE)
  const pagedTrips  = filteredTrips.slice(page * CARDS_PER_PAGE, (page + 1) * CARDS_PER_PAGE)

  function handleSearch() {
    setActiveFrom(inputFrom)
    setActiveTo(inputTo)
    setSelectedCity(null) // map selection cleared when typing a custom search
  }

  function handleCitySelect(cityLabel) {
    setSelectedCity(cityLabel)
    if (cityLabel) {
      // Mirror city name into the "From" input for visual consistency
      setInputFrom(cityLabel)
      setActiveFrom(cityLabel)
      setInputTo('')
      setActiveTo('')
    } else {
      setInputFrom('')
      setActiveFrom('')
    }
  }

  function clearFilters() {
    setSelectedCity(null)
    setActiveFrom('')
    setActiveTo('')
    setInputFrom('')
    setInputTo('')
  }

  const isFiltered = selectedCity || activeFrom || activeTo

  return (
    <div className="min-h-screen flex flex-col bg-gray-50">
      <Navbar />

      <main className="flex-1">

        {/* ── Map ── */}
        <section className="max-w-5xl mx-auto px-4 pt-6">
          <div className="h-[430px] rounded-2xl overflow-hidden shadow-md border border-gray-200">
            <HomeMap
              trips={mapTrips}
              selectedCity={selectedCity}
              onCitySelect={handleCitySelect}
            />
          </div>
          {mapTrips.length === 0 && !loading && (
            <p className="text-center text-xs text-gray-400 mt-2">
              No trips on the map yet — be the first to publish one.
            </p>
          )}
        </section>

        {/* ── Search bar ── */}
        <section className="max-w-3xl mx-auto px-4 mt-4">
          <SearchBar
            from={inputFrom}
            to={inputTo}
            onFromChange={setInputFrom}
            onToChange={setInputTo}
            onSearch={handleSearch}
          />
        </section>

        {/* ── Trip cards ── */}
        <section className="max-w-5xl mx-auto px-4 mt-6 pb-12">
          {/* Header row */}
          <div className="flex items-center justify-between mb-3">
            <h2 className="text-sm font-semibold text-gray-600">
              {loading
                ? 'Loading trips…'
                : isFiltered
                  ? `${filteredTrips.length} trip${filteredTrips.length !== 1 ? 's' : ''} found`
                  : `${allTrips.length} available trip${allTrips.length !== 1 ? 's' : ''}`}
            </h2>
            {isFiltered && (
              <button
                onClick={clearFilters}
                className="text-xs text-blue-600 hover:text-blue-800 hover:underline transition-colors"
              >
                Clear filters
              </button>
            )}
          </div>

          {/* Skeleton — 4-column grid */}
          {loading && (
            <div className="grid grid-cols-4 gap-4">
              {Array.from({ length: 8 }).map((_, i) => (
                <div key={i} className="h-52 bg-white rounded-2xl border border-gray-100 animate-pulse" />
              ))}
            </div>
          )}

          {/* Empty state */}
          {!loading && filteredTrips.length === 0 && (
            <div className="py-16 text-center text-gray-400">
              <svg className="w-12 h-12 mx-auto mb-3 text-gray-200" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1}
                  d="M9 20l-5.447-2.724A1 1 0 013 16.382V5.618a1 1 0 011.447-.894L9 7m0 13l6-3m-6 3V7m6 10l4.553 2.276A1 1 0 0021 18.382V7.618a1 1 0 00-.553-.894L15 4m0 13V4m0 0L9 7"
                />
              </svg>
              <p className="text-sm font-medium">No trips found</p>
              <p className="text-xs mt-1">Try adjusting the origin or destination.</p>
            </div>
          )}

          {/* 4-column grid */}
          {!loading && pagedTrips.length > 0 && (
            <div className="grid grid-cols-4 gap-4">
              {pagedTrips.map((trip) => (
                <TripCard key={trip.id} trip={trip} />
              ))}
            </div>
          )}

          {/* Pagination — appears only after 3 full rows (> 12 cards) */}
          {!loading && totalPages > 1 && (
            <div className="flex items-center justify-center gap-4 mt-8">
              <button
                onClick={() => setPage((p) => p - 1)}
                disabled={page === 0}
                className="w-10 h-10 flex items-center justify-center rounded-full border border-gray-200 bg-white text-gray-600 hover:bg-blue-50 hover:border-blue-300 hover:text-blue-600 disabled:opacity-30 disabled:cursor-not-allowed transition-colors shadow-sm"
                aria-label="Previous page"
              >
                <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
                </svg>
              </button>

              <span className="text-sm text-gray-500">
                Page <span className="font-semibold text-gray-800">{page + 1}</span> of {totalPages}
              </span>

              <button
                onClick={() => setPage((p) => p + 1)}
                disabled={page >= totalPages - 1}
                className="w-10 h-10 flex items-center justify-center rounded-full border border-gray-200 bg-white text-gray-600 hover:bg-blue-50 hover:border-blue-300 hover:text-blue-600 disabled:opacity-30 disabled:cursor-not-allowed transition-colors shadow-sm"
                aria-label="Next page"
              >
                <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
                </svg>
              </button>
            </div>
          )}
        </section>
      </main>

      <Footer />
    </div>
  )
}
