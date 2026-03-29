# CoVoSIO API — AI Reference

**Base:** `http://localhost:4000` | **Format:** JSON | **Auth:** `Authorization: Bearer <accessToken>`
**Roles:** `ROLE_PASSENGER` `ROLE_DRIVER` `ROLE_ADMIN`

---

## COMMON

**Pagination params:** `page`(0) `size`(10) `sort`(field,asc|desc)
**Page envelope:** `{content:[…], totalElements, totalPages, size, number, first, last}`
**Error envelope:** `{status, error, message, timestamp}`
**Error codes:** `400 BUSINESS_RULE_VIOLATION` | `400 VALIDATION_ERROR` | `403 ACCESS_DENIED` | `404 RESOURCE_NOT_FOUND` | `500 INTERNAL_ERROR`

**Enums:**
- TripStatus: `AVAILABLE` `CANCELLED` `COMPLETED`
- ReservationStatus: `PENDING` `CONFIRMED` `CANCELLED`
- ApplicationStatus: `PENDING` `APPROVED` `REJECTED`
- DocumentType: `LICENSE` `CAR_REGISTRATION`
- DocumentStatus: `PENDING` `APPROVED` `REJECTED`
- ReviewDirection: `PASSENGER_TO_DRIVER` `DRIVER_TO_PASSENGER`

---

## IDENTITY MODEL

- `users` table = all platform users (passengers + drivers share the same row)
- `passenger_profiles` = created at registration, always exists for every user
- `driver_profiles` = created only when a driver application is APPROVED; its existence means the user is a driver
- `admins` = fully separate table, separate auth; no row in `users`
- **Role detection:** `DRIVER` if `driver_profiles` row exists, else `PASSENGER`
- **Email uniqueness** is enforced across both `users` and `admins` tables

---

## BUSINESS RULES

| Code | Rule |
|------|------|
| R01 | Passenger cannot book own trip |
| R02 | Booking blocked if seats insufficient (atomic pessimistic lock) |
| R03 | Cancellation blocked <2h before departure |
| R04 | Review only after trip COMPLETED |
| R05 | One review per reservation per direction — enforced by DB unique constraint |
| R06 | Cancelling a trip cascade-cancels all non-cancelled reservations |
| R07 | Trip with CONFIRMED reservations: only `originLabel` editable |
| R08 | Creating a trip requires: (1) `driver_profiles` row exists, (2) `car.registrationVerified=true` |
| R09 | Deleting a car blocked if a future AVAILABLE trip uses it |
| R10 | Refresh token stored and revocable server-side |
| R11 | Account suspension immediately revokes all refresh tokens |

---

## AUTH (no token required)

**Response shape** (register / login / refresh):
`{accessToken, refreshToken, tokenType:"Bearer", role, email, firstName, lastName}`

> **Admin login:** `refreshToken` is always `""` — admins are stateless (no refresh flow).
> **Login order:** admins table is checked first, then users table.

| Method | Path | Body | Returns | Errors |
|--------|------|------|---------|--------|
| POST | `/auth/register` | `{email, password(min8), firstName, lastName, phone?}` | 201 tokens + role=PASSENGER | 400 validation / duplicate (checked across users+admins) |
| POST | `/auth/login` | `{email, password}` | 200 tokens | 400 bad credentials / suspended |
| POST | `/auth/refresh` | `{refreshToken}` | 200 tokens (new accessToken, same refreshToken) | 400 invalid / expired / revoked |
| POST | `/auth/logout` | `{refreshToken}` | 204 | 400 token not found |

---

## USERS

**UserProfileResponse:** `{id, email, firstName, lastName, phone, avatarUrl, avgRating, isActive, createdAt, role}`
**PublicUserResponse:** `{id, firstName, lastName, avatarUrl, avgRating, role}`

| Method | Path | Auth | Body | Returns | Errors |
|--------|------|------|------|---------|--------|
| GET | `/users/me` | any | — | 200 UserProfileResponse | — |
| PUT | `/users/me` | any | `{firstName, lastName, phone?, avatarUrl?}` | 200 UserProfileResponse | 400 |
| PUT | `/users/me/password` | any | `{currentPassword, newPassword(min8)}` | 204 | 400 wrong current password |
| GET | `/users/{id}` | any | — | 200 PublicUserResponse | 404 |

---

## CARS (ROLE_DRIVER only)

**CarResponse:** `{id, brand, model, color, plate, totalSeats, registrationVerified, createdAt}`

