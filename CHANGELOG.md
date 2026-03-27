# CHANGELOG

## [0.8.0] - 2026-03-27
### Added
- UC-A01 : GET /admin/users — paginated list of all users with role-specific fields
- UC-A02 : GET /admin/users/:id — full user details
- UC-A03–A05 : PUT /admin/users/:id/role — role change (PASSENGER/DRIVER/ADMIN) via TPT native SQL
- UC-A05 : PUT /admin/users/:id/status — suspend/activate + R11 (refresh token revocation)
- UC-A06 : DELETE /admin/users/:id — soft delete (isActive = false + R11)
- UC-A07 : GET /admin/trips — all trips regardless of status
- UC-A08 : DELETE /admin/trips/:id — admin trip cancellation (R06 cascade)
- UC-A09 : GET /admin/reservations — all reservations
- UC-A10 : PUT/DELETE /admin/reviews/:id — review moderation
- UC-A11 : GET /admin/stats — platform statistics (counts per entity/status)
- UC-A12 : GET /admin/trips/map — global map (all trips, all statuses)
- UC-A13 : GET /admin/documents?status=PENDING — driver documents by status
- UC-A14 : PUT /admin/documents/:id/review — approve/reject document; sets licenseVerified/registrationVerified (R08)
- UC-A15 : POST /admin/documents/:id/notify — re-notify driver about pending document
- AdminService, AdminController, 6 admin DTOs (AdminUserResponse, AdminUserRoleRequest, AdminUserStatusRequest, AdminDocumentReviewRequest, AdminReviewModerationRequest, PlatformStatsResponse)
- Flyway migration V7 — v_platform_stats view for admin dashboard
- 35 AdminControllerTest integration tests — 156 total, 0 failures

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
