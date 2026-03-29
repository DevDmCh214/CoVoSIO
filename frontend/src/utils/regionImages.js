/**
 * Maps France's 13 administrative regions to:
 *  - a picsum seed for a consistent landscape photo
 *  - a list of major cities for fast name-based lookup
 *  - an approximate bounding box [minLat, maxLat, minLng, maxLng] as fallback
 *
 * Lookup priority:
 *   1. City name string match  (instant, no coordinates needed)
 *   2. Coordinate bounding box (for cities not in the list)
 *   3. Generic France image    (ultimate fallback)
 */

const REGIONS = [
  {
    name:   'Île-de-France',
    seed:   'paris-eiffel-seine-sunset',
    cities: [
      'paris', 'versailles', 'boulogne-billancourt', 'saint-denis',
      'argenteuil', 'montreuil', 'vitry-sur-seine', 'créteil',
      'nanterre', 'aulnay-sous-bois', 'vincennes', 'massy',
    ],
    bounds: { minLat: 48.12, maxLat: 49.24, minLng: 1.44, maxLng: 3.56 },
  },
  {
    name:   'Hauts-de-France',
    seed:   'lille-grand-place-flemish',
    cities: [
      'lille', 'amiens', 'roubaix', 'tourcoing', 'dunkerque',
      'valenciennes', 'lens', 'arras', 'calais', 'boulogne-sur-mer',
    ],
    bounds: { minLat: 49.97, maxLat: 51.09, minLng: 1.45, maxLng: 4.27 },
  },
  {
    name:   'Grand Est',
    seed:   'strasbourg-cathedral-alsace-timber',
    cities: [
      'strasbourg', 'reims', 'metz', 'mulhouse', 'nancy', 'colmar',
      'troyes', 'châlons-en-champagne', 'épinal', 'thionville',
    ],
    bounds: { minLat: 47.41, maxLat: 49.98, minLng: 3.39, maxLng: 8.24 },
  },
  {
    name:   'Normandie',
    seed:   'normandie-cliffs-etretat-ocean',
    cities: [
      'le havre', 'rouen', 'caen', 'cherbourg', 'alençon',
      'évreux', 'dieppe', 'bayeux', 'lisieux',
    ],
    bounds: { minLat: 48.39, maxLat: 49.73, minLng: -1.94, maxLng: 2.15 },
  },
  {
    name:   'Bretagne',
    seed:   'bretagne-coastline-lighthouse-atlantic',
    cities: [
      'rennes', 'brest', 'quimper', 'lorient', 'vannes',
      'saint-malo', 'fougères', 'vitré', 'lannion',
    ],
    bounds: { minLat: 47.27, maxLat: 48.85, minLng: -5.15, maxLng: -1.04 },
  },
  {
    name:   'Pays de la Loire',
    seed:   'nantes-chateau-ducs-bretagne-loire',
    cities: [
      'nantes', 'angers', 'le mans', 'saint-nazaire', 'laval',
      'cholet', 'la roche-sur-yon', 'saumur',
    ],
    bounds: { minLat: 46.27, maxLat: 48.37, minLng: -2.55, maxLng: 0.93 },
  },
  {
    name:   'Centre-Val de Loire',
    seed:   'loire-valley-chateau-chambord',
    cities: [
      'orléans', 'tours', 'blois', 'chartres', 'bourges',
      'châteauroux', 'amboise', 'vendôme',
    ],
    bounds: { minLat: 46.35, maxLat: 48.94, minLng: 0.06, maxLng: 3.13 },
  },
  {
    name:   'Bourgogne-Franche-Comté',
    seed:   'dijon-burgundy-vineyards-golden-slopes',
    cities: [
      'dijon', 'besançon', 'belfort', 'mâcon', 'chalon-sur-saône',
      'nevers', 'auxerre', 'montbéliard',
    ],
    bounds: { minLat: 46.15, maxLat: 48.40, minLng: 2.84, maxLng: 7.08 },
  },
  {
    name:   'Auvergne-Rhône-Alpes',
    seed:   'lyon-presquile-rhone-saone-evening',
    cities: [
      'lyon', 'grenoble', 'saint-étienne', 'clermont-ferrand',
      'annecy', 'valence', 'chambéry', 'bourg-en-bresse',
      'roanne', 'thonon-les-bains', 'aurillac',
    ],
    bounds: { minLat: 44.11, maxLat: 46.80, minLng: 2.06, maxLng: 7.18 },
  },
  {
    name:   'Nouvelle-Aquitaine',
    seed:   'bordeaux-place-bourse-miroir-eau',
    cities: [
      'bordeaux', 'limoges', 'poitiers', 'pau', 'la rochelle',
      'angoulême', 'brive-la-gaillarde', 'périgueux', 'agen', 'niort',
    ],
    bounds: { minLat: 42.77, maxLat: 47.57, minLng: -1.79, maxLng: 1.91 },
  },
  {
    name:   'Occitanie',
    seed:   'toulouse-capitole-garonne-pink-city',
    cities: [
      'toulouse', 'montpellier', 'nîmes', 'perpignan', 'albi',
      'carcassonne', 'béziers', 'narbonne', 'rodez', 'tarbes',
    ],
    bounds: { minLat: 42.33, maxLat: 44.93, minLng: 0.16, maxLng: 4.86 },
  },
  {
    name:   "Provence-Alpes-Côte d'Azur",
    seed:   'marseille-calanques-mediterranean-blue',
    cities: [
      'marseille', 'nice', 'toulon', 'aix-en-provence', 'avignon',
      'cannes', 'antibes', 'gap', 'fréjus', 'arles',
    ],
    bounds: { minLat: 43.16, maxLat: 44.92, minLng: 4.23, maxLng: 7.72 },
  },
  {
    name:   'Corse',
    seed:   'corse-porto-gulf-maquis-granite',
    cities: [
      'ajaccio', 'bastia', 'corte', 'calvi', 'porto-vecchio', 'bonifacio',
    ],
    bounds: { minLat: 41.33, maxLat: 43.03, minLng: 8.53, maxLng: 9.57 },
  },
]