| Method | Path | Body | Returns | Errors |
|--------|------|------|---------|--------|
| POST | `/cars` | `{brand, model, color, plate(unique), totalSeats(1-9)}` | 201 CarResponse | 400 403 |
| GET | `/cars/me` | — | 200 CarResponse[] | — |
| DELETE | `/cars/{id}` | — | 204 | 400(R09) 403 404 |

---

## TRIPS

**TripResponse:** `{id, driverId, driverFirstName, driverLastName, driverAvgRating, carId, carBrand, carModel, carColor, originLabel, originLat, originLng, destinationLabel, destLat, destLng, departureAt, seatsAvailable, pricePerSeat, petsAllowed, smokingAllowed, status, createdAt}`
**TripMapResponse:** `{id, originLabel, originLat, originLng, destinationLabel, destLat, destLng, seatsAvailable, pricePerSeat, departureAt, status}`

**POST/PUT body:** `{carId, originLabel, originLat, originLng, destinationLabel, destLat, destLng, departureAt(future ISO-8601), seatsAvailable(min1), pricePerSeat(≥0), petsAllowed?, smokingAllowed?}`

| Method | Path | Auth | Notes | Returns | Errors |
|--------|------|------|-------|---------|--------|
| POST | `/trips` | DRIVER | R08: driver_profiles row + car.registrationVerified required | 201 TripResponse | 400 403 404 |
| GET | `/trips` | any | Query: `origin`, `destination`, `date(yyyy-MM-dd)` + pagination. AVAILABLE with seats only | 200 Page\<TripResponse\> | — |
| GET | `/trips/{id}` | any | — | 200 TripResponse | 404 |
| PUT | `/trips/{id}` | DRIVER (owner) | R07: if CONFIRMED reservations exist, only `originLabel` editable | 200 TripResponse | 400 403 404 |
| DELETE | `/trips/{id}` | DRIVER (owner) | R06: cascade-cancels all reservations | 204 | 400 403 404 |
| GET | `/trips/me` | DRIVER | Driver's own trips, paginated | 200 Page\<TripResponse\> | — |
| GET | `/trips/map` | any | AVAILABLE with seats only, default size 100 | 200 Page\<TripMapResponse\> | — |
| GET | `/trips/map/me` | DRIVER | All driver's own trips, all statuses | 200 Page\<TripMapResponse\> | — |

---

## RESERVATIONS

**ReservationResponse:** `{id, tripId, tripOriginLabel, tripDestinationLabel, tripDepartureAt, tripPricePerSeat, passengerId, passengerFirstName, passengerLastName, seatsBooked, status, createdAt}`

| Method | Path | Auth | Body / Notes | Returns | Errors |
|--------|------|------|-------------|---------|--------|
| POST | `/reservations` | PASSENGER | `{tripId, seatsBooked(min1)}` — R01 R02 | 201 ReservationResponse | 400 403 404 |
| DELETE | `/reservations/{id}` | PASSENGER (owner) | R03: blocked <2h before departure | 204 | 400 403 404 |
| GET | `/reservations/me` | PASSENGER | Paginated | 200 Page\<ReservationResponse\> | — |
| GET | `/trips/{tripId}/reservations` | DRIVER (trip owner) | Paginated | 200 Page\<ReservationResponse\> | 403 404 |

---

## REVIEWS

**ReviewResponse:** `{id, reservationId, authorId, authorFirstName, authorLastName, targetId, targetFirstName, targetLastName, direction, rating, comment, createdAt}`

| Method | Path | Auth | Body | Returns | Errors |
|--------|------|------|------|---------|--------|
| POST | `/reservations/{reservationId}/review` | party to reservation | `{rating(1-5), comment?(max1000)}` Direction auto-inferred: driver_profiles row + trip ownership → DRIVER_TO_PASSENGER, else PASSENGER_TO_DRIVER. R04 R05 | 201 ReviewResponse | 400 403 404 |

---

## DOCUMENTS

**DocumentResponse:** `{id, type, mimeType, status, rejectionReason, carId, driverId, driverFirstName, driverLastName, uploadedAt, reviewedAt}`

> `driverId/driverFirstName/driverLastName` are populated from the application's user (for LICENSE docs) or the car's driver (for CAR_REGISTRATION docs). Present in all contexts but most relevant to admin views.

**Upload rules:**
- `LICENSE` — any authenticated user. Creates or reuses an existing `PENDING` driver_application. `carId` ignored.
- `CAR_REGISTRATION` — DRIVER only; `carId` required; car must belong to the caller.
- Files: JPEG / PNG / PDF, max 5 MB, magic-byte validated. Stored as UUID-named files outside public folder.

