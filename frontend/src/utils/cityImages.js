/**
 * Maps 20 French cities to consistent picsum.photos seed strings.
 * Unknown cities fall back to a hash-derived seed so images are always stable.
 */
const CITY_SEEDS = {
  paris:             'paris-haussman-boulevard',
  lyon:              'lyon-fourviere-hill',
  marseille:         'marseille-vieux-port',
  toulouse:          'toulouse-capitole-pink',
  nice:              'nice-promenade-azur',
  nantes:            'nantes-chateau-bretagne',
  montpellier:       'montpellier-place-comedie',
  strasbourg:        'strasbourg-alsace-canal',
  bordeaux:          'bordeaux-place-bourse',
  lille:             'lille-grand-place-nord',
  rennes:            'rennes-parlement-bretagne',
  reims:             'reims-cathedral-champagne',
  'le havre':        'lehavre-oscar-niemeyer',
  grenoble:          'grenoble-bastille-alpes',
  dijon:             'dijon-palais-bourgogne',
  angers:            'angers-chateau-maine',
  nimes:             'nimes-arenes-romaines',
  toulon:            'toulon-rade-provence',
  'saint-etienne':   'saintetienne-cite-design',
  clermont:          'clermont-puy-volcan',
}

const FALLBACK_SEEDS = [
  'french-city-square',
  'european-old-town',
  'france-architecture',
  'urban-street-cafe',
  'city-river-bridge',
]

function stableHash(str) {
  let h = 0
  for (let i = 0; i < str.length; i++) {
    h = Math.imul(31, h) + str.charCodeAt(i) | 0
  }
  return Math.abs(h)
}

/**
 * Returns a picsum.photos URL consistent for the given city name.
 * @param {string} cityLabel
 * @param {number} width
 * @param {number} height
 */
export function getCityImageUrl(cityLabel = '', width = 400, height = 200) {
  const normalized = cityLabel.toLowerCase().trim()

  const matchedKey = Object.keys(CITY_SEEDS).find((key) =>
    normalized.startsWith(key) || normalized.includes(key)
  )

  const seed = matchedKey
    ? CITY_SEEDS[matchedKey]
    : FALLBACK_SEEDS[stableHash(normalized) % FALLBACK_SEEDS.length]

  return `https://picsum.photos/seed/${seed}/${width}/${height}`
}
