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
- DocumentType: `LICENSE` `CAR_REGISTRATION`
- DocumentStatus: `PENDING` `APPROVED` `REJECTED`
- ReviewDirection: `PASSENGER_TO_DRIVER` `DRIVER_TO_PASSENGER`

---

## BUSINESS RULES

| Code | Rule |
|------|------|
| R01 | Passenger cannot book own trip |
| R02 | Booking blocked if seats insufficient (atomic) |
| R03 | Cancellation blocked <2h before departure |
| R04 | Review only after trip COMPLETED |
| R05 | One review per reservation per direction |
| R06 | Cancelling trip cascade-cancels all reservations |
| R07 | Trip with CONFIRMED reservations: only `originLabel` editable |
| R08 | Publishing trip requires `licenseVerified=true` AND car `registrationVerified=true`. Approving a LICENSE doc for a PASSENGER atomically promotes them to DRIVER. |
| R09 | Deleting car blocked if future AVAILABLE trip uses it |
| R10 | Refresh token stored & revocable server-side |
| R11 | Account suspension revokes all refresh tokens |

---

## AUTH (no token required)

**Tokens response shape** (register/login/refresh):
`{accessToken, refreshToken, tokenType:"Bearer", role, email, firstName, lastName}`

| Method | Path | Body | Returns | Errors |
|--------|------|------|---------|--------|
| POST | `/auth/register` | `{email, password(min8), firstName, lastName, phone?}` | 201 tokens | 400 validation/duplicate |
| POST | `/auth/login` | `{email, password}` | 200 tokens | 400 bad creds/suspended |
| POST | `/auth/refresh` | `{refreshToken}` | 200 tokens | 400 invalid/expired |
| POST | `/auth/logout` | `{refreshToken}` | 204 | 400 not found |

---

## USERS

**UserProfileResponse:** `{id, email, firstName, lastName, phone, avatarUrl, avgRating, isActive, createdAt, role}`
**PublicUserResponse:** `{id, firstName, lastName, avatarUrl, avgRating, role}`

| Method | Path | Auth | Body | Returns | Errors |
|--------|------|------|------|---------|--------|
| GET | `/users/me` | any | — | 200 UserProfileResponse | — |
| PUT | `/users/me` | any | `{firstName, lastName, phone?, avatarUrl?}` | 200 UserProfileResponse | 400 |
| PUT | `/users/me/password` | any | `{currentPassword, newPassword(min8)}` | 204 | 400 |
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
| POST | `/trips` | DRIVER | R08: needs verified license+car | 201 TripResponse | 400 403 404 |
| GET | `/trips` | any | Query: `origin`, `destination`, `date(yyyy-MM-dd)` + pagination. AVAILABLE+seats only | 200 Page\<TripResponse\> | — |
| GET | `/trips/{id}` | any | — | 200 TripResponse | 404 |
| PUT | `/trips/{id}` | DRIVER (owner) | R07: if CONFIRMED reservations, only `originLabel` editable | 200 TripResponse | 400 403 404 |
| DELETE | `/trips/{id}` | DRIVER (owner) | R06: cascade-cancels reservations | 204 | 400 403 404 |
| GET | `/trips/me` | DRIVER | Driver's own trips, paginated | 200 Page\<TripResponse\> | — |
| GET | `/trips/map` | any | AVAILABLE+seats only, default size 100 | 200 Page\<TripMapResponse\> | — |
| GET | `/trips/map/me` | DRIVER | All driver's trips all statuses | 200 Page\<TripMapResponse\> | — |

---

## RESERVATIONS

**ReservationResponse:** `{id, tripId, tripOriginLabel, tripDestinationLabel, tripDepartureAt, tripPricePerSeat, passengerId, passengerFirstName, passengerLastName, seatsBooked, status, createdAt}`

| Method | Path | Auth | Body/Notes | Returns | Errors |
|--------|------|------|-----------|---------|--------|
| POST | `/reservations` | PASSENGER | `{tripId, seatsBooked(min1)}` R01 R02 | 201 ReservationResponse | 400 403 404 |
| DELETE | `/reservations/{id}` | PASSENGER (owner) | R03: blocked <2h departure | 204 | 400 403 404 |
| GET | `/reservations/me` | PASSENGER | Paginated | 200 Page\<ReservationResponse\> | — |
| GET | `/trips/{tripId}/reservations` | DRIVER (trip owner) | Paginated | 200 Page\<ReservationResponse\> | 403 404 |

---

## REVIEWS