| Method | Path | Auth | Body / Notes | Returns | Errors |
|--------|------|------|-------------|---------|--------|
| POST | `/documents` | any | `multipart/form-data`: `file`, `type`, `carId?` | 201 DocumentResponse | 400 403 404 |
| GET | `/users/me/documents` | any | Returns docs from caller's PENDING application + docs linked to caller's cars | 200 DocumentResponse[] | — |
| GET | `/users/me/documents/{id}/file` | any (owner only) | Streams the binary file with correct Content-Type | 200 binary | 403 404 |

---

## ADMIN (ROLE_ADMIN — all `/admin/**`)

**AdminUserResponse:** `{id, email, firstName, lastName, phone, avatarUrl, avgRating, isActive, createdAt, role, licenseVerified, licenseNumber?, permissions?}`

> `licenseVerified` = true when a `driver_profiles` row exists for this user. `permissions` only present for ADMIN role users.

### Users

| Method | Path | Body | Returns | Errors |
|--------|------|------|---------|--------|
| GET | `/admin/users` | pagination | 200 Page\<AdminUserResponse\> | — |
| GET | `/admin/users/{id}` | — | 200 AdminUserResponse | 404 |
| PUT | `/admin/users/{id}/role` | `{role: PASSENGER\|DRIVER}` Blocked if driver has trips | 200 AdminUserResponse | 400 404 |
| PUT | `/admin/users/{id}/status` | `{active: bool}` R11: suspension revokes all tokens | 200 AdminUserResponse | 404 |
| DELETE | `/admin/users/{id}` | — Soft-delete (is_active=false) + R11 | 204 | 404 |

### Trips

| Method | Path | Notes | Returns | Errors |
|--------|------|-------|---------|--------|
| GET | `/admin/trips` | All statuses, paginated | 200 Page\<TripResponse\> | — |
| DELETE | `/admin/trips/{id}` | R06 cascade-cancel | 204 | 400 404 |
| GET | `/admin/trips/map` | All statuses, default size 200 | 200 Page\<TripMapResponse\> | — |

### Reservations

| Method | Path | Returns |
|--------|------|---------|
| GET | `/admin/reservations` | 200 Page\<ReservationResponse\> (all statuses, paginated) |

### Reviews

| Method | Path | Body | Returns | Errors |
|--------|------|------|---------|--------|
| PUT | `/admin/reviews/{id}` | `{rating?(1-5), comment?}` null fields keep existing value | 200 ReviewResponse | 404 |
| DELETE | `/admin/reviews/{id}` | — Permanent delete | 204 | 404 |

### Driver Applications

**DriverApplicationResponse:** `{id, userId, userFirstName, userLastName, userEmail, status, rejectionReason, appliedAt, reviewedAt}`

| Method | Path | Notes | Returns | Errors |
|--------|------|-------|---------|--------|
| GET | `/admin/applications` | Query: `status?(PENDING\|APPROVED\|REJECTED)` + pagination | 200 Page\<DriverApplicationResponse\> | 400 |
| PUT | `/admin/applications/{id}/review` | `{status: APPROVED\|REJECTED, rejectionReason?(required if REJECTED)}` APPROVED → inserts `driver_profiles` row; user gains ROLE_DRIVER on next login (R08). REJECTED → sets rejection reason. Already-reviewed applications return 400. | 200 DriverApplicationResponse | 400 404 |

### Documents (CAR_REGISTRATION only — LICENSE review is via Applications)

| Method | Path | Notes | Returns | Errors |
|--------|------|-------|---------|--------|
| GET | `/admin/documents` | Query: `status?(PENDING\|APPROVED\|REJECTED)` + pagination | 200 Page\<DocumentResponse\> | 400 |
| PUT | `/admin/documents/{id}/review` | `{status: APPROVED\|REJECTED, rejectionReason?(required if REJECTED)}` Only accepts `CAR_REGISTRATION` type — returns 400 for LICENSE. APPROVED → sets `car.registrationVerified=true`. Already-reviewed docs return 400. | 200 DocumentResponse | 400 404 |
| POST | `/admin/documents/{id}/notify` | Re-notify driver about pending document (no body) | 200 | 404 |

### Statistics

| Method | Path | Returns |
|--------|------|---------|
| GET | `/admin/stats` | `{totalUsers, totalDrivers, totalPassengers, totalAdmins, totalTrips, totalAvailableTrips, totalCompletedTrips, totalReservations, totalConfirmedReservations, totalReviews, totalPendingDocuments}` — `totalPendingDocuments` counts pending driver applications |