const FALLBACK_SEED = 'france-countryside-road-rural'

// ─── Lookup helpers ──────────────────────────────────────────────────────────

function findByCity(cityLabel) {
  const normalized = cityLabel.toLowerCase().trim()
  return REGIONS.find((r) =>
    r.cities.some((c) => normalized.startsWith(c) || normalized.includes(c))
  ) ?? null
}

function findByCoords(lat, lng) {
  if (lat == null || lng == null) return null
  const numLat = Number(lat)
  const numLng = Number(lng)
  return (
    REGIONS.find(
      (r) =>
        numLat >= r.bounds.minLat &&
        numLat <= r.bounds.maxLat &&
        numLng >= r.bounds.minLng &&
        numLng <= r.bounds.maxLng
    ) ?? null
  )
}

// ─── Public API ──────────────────────────────────────────────────────────────

/**
 * Returns the region name for a given city label + optional coordinates.
 * Returns null if no region matches (use fallback image).
 *
 * @param {string} cityLabel  - e.g. "Lyon"
 * @param {number} [lat]      - originLat from TripResponse / TripMapResponse
 * @param {number} [lng]      - originLng from TripResponse / TripMapResponse
 * @returns {string|null}
 */
export function getRegionName(cityLabel = '', lat, lng) {
  return (findByCity(cityLabel) ?? findByCoords(lat, lng))?.name ?? null
}

/**
 * Returns a picsum.photos URL for the region that contains the given city.
 * Falls back gracefully if the region is unknown.
 *
 * @param {string} cityLabel
 * @param {number} [lat]
 * @param {number} [lng]
 * @param {number} [width=400]
 * @param {number} [height=200]
 * @returns {string}
 */
export function getRegionImageUrl(cityLabel = '', lat, lng, width = 400, height = 200) {
  const region = findByCity(cityLabel) ?? findByCoords(lat, lng)
  const seed   = region?.seed ?? FALLBACK_SEED
  return `https://picsum.photos/seed/${seed}/${width}/${height}`
}

/**
 * Convenience export: all region names for UI filters / legends.
 */
export const REGION_NAMES = REGIONS.map((r) => r.name)
