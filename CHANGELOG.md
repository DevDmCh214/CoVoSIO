# CHANGELOG

## [0.7.0] - 2026-03-27
### Added
- UC-P06 : passenger reviews the driver (POST /reservations/:id/review) — R04, R05 enforced
- UC-D09 : driver reviews the passenger (POST /reservations/:id/review) — R04, R05 enforced
- ReviewDirection enum (PASSENGER_TO_DRIVER, DRIVER_TO_PASSENGER)
- Driver avg_rating recalculated after each PASSENGER_TO_DRIVER review
- Flyway migration V6 — reviews table with UNIQUE(reservation_id, direction)

## [0.6.0] - 2026-03-27
### Added
- UC-P03 : passenger books a trip (POST /reservations) — R01, R02 enforced
- UC-P04 : passenger cancels a reservation (DELETE /reservations/:id) — R03 enforced
- UC-P05 : passenger views own reservations (GET /reservations/me)
- UC-D08 : driver views reservations for a trip (GET /trips/:id/reservations)
- ReservationStatus enum (PENDING, CONFIRMED, CANCELLED)
- Pessimistic write lock on Trip for atomic seat decrement (R02)

## [0.5.0] - 2026-03-27
### Added
- UC-D04 : driver publishes a trip (POST /trips) — R08 enforced
- UC-D05 : driver edits a trip (PUT /trips/:id) — R07 enforced
- UC-D06 : driver cancels a trip (DELETE /trips/:id) — R06 cascade
- UC-D07 : driver lists own trips (GET /trips/me)
- UC-P01 : passenger searches trips (GET /trips)
- UC-P02 : passenger views trip detail (GET /trips/:id)
- UC-P07 : passenger map view (GET /trips/map)
- UC-D10 : driver map view (GET /trips/map/me)

## [0.4.0] - 2026-03-27
### Added
- UC-D11 : driver uploads verification documents (POST /documents)
- UC-D12 : driver views own documents (GET /users/me/documents, GET /users/me/documents/:id/file)

## [0.3.0] - 2026-03-27
### Added
- UC-D01  : driver adds a car (POST /cars) — R09 enforced
- UC-D01b : driver deletes a car (DELETE /cars/:id)
- Driver lists own cars (GET /cars/me)

## [0.2.0] - 2026-03-27
### Added
- UC-C05 : view own profile (GET /users/me)
- UC-C06 : update own profile (PUT /users/me)
- UC-C07 : change password (PUT /users/me/password)
- UC-C08 : view public profile (GET /users/:id)

## [0.1.0] - 2026-03-27
### Added
- UC-C01 : register (POST /auth/register)
- UC-C02 : login (POST /auth/login)
- UC-C03 : token refresh (POST /auth/refresh)
- UC-C04 : logout (POST /auth/logout) — R10 enforced
