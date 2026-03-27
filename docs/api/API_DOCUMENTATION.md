# CoVoSIO — API Reference

**Base URL:** `http://localhost:4000`
**Format:** All requests and responses use `application/json` unless noted otherwise.
**Auth:** JWT Bearer token — include `Authorization: Bearer <accessToken>` on every protected endpoint.

---

## Table of Contents

1. [Authentication](#1-authentication)
2. [User Profile](#2-user-profile)
3. [Cars](#3-cars)
4. [Trips](#4-trips)
5. [Reservations](#5-reservations)
6. [Reviews](#6-reviews)
7. [Documents](#7-documents)
8. [Admin](#8-admin)
9. [Common Concepts](#9-common-concepts)

---

## 1. Authentication

No token required on these endpoints.

---

### POST /auth/register

Creates a **Passenger** account and returns tokens.

**Request body**
```json
{
  "email": "alice@example.com",
  "password": "mypassword",
  "firstName": "Alice",
  "lastName": "Martin",
  "phone": "+33612345678"
}
```

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `email` | string | yes | valid email format |
| `password` | string | yes | min 8 characters |
| `firstName` | string | yes | max 100 chars |
| `lastName` | string | yes | max 100 chars |
| `phone` | string | no | max 20 chars |

**Response `201 Created`**
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "550e8400-e29b-...",
  "tokenType": "Bearer",
  "role": "PASSENGER",
  "email": "alice@example.com",
  "firstName": "Alice",
  "lastName": "Martin"
}
```

| Status | Meaning |
|--------|---------|
| 201 | Account created |
| 400 | Validation error or email already taken |

---

### POST /auth/login

Authenticates with email and password.

**Request body**
```json
{
  "email": "alice@example.com",
  "password": "mypassword"
}
```

**Response `200 OK`** — same shape as `/auth/register`.

| Status | Meaning |
|--------|---------|
| 200 | Login successful |
| 400 | Invalid credentials or suspended account |

---

### POST /auth/refresh

Exchanges a valid refresh token for a new access token.

**Request body**
```json
{
  "refreshToken": "550e8400-e29b-..."
}
```

**Response `200 OK`** — same shape as `/auth/register`.

| Status | Meaning |
|--------|---------|
| 200 | New access token issued |
| 400 | Token invalid, expired, or revoked |

---

### POST /auth/logout

Revokes the refresh token (R10 — token stored and invalidated server-side).

**Request body**
```json
{
  "refreshToken": "550e8400-e29b-..."
}
```

**Response `204 No Content`**

| Status | Meaning |
|--------|---------|
| 204 | Logged out |
| 400 | Refresh token not found |

---

## 2. User Profile

**Auth required:** any authenticated user, unless noted.

---

### GET /users/me

Returns the full profile of the authenticated user.

**Response `200 OK`**
```json
{
  "id": "550e8400-...",
  "email": "alice@example.com",
  "firstName": "Alice",
  "lastName": "Martin",
  "phone": "+33612345678",
  "avatarUrl": "https://...",
  "avgRating": "4.75",
  "isActive": true,
  "createdAt": "2025-06-01T10:00:00",
  "role": "PASSENGER"
}
```

---

### PUT /users/me

Updates the authenticated user's first name, last name, phone, and avatar URL.

**Request body**
```json
{
  "firstName": "Alice",
  "lastName": "Dupont",
  "phone": "+33699999999",
  "avatarUrl": "https://cdn.example.com/avatar.png"
}
```

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `firstName` | string | yes | max 100 chars |
| `lastName` | string | yes | max 100 chars |
| `phone` | string | no | max 20 chars |
| `avatarUrl` | string | no | max 500 chars |

**Response `200 OK`** — updated `UserProfileResponse` (same shape as GET /users/me).

| Status | Meaning |
|--------|---------|
| 200 | Profile updated |
| 400 | Validation error |

---

### PUT /users/me/password

Changes the authenticated user's password.

**Request body**
```json
{
  "currentPassword": "mypassword",
  "newPassword": "newstrongpassword"
}
```

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `currentPassword` | string | yes | — |
| `newPassword` | string | yes | min 8 characters |

**Response `204 No Content`**

| Status | Meaning |
|--------|---------|
| 204 | Password changed |
| 400 | Current password incorrect or validation error |

---

### GET /users/{id}

Returns the public profile of any user (name, rating, role only — no email or phone).

**Path param:** `id` — User UUID

**Response `200 OK`**
```json
{
  "id": "550e8400-...",
  "firstName": "Jean",
  "lastName": "Driver",
  "avatarUrl": null,
  "avgRating": "4.50",
  "role": "DRIVER"
}
```

| Status | Meaning |
|--------|---------|
| 200 | Public profile returned |
| 404 | User not found |

---

## 3. Cars

**Auth required:** `ROLE_DRIVER`

---

### POST /cars

Registers a new car for the authenticated driver.

**Request body**
```json
{
  "brand": "Renault",
  "model": "Clio",
  "color": "Blue",
  "plate": "AB-123-CD",
  "totalSeats": 4
}
```

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `brand` | string | yes | max 100 chars |
| `model` | string | yes | max 100 chars |
| `color` | string | yes | max 50 chars |
| `plate` | string | yes | max 20 chars, unique |
| `totalSeats` | integer | yes | 1–9 |

**Response `201 Created`**
```json
{
  "id": "550e8400-...",
  "brand": "Renault",
  "model": "Clio",
  "color": "Blue",
  "plate": "AB-123-CD",
  "totalSeats": 4,
  "registrationVerified": false,
  "createdAt": "2025-06-01T10:00:00"
}
```

| Status | Meaning |
|--------|---------|
| 201 | Car created |
| 400 | Validation error |
| 403 | Not a driver |

---

### DELETE /cars/{id}

Soft-deletes a car (sets `isActive = false`). Blocked if the car has a future `AVAILABLE` trip (R09).

**Path param:** `id` — Car UUID

**Response `204 No Content`**

| Status | Meaning |
|--------|---------|
| 204 | Car deleted |
| 400 | Future AVAILABLE trip attached (R09) |
| 403 | Not the owner |
| 404 | Car not found |

---

### GET /cars/me

Lists all active cars owned by the authenticated driver.

**Response `200 OK`**
```json
[
  {
    "id": "550e8400-...",
    "brand": "Renault",
    "model": "Clio",
    "color": "Blue",
    "plate": "AB-123-CD",
    "totalSeats": 4,
    "registrationVerified": true,
    "createdAt": "2025-06-01T10:00:00"
  }
]
```

---

## 4. Trips

**Auth required:** any authenticated user for read endpoints; `ROLE_DRIVER` for write endpoints.

---

### POST /trips

Publishes a new trip. Requires `licenseVerified = true` AND car `registrationVerified = true` (R08).

**Auth:** `ROLE_DRIVER`

**Request body**
```json
{
  "carId": "550e8400-...",
  "originLabel": "Paris, France",
  "originLat": 48.856600,
  "originLng": 2.352200,
  "destinationLabel": "Lyon, France",
  "destLat": 45.764000,
  "destLng": 4.835700,
  "departureAt": "2025-07-15T08:30:00",
  "seatsAvailable": 3,
  "pricePerSeat": 15.00,
  "petsAllowed": false,
  "smokingAllowed": false
}
```

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `carId` | UUID | yes | must belong to the driver |
| `originLabel` | string | yes | max 255 chars |
| `originLat` | decimal | yes | |
| `originLng` | decimal | yes | |
| `destinationLabel` | string | yes | max 255 chars |
| `destLat` | decimal | yes | |
| `destLng` | decimal | yes | |
| `departureAt` | ISO-8601 datetime | yes | must be in the future |
| `seatsAvailable` | integer | yes | min 1 |
| `pricePerSeat` | decimal | yes | ≥ 0 |
| `petsAllowed` | boolean | no | default `false` |
| `smokingAllowed` | boolean | no | default `false` |

**Response `201 Created`** — `TripResponse` (see [Trip object](#trip-object)).

| Status | Meaning |
|--------|---------|
| 201 | Trip created |
| 400 | Validation error or R08 violation (unverified license/car) |
| 403 | Not a driver |
| 404 | Car not found |

---

### GET /trips

Searches for `AVAILABLE` trips with free seats. All query parameters are optional.

**Query parameters**

| Param | Type | Description |
|-------|------|-------------|
| `origin` | string | Partial, case-insensitive match on origin city |
| `destination` | string | Partial, case-insensitive match on destination city |
| `date` | date (`yyyy-MM-dd`) | Filters to trips departing on this date |
| `page` | integer | Page index, default `0` |
| `size` | integer | Page size, default `10` |
| `sort` | string | Sort field, default `departureAt` |

**Response `200 OK`** — paginated page of `TripResponse`.

---

### GET /trips/{id}

Returns full details for a single trip.

**Path param:** `id` — Trip UUID

**Response `200 OK`** — `TripResponse` (see [Trip object](#trip-object)).

| Status | Meaning |
|--------|---------|
| 200 | Trip returned |
| 404 | Trip not found |

---

### PUT /trips/{id}

Edits a trip. **R07:** if the trip has `CONFIRMED` reservations, only `originLabel` (meeting point) is editable.

**Auth:** `ROLE_DRIVER` (must be the trip owner)

**Request body** — same shape as `POST /trips`.

**Response `200 OK`** — updated `TripResponse`.

| Status | Meaning |
|--------|---------|
| 200 | Trip updated |
| 400 | Validation error or trip not AVAILABLE |
| 403 | Not the owner |
| 404 | Trip or car not found |

---

### DELETE /trips/{id}

Cancels a trip and cascade-cancels all its reservations (R06).

**Auth:** `ROLE_DRIVER` (must be the trip owner)

**Response `204 No Content`**

| Status | Meaning |
|--------|---------|
| 204 | Trip cancelled |
| 400 | Trip already cancelled |
| 403 | Not the owner |
| 404 | Trip not found |

---

### GET /trips/me

Lists the authenticated driver's own trips, newest departure first.

**Auth:** `ROLE_DRIVER`

**Query parameters:** pagination (`page`, `size`, `sort`)

**Response `200 OK`** — paginated page of `TripResponse`.

---

### GET /trips/map

Returns minimal trip data for Leaflet map markers — `AVAILABLE` trips with free seats only.

**Auth:** any authenticated user

**Query parameters:** pagination (`page`, `size`; default size `100`)

**Response `200 OK`** — paginated page of `TripMapResponse`.

```json
{
  "content": [
    {
      "id": "550e8400-...",
      "originLabel": "Paris, France",
      "originLat": 48.8566,
      "originLng": 2.3522,
      "destinationLabel": "Lyon, France",
      "destLat": 45.764,
      "destLng": 4.8357,
      "seatsAvailable": 3,
      "pricePerSeat": 15.00,
      "departureAt": "2025-07-15T08:30:00",
      "status": "AVAILABLE"
    }
  ]
}
```

---

### GET /trips/map/me

Returns all of the authenticated driver's trips for the driver map view (all statuses).

**Auth:** `ROLE_DRIVER`

**Response `200 OK`** — paginated page of `TripMapResponse`.

---

#### Trip object

Full `TripResponse` shape returned by trip endpoints:

```json
{
  "id": "550e8400-...",
  "driverId": "...",
  "driverFirstName": "Jean",
  "driverLastName": "Driver",
  "driverAvgRating": "4.50",
  "carId": "...",
  "carBrand": "Renault",
  "carModel": "Clio",
  "carColor": "Blue",
  "originLabel": "Paris, France",
  "originLat": 48.8566,
  "originLng": 2.3522,
  "destinationLabel": "Lyon, France",
  "destLat": 45.764,
  "destLng": 4.8357,
  "departureAt": "2025-07-15T08:30:00",
  "seatsAvailable": 3,
  "pricePerSeat": 15.00,
  "petsAllowed": false,
  "smokingAllowed": false,
  "status": "AVAILABLE",
  "createdAt": "2025-06-01T10:00:00"
}
```

`status` values: `AVAILABLE` · `CANCELLED` · `COMPLETED`

---

## 5. Reservations

---

### POST /reservations

Books seats on a trip (R01: no self-booking; R02: atomic seat check).

**Auth:** `ROLE_PASSENGER`

**Request body**
```json
{
  "tripId": "550e8400-...",
  "seatsBooked": 2
}
```

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `tripId` | UUID | yes | must be an AVAILABLE trip |
| `seatsBooked` | integer | yes | min 1 |

**Response `201 Created`**
```json
{
  "id": "...",
  "tripId": "...",
  "tripOriginLabel": "Paris, France",
  "tripDestinationLabel": "Lyon, France",
  "tripDepartureAt": "2025-07-15T08:30:00",
  "tripPricePerSeat": 15.00,
  "passengerId": "...",
  "passengerFirstName": "Alice",
  "passengerLastName": "Martin",
  "seatsBooked": 2,
  "status": "CONFIRMED",
  "createdAt": "2025-06-01T10:00:00"
}
```

`status` values: `PENDING` · `CONFIRMED` · `CANCELLED`

| Status | Meaning |
|--------|---------|
| 201 | Reservation created |
| 400 | R01 (self-booking) or R02 (not enough seats) violation, or trip not AVAILABLE |
| 403 | Not a passenger |
| 404 | Trip not found |

---

### DELETE /reservations/{id}

Cancels a reservation and restores seats. **R03:** blocked within 2 hours of departure.

**Auth:** `ROLE_PASSENGER` (must be the reservation owner)

**Path param:** `id` — Reservation UUID

**Response `204 No Content`**

| Status | Meaning |
|--------|---------|
| 204 | Reservation cancelled |
| 400 | Already cancelled or R03 (2 h window) violation |
| 403 | Not the reservation owner |
| 404 | Reservation not found |

---

### GET /reservations/me

Lists the authenticated passenger's reservations, newest first.

**Auth:** `ROLE_PASSENGER`

**Query parameters:** pagination (`page`, `size`, `sort`)

**Response `200 OK`** — paginated page of `ReservationResponse`.

---

### GET /trips/{tripId}/reservations

Lists all reservations for a trip owned by the authenticated driver.

**Auth:** `ROLE_DRIVER` (must be the trip owner)

**Path param:** `tripId` — Trip UUID

**Query parameters:** pagination (`page`, `size`, `sort`)

**Response `200 OK`** — paginated page of `ReservationResponse`.

| Status | Meaning |
|--------|---------|
| 200 | Reservations returned |
| 403 | Not the trip owner |
| 404 | Trip not found |

---

## 6. Reviews

---

### POST /reservations/{reservationId}/review

Submits a post-trip review. Direction is inferred from the caller's role:
- Passenger → `PASSENGER_TO_DRIVER`
- Driver → `DRIVER_TO_PASSENGER`

**Auth:** any authenticated user (must be a party to the reservation)

**R04:** trip must be `COMPLETED`.
**R05:** one review per reservation per direction.

**Path param:** `reservationId` — Reservation UUID

**Request body**
```json
{
  "rating": 5,
  "comment": "Great driver, very punctual!"
}
```

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `rating` | integer | yes | 1–5 |
| `comment` | string | no | max 1000 chars |

**Response `201 Created`**
```json
{
  "id": "...",
  "reservationId": "...",
  "authorId": "...",
  "authorFirstName": "Alice",
  "authorLastName": "Martin",
  "targetId": "...",
  "targetFirstName": "Jean",
  "targetLastName": "Driver",
  "direction": "PASSENGER_TO_DRIVER",
  "rating": 5,
  "comment": "Great driver, very punctual!",
  "createdAt": "2025-07-16T09:00:00"
}
```

`direction` values: `PASSENGER_TO_DRIVER` · `DRIVER_TO_PASSENGER`

| Status | Meaning |
|--------|---------|
| 201 | Review created |
| 400 | R04 (trip not completed) or R05 (duplicate review) violation |
| 403 | Caller is not a party to the reservation |
| 404 | Reservation not found |

---

## 7. Documents

**Auth required:** `ROLE_DRIVER`

---

### POST /documents

Uploads a driver's license or car registration document.

**Content-Type:** `multipart/form-data`

**Form fields**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `file` | file | yes | JPEG, PNG, or PDF — max 5 MB |
| `type` | string | yes | `LICENSE` or `CAR_REGISTRATION` |
| `carId` | UUID | conditional | Required when `type = CAR_REGISTRATION` |

The file type is detected from its magic signature — the declared content type is ignored.

**Response `201 Created`**
```json
{
  "id": "...",
  "type": "LICENSE",
  "mimeType": "image/jpeg",
  "status": "PENDING",
  "rejectionReason": null,
  "carId": null,
  "uploadedAt": "2025-06-01T10:00:00",
  "reviewedAt": null
}
```

`type` values: `LICENSE` · `CAR_REGISTRATION`
`status` values: `PENDING` · `APPROVED` · `REJECTED`

| Status | Meaning |
|--------|---------|
| 201 | Document uploaded |
| 400 | File too large (> 5 MB), invalid type, or bad magic signature |
| 403 | Not a driver |
| 404 | Car not found (CAR_REGISTRATION) |

---

### GET /users/me/documents

Lists all documents uploaded by the authenticated driver, newest first.

**Response `200 OK`** — array of `DocumentResponse`.

---

### GET /users/me/documents/{id}/file

Downloads the raw file for a document owned by the authenticated driver. Files are never served directly from disk — this endpoint validates ownership before streaming the file.

**Path param:** `id` — Document UUID

**Response `200 OK`** — file binary with the appropriate `Content-Type` header (`image/jpeg`, `image/png`, or `application/pdf`).

| Status | Meaning |
|--------|---------|
| 200 | File returned |
| 403 | Not the document owner |
| 404 | Document not found |

---

## 8. Admin

**Auth required:** `ROLE_ADMIN` on all `/admin/**` endpoints.

---

### Users

#### GET /admin/users

Returns all users paginated.

**Query parameters:** pagination (`page`, `size`, `sort`; default sort `createdAt`)

**Response `200 OK`** — paginated page of `AdminUserResponse`.

```json
{
  "content": [
    {
      "id": "...",
      "email": "driver@example.com",
      "firstName": "Jean",
      "lastName": "Driver",
      "phone": null,
      "avatarUrl": null,
      "avgRating": "4.50",
      "isActive": true,
      "createdAt": "2025-06-01T10:00:00",
      "role": "DRIVER",
      "licenseVerified": true,
      "licenseNumber": "AA-1234",
      "permissions": null
    }
  ],
  "totalElements": 42,
  "totalPages": 5,
  "size": 10,
  "number": 0
}
```

Driver-specific fields (`licenseVerified`, `licenseNumber`) are `null` for non-drivers.
Admin-specific field (`permissions`) is `null` for non-admins.

---

#### GET /admin/users/{id}

Returns full details for a single user.

**Path param:** `id` — User UUID

**Response `200 OK`** — `AdminUserResponse`.

| Status | Meaning |
|--------|---------|
| 200 | User found |
| 404 | User not found |

---

#### PUT /admin/users/{id}/role

Changes a user's role. Restructures the database TPT sub-tables.

> **Constraint:** Cannot change the role of a passenger who has existing reservations, or a driver who has existing trips. Remove or transfer those records first.

**Path param:** `id` — User UUID

**Request body**
```json
{
  "role": "DRIVER"
}
```

`role` values: `PASSENGER` · `DRIVER` · `ADMIN`

**Response `200 OK`** — updated `AdminUserResponse`.

| Status | Meaning |
|--------|---------|
| 200 | Role updated |
| 400 | Invalid role or user has dependent data |
| 404 | User not found |

---

#### PUT /admin/users/{id}/status

Suspends or activates a user account. **R11:** suspension immediately revokes all refresh tokens.

**Path param:** `id` — User UUID

**Request body**
```json
{
  "active": false
}
```

**Response `200 OK`** — updated `AdminUserResponse`.

| Status | Meaning |
|--------|---------|
| 200 | Status updated |
| 404 | User not found |

---

#### DELETE /admin/users/{id}

Soft-deletes a user (`isActive = false`) and revokes all tokens (R11). The record is retained in the database.

**Path param:** `id` — User UUID

**Response `204 No Content`**

| Status | Meaning |
|--------|---------|
| 204 | User soft-deleted |
| 404 | User not found |

---

### Trips

#### GET /admin/trips

Returns all trips regardless of status, paginated.

**Query parameters:** pagination (`page`, `size`, `sort`; default sort `createdAt`)

**Response `200 OK`** — paginated page of `TripResponse`.

---

#### DELETE /admin/trips/{id}

Cancels a trip and cascade-cancels all its reservations (R06).

**Path param:** `id` — Trip UUID

**Response `204 No Content`**

| Status | Meaning |
|--------|---------|
| 204 | Trip cancelled |
| 400 | Trip already cancelled |
| 404 | Trip not found |

---

#### GET /admin/trips/map

Returns minimal trip data for all trips (all statuses) for the admin global map.

**Query parameters:** pagination (`page`, `size`; default size `200`)

**Response `200 OK`** — paginated page of `TripMapResponse`.

---

### Reservations

#### GET /admin/reservations

Returns all reservations regardless of status, paginated.

**Query parameters:** pagination (`page`, `size`, `sort`; default sort `createdAt`)

**Response `200 OK`** — paginated page of `ReservationResponse`.

---

### Reviews

#### PUT /admin/reviews/{id}

Overwrites a review's rating and/or comment. Fields that are `null` in the request keep their existing value.

**Path param:** `id` — Review UUID

**Request body**
```json
{
  "rating": 3,
  "comment": "Updated by moderator"
}
```

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `rating` | integer | no | 1–5 |
| `comment` | string | no | — |

**Response `200 OK`** — updated `ReviewResponse`.

| Status | Meaning |
|--------|---------|
| 200 | Review updated |
| 404 | Review not found |

---

#### DELETE /admin/reviews/{id}

Permanently deletes a review.

**Path param:** `id` — Review UUID

**Response `204 No Content`**

| Status | Meaning |
|--------|---------|
| 204 | Review deleted |
| 404 | Review not found |

---

### Statistics

#### GET /admin/stats

Returns aggregated platform counters.

**Response `200 OK`**
```json
{
  "totalUsers": 1250,
  "totalDrivers": 430,
  "totalPassengers": 810,
  "totalAdmins": 10,
  "totalTrips": 3200,
  "totalAvailableTrips": 980,
  "totalCompletedTrips": 2100,
  "totalReservations": 8400,
  "totalConfirmedReservations": 7200,
  "totalReviews": 5600,
  "totalPendingDocuments": 37
}
```

---

### Documents

#### GET /admin/documents

Returns paginated driver documents with an optional status filter.

**Query parameters**

| Param | Type | Description |
|-------|------|-------------|
| `status` | string | Optional filter: `PENDING`, `APPROVED`, or `REJECTED` |
| `page` | integer | Default `0` |
| `size` | integer | Default `10` |
| `sort` | string | Default `uploadedAt` |

**Response `200 OK`** — paginated page of `DocumentResponse`. In admin context, `driverId`, `driverFirstName`, and `driverLastName` are populated.

```json
{
  "content": [
    {
      "id": "...",
      "type": "LICENSE",
      "mimeType": "image/jpeg",
      "status": "PENDING",
      "rejectionReason": null,
      "carId": null,
      "driverId": "...",
      "driverFirstName": "Jean",
      "driverLastName": "Driver",
      "uploadedAt": "2025-06-01T10:00:00",
      "reviewedAt": null
    }
  ]
}
```

| Status | Meaning |
|--------|---------|
| 200 | Documents returned |
| 400 | Invalid status value |

---

#### PUT /admin/documents/{id}/review

Approves or rejects a driver document.

- **APPROVED + LICENSE** → sets `driver.licenseVerified = true` (R08)
- **APPROVED + CAR_REGISTRATION** → sets `car.registrationVerified = true` (R08)
- **REJECTED** → `rejectionReason` is mandatory

**Path param:** `id` — Document UUID

**Request body**
```json
{
  "status": "REJECTED",
  "rejectionReason": "The document is expired"
}
```

| Field | Type | Required | Constraints |
|-------|------|----------|-------------|
| `status` | string | yes | `APPROVED` or `REJECTED` |
| `rejectionReason` | string | conditional | Required when `status = REJECTED` |

**Response `200 OK`** — updated `DocumentResponse`.

| Status | Meaning |
|--------|---------|
| 200 | Document reviewed |
| 400 | Document already reviewed, missing rejection reason, or PENDING passed as status |
| 404 | Document not found |

---

#### POST /admin/documents/{id}/notify

Sends a re-notification to the driver about a pending document (UC-A15). Validates that the document exists; the actual notification channel is handled externally.

**Path param:** `id` — Document UUID

**Response `200 OK`** (no body)

| Status | Meaning |
|--------|---------|
| 200 | Notification queued |
| 404 | Document not found |

---

## 9. Common Concepts

### Pagination

All paginated endpoints accept the following query parameters and return a standard page envelope:

| Param | Default | Description |
|-------|---------|-------------|
| `page` | `0` | Zero-based page index |
| `size` | `10` | Items per page |
| `sort` | varies | Field name, optionally followed by `,asc` or `,desc` |

**Page envelope**
```json
{
  "content": [ ... ],
  "totalElements": 42,
  "totalPages": 5,
  "size": 10,
  "number": 0,
  "first": true,
  "last": false
}
```

---

### Error response

All errors follow this format:

```json
{
  "status": 400,
  "error": "BUSINESS_RULE_VIOLATION",
  "message": "You cannot book your own trip",
  "timestamp": "2025-06-01T10:30:00"
}
```

| HTTP Status | `error` value | Cause |
|-------------|--------------|-------|
| 400 | `BUSINESS_RULE_VIOLATION` | Business rule violated (R01–R11) |
| 400 | `VALIDATION_ERROR` | Bean validation failed |
| 403 | `ACCESS_DENIED` | Missing role or not the resource owner |
| 404 | `RESOURCE_NOT_FOUND` | Entity not found |
| 500 | `INTERNAL_ERROR` | Unexpected server error |

---

### Business rules reference

| Code | Rule | Enforced by |
|------|------|------------|
| R01 | Passenger cannot book their own trip | `POST /reservations` |
| R02 | Booking blocked if `seatsAvailable < seatsBooked` (atomic) | `POST /reservations` |
| R03 | Cancellation blocked within 2 hours of departure | `DELETE /reservations/{id}` |
| R04 | Review only allowed after trip is `COMPLETED` | `POST /reservations/{id}/review` |
| R05 | One review per reservation per direction | `POST /reservations/{id}/review` |
| R06 | Cancelling a trip cancels all its reservations | `DELETE /trips/{id}`, `DELETE /admin/trips/{id}` |
| R07 | Trip with CONFIRMED reservations: only meeting point editable | `PUT /trips/{id}` |
| R08 | Publishing a trip requires `licenseVerified = true` AND car verified | `POST /trips`, `PUT /admin/documents/{id}/review` |
| R09 | Deleting a car blocked if future AVAILABLE trip uses it | `DELETE /cars/{id}` |
| R10 | Refresh token stored and revocable server-side | `POST /auth/refresh`, `POST /auth/logout` |
| R11 | Account suspension revokes all refresh tokens | `PUT /admin/users/{id}/status`, `DELETE /admin/users/{id}` |

---

### Enum reference

**TripStatus:** `AVAILABLE` · `CANCELLED` · `COMPLETED`

**ReservationStatus:** `PENDING` · `CONFIRMED` · `CANCELLED`

**DocumentType:** `LICENSE` · `CAR_REGISTRATION`

**DocumentStatus:** `PENDING` · `APPROVED` · `REJECTED`

**ReviewDirection:** `PASSENGER_TO_DRIVER` · `DRIVER_TO_PASSENGER`

**User roles (JWT claim):** `ROLE_PASSENGER` · `ROLE_DRIVER` · `ROLE_ADMIN`
