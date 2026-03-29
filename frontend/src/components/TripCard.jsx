import { getRegionImageUrl, getRegionName } from '../utils/regionImages'

function formatDate(iso) {
  return new Date(iso).toLocaleDateString('en-GB', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  })
}

function formatTime(iso) {
  return new Date(iso).toLocaleTimeString('en-GB', {
    hour: '2-digit',
    minute: '2-digit',
  })
}

export default function TripCard({ trip }) {
  const region = getRegionName(trip.originLabel, trip.originLat, trip.originLng)

  return (
    <div className="bg-white rounded-2xl shadow-sm border border-gray-100 overflow-hidden hover:shadow-md hover:-translate-y-0.5 transition-all cursor-pointer group">

      {/* Region landscape image */}
      <div className="h-28 overflow-hidden relative">
        <img
          src={getRegionImageUrl(trip.originLabel, trip.originLat, trip.originLng, 400, 200)}
          alt={region ?? trip.originLabel}
          loading="lazy"
          className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
        />
        {region && (
          <span className="absolute bottom-1.5 left-1.5 bg-black/50 text-white text-[10px] font-medium px-1.5 py-0.5 rounded backdrop-blur-sm">
            {region}
          </span>
        )}
      </div>

      {/* Details */}
      <div className="p-3 space-y-2">
        {/* Route */}
        <div className="space-y-0.5">
          <p className="text-[11px] font-medium text-gray-400 uppercase tracking-wider truncate">
            {trip.originLabel}
          </p>
          <div className="flex items-center gap-1">
            <svg className="w-3 h-3 text-blue-500 shrink-0" fill="currentColor" viewBox="0 0 20 20">
              <path fillRule="evenodd" d="M5.05 4.05a7 7 0 119.9 9.9L10 18.9l-4.95-4.95a7 7 0 010-9.9zM10 11a2 2 0 100-4 2 2 0 000 4z" clipRule="evenodd" />
            </svg>
            <p className="text-sm font-semibold text-gray-800 truncate">
              {trip.destinationLabel}
            </p>
          </div>
        </div>

        {/* Date & time */}
        <div className="flex items-center justify-between text-xs text-gray-500">
          <span>{formatDate(trip.departureAt)}</span>
          <span className="font-medium text-gray-700">{formatTime(trip.departureAt)}</span>
        </div>

        {/* Price & seats */}
        <div className="flex items-center justify-between pt-1 border-t border-gray-50">
          <span className="text-base font-bold text-blue-600">
            €{Number(trip.pricePerSeat).toFixed(2)}
          </span>
          <span className="text-xs text-gray-400">
            {trip.seatsAvailable} seat{trip.seatsAvailable !== 1 ? 's' : ''}
          </span>
        </div>
      </div>
    </div>
  )
}