**ReviewResponse:** `{id, reservationId, authorId, authorFirstName, authorLastName, targetId, targetFirstName, targetLastName, direction, rating, comment, createdAt}`

| Method | Path | Auth | Body | Returns | Errors |
|--------|------|------|------|---------|--------|
| POST | `/reservations/{reservationId}/review` | party to reservation | `{rating(1-5), comment?(max1000)}` Direction auto-inferred from role. R04 R05 | 201 ReviewResponse | 400 403 404 |

---

## DOCUMENTS (ROLE_PASSENGER or ROLE_DRIVER)

**DocumentResponse:** `{id, type, mimeType, status, rejectionReason, carId, uploadedAt, reviewedAt}`

**Upload rules:**
- `LICENSE` — allowed for `ROLE_PASSENGER` and `ROLE_DRIVER` (driver-promotion flow)
- `CAR_REGISTRATION` — `ROLE_DRIVER` only; `carId` required

| Method | Path | Body/Notes | Returns | Errors |
|--------|------|-----------|---------|--------|
| POST | `/documents` | `multipart/form-data`: `file`(JPEG/PNG/PDF ≤5MB), `type`, `carId`(if CAR_REGISTRATION). Magic-byte detection. | 201 DocumentResponse | 400 403 404 |
| GET | `/users/me/documents` | — | 200 DocumentResponse[] | — |
| GET | `/users/me/documents/{id}/file` | — | 200 binary (Content-Type set) | 403 404 |

---

## ADMIN (ROLE_ADMIN — all `/admin/**`)

**AdminUserResponse:** `{id, email, firstName, lastName, phone, avatarUrl, avgRating, isActive, createdAt, role, licenseVerified?, licenseNumber?, permissions?}`

### Users
| Method | Path | Body | Returns | Errors |
|--------|------|------|---------|--------|
| GET | `/admin/users` | pagination | 200 Page\<AdminUserResponse\> | — |
| GET | `/admin/users/{id}` | — | 200 AdminUserResponse | 404 |
| PUT | `/admin/users/{id}/role` | `{role: PASSENGER\|DRIVER\|ADMIN}` Blocked if user has dependent trips/reservations | 200 AdminUserResponse | 400 404 |
| PUT | `/admin/users/{id}/status` | `{active: bool}` R11: suspension revokes tokens | 200 AdminUserResponse | 404 |
| DELETE | `/admin/users/{id}` | — Soft-delete, R11 | 204 | 404 |

### Trips
| Method | Path | Notes | Returns | Errors |
|--------|------|-------|---------|--------|
| GET | `/admin/trips` | All statuses, paginated | 200 Page\<TripResponse\> | — |
| DELETE | `/admin/trips/{id}` | R06 cascade-cancel | 204 | 400 404 |
| GET | `/admin/trips/map` | All statuses, default size 200 | 200 Page\<TripMapResponse\> | — |

### Reservations
| Method | Path | Returns |
|--------|------|---------|
| GET | `/admin/reservations` | 200 Page\<ReservationResponse\> (all statuses) |

### Reviews
| Method | Path | Body | Returns | Errors |
|--------|------|------|---------|--------|
| PUT | `/admin/reviews/{id}` | `{rating?(1-5), comment?}` null fields keep existing value | 200 ReviewResponse | 404 |
| DELETE | `/admin/reviews/{id}` | — Permanent | 204 | 404 |

### Documents
| Method | Path | Notes | Returns | Errors |
|--------|------|-------|---------|--------|
| GET | `/admin/documents` | Query: `status?(PENDING\|APPROVED\|REJECTED)` + pagination. Response includes `driverId/FirstName/LastName` | 200 Page\<DocumentResponse\> | 400 |
| PUT | `/admin/documents/{id}/review` | `{status:APPROVED\|REJECTED, rejectionReason?(required if REJECTED)}` APPROVED LICENSE (PASSENGER uploader) → promotes to DRIVER + `licenseVerified=true` (R08); APPROVED LICENSE (DRIVER uploader) → `licenseVerified=true`; APPROVED CAR_REGISTRATION → `registrationVerified=true` | 200 DocumentResponse | 400 404 |
| POST | `/admin/documents/{id}/notify` | Re-notify driver (no body) | 200 | 404 |

### Statistics
| Method | Path | Returns |
|--------|------|---------|
| GET | `/admin/stats` | `{totalUsers, totalDrivers, totalPassengers, totalAdmins, totalTrips, totalAvailableTrips, totalCompletedTrips, totalReservations, totalConfirmedReservations, totalReviews, totalPendingDocuments}` |
