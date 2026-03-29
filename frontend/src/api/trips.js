import { api } from './client'

/**
 * Fetch all available trips for the public Leaflet map (UC-P07).
 * @returns {Promise<Array>} list of TripMapResponse
 */
export async function fetchMapTrips() {
  return api.get('/trips/map')
}

/**
 * Search trips with optional origin / destination / date filters (UC-P01).
 * Returns a Spring Page — callers read `.content`.
 * @param {{ origin?: string, destination?: string, date?: string }} filters
 */
export async function searchTrips({ origin, destination, date } = {}) {
  return api.get('/trips', { origin, destination, date, size: 50 })
}
