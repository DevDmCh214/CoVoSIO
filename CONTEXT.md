# CoVoSIO — Project Summary

> Local ridesharing platform — full-stack demo with separate frontend, backend, and database.

---

## Table of Contents

1. [Presentation](#1-presentation)
2. [Technical Stack](#2-technical-stack)
3. [Architecture](#3-architecture)
4. [Database — TPT Inheritance](#4-database--tpt-inheritance)
5. [Actors and Roles](#5-actors-and-roles)
6. [Detailed Use Cases](#6-detailed-use-cases)
7. [Business Rules](#7-business-rules)
8. [Document Verification](#8-document-verification)
9. [Project Structure](#9-project-structure)

---

## 1. Presentation

CoVoSIO is a ridesharing application that allows users to **publish**, **search**, and **book** trips between French cities. It includes an interactive map, a system for verifying drivers by admins, and complete role management via database inheritance.

**Demo Objectives:**
- Complete REST API (CRUD) with Spring Boot
- Relational PostgreSQL database with TPT inheritance
- React frontend with Leaflet/OpenStreetMap map
- Strict separation of frontend / backend / database (Docker Compose)
- JWT authentication with access token + refresh token
- Admin dashboard with user management, trips, and documents

---

## 2. Technical Stack

| Layer | Technology |
|---|---|
| Backend | Java 21 + Spring Boot 3 + Spring Security |
| ORM | Spring Data JPA + Hibernate |
| Database | PostgreSQL 16 |
| Database Migrations | Flyway |
| Frontend | React + Vite + TailwindCSS |
| Map | Leaflet.js + OpenStreetMap (free tiles) |
| Geocoding | Nominatim API (OSM, no API key) |
| Authentication | JWT (access 15 min + refresh 7 days) |
| Containers | Docker Compose (3 services) |
| Build Tool | Maven |

---

## 3. Architecture

```
┌─────────────────────────────────────────────┐
│              Docker Compose                  │
│                                              │
│  ┌──────────────┐    ┌──────────────────┐   │
│  │   Frontend   │    │     Backend      │   │
│  │ React + Vite │───▶│  Spring Boot 3   │   │
│  │   Port 3000  │    │   Port 4000      │   │
│  └──────────────┘    └────────┬─────────┘   │
│                               │             │
│                      ┌────────▼─────────┐   │
│                      │   PostgreSQL 16  │   │
│                      │    Port 5432     │   │
│                      └──────────────────┘   │
└─────────────────────────────────────────────┘
```

### Backend Layers (Spring Boot)

```
src/main/java/com/covosio/
├── config/          → SecurityConfig, CorsConfig, JwtConfig
├── controller/      → UserController, TripController,
│                      ReservationController, ReviewController,
│                      CarController, DocumentController,
│                      AdminController
├── service/         → AuthService, TripService, ReservationService,
│                      ReviewService, CarService, DocumentService,
│                      GeoService, StatsService
├── repository/      → Spring Data JPA (one repo per entity)
├── entity/          → User (abstract), Passenger, Driver, Admin,
│                      Car, Trip, Reservation, Review, DriverDocument
├── dto/             → request/response DTOs per resource
└── security/        → JwtUtil, JwtFilter, UserDetailsServiceImpl
```

### File Storage (Driver Documents)

```
/uploads/documents/{driver_id}/
    license_{uuid}.jpg          ← driver's license
    car_{car_id}_plate_{uuid}.jpg   ← registration / plate
```
Files are **never served directly** — only via secured endpoint with role verification.

---

## 4. Database — TPT Inheritance

**Table-Per-Type (Joined)** strategy via `@Inheritance(strategy = InheritanceType.JOINED)` in JPA.

### Principle

- A common `users` table contains all shared fields + a `dtype` discriminator.
- Each subtype (`passengers`, `drivers`, `admins`) has its own table with only its specific columns.
- The primary key of subtables is also an FK to `users.id` (`ON DELETE CASCADE`).

### Main Tables

```sql
users           → id, dtype, email, password_hash, first_name, last_name,
                  phone, avatar_url, avg_rating, is_active, created_at

passengers      → user_id (FK), total_trips_done, last_search_at

drivers         → user_id (FK), license_number, license_verified,
                  total_trips_driven, acceptance_rate

admins          → user_id (FK), permissions, last_login_at

cars            → id, driver_id (FK), brand, model, color, plate,
                  total_seats, registration_verified, is_active

trips           → id, driver_id (FK), car_id (FK), origin_label,
                  origin_lat, origin_lng, destination_label, dest_lat,
                  dest_lng, departure_at, seats_available,
                  price_per_seat, pets_allowed, smoking_allowed, status

reservations    → id, trip_id (FK), passenger_id (FK), seats_booked,
                  status, created_at

reviews         → id, reservation_id (FK), author_id (FK), target_id (FK),
                  rating, comment, is_moderated, created_at

driver_documents → id, driver_id (FK), car_id (FK nullable), type,
                   file_path, mime_type, status, rejection_reason,
                   reviewed_by (FK), reviewed_at, uploaded_at
```

### Main Relationships

- `Driver` → `Cars` (1 → N)
- `Driver` → `Trips` (1 → N)
- `Car` → `Trips` (1 → N)
- `Passenger` ↔ `Trips` via `Reservation` (N ↔ N)
- `Reservation` → `Review` (0 → 1)
- `Driver` → `DriverDocuments` (1 → N)

### Design Decisions

| Decision | Reason |
|---|---|
| TPT Strategy (Joined) | Own specific columns, no unnecessary nulls |
| `dtype` Discriminator in `users` | Filter by role without JOIN |
| UUID as PK | Better for exposed REST API |
| Soft delete (`is_active = false`) | Preserves trip and review history |
| Anonymization on deletion | Referential integrity preserved |

---

## 5. Actors and Roles

| Role | Description |
|---|---|
| **Passenger** | Searches and books trips, leaves driver reviews |
| **Driver** | Publishes and manages trips, manages cars, verified by admin |
| **Admin** | Supervises everything: users, trips, documents, statistics |

A driver can also book trips from other drivers (inherits passenger capabilities). The role is stored in `dtype` and managed via Spring Security roles.

---

## 6. Detailed Use Cases

### Common — All Authenticated Users

| Code | Use Case | Method | Endpoint |
|---|---|---|---|
| UC-C01 | Register | POST | `/auth/register` |
| UC-C02 | Log in | POST | `/auth/login` |
| UC-C03 | Refresh token | POST | `/auth/refresh` |
| UC-C04 | Log out | POST | `/auth/logout` |
| UC-C05 | View own profile | GET | `/users/me` |
| UC-C06 | Edit own profile | PUT | `/users/me` |
| UC-C07 | Change password | PUT | `/users/me/password` |
| UC-C08 | View public profile | GET | `/users/:id` |

**UC-C01 — Register:** Email, password (hashed), first name, last name, phone. User creates a `Passenger` role by default.

**UC-C02 — Log in:** Email + password. Returns access token (15 min) + refresh token (7 days, stored in DB).

**UC-C03 — Refresh token:** Valid only if token exists in DB and user is active. Returns new access token.

**UC-C04 — Log out:** Deletes the refresh token from DB.

**UC-C05 — View own profile:** Email, name, phone, role, avatar, average rating (drivers).

**UC-C06 — Edit own profile:** Can change first/last name, phone, avatar URL.

**UC-C07 — Change password:** Old password verification required. Hashed and stored.

**UC-C08 — View public profile:** First name, rating, trip count (if driver), verification status. No email or phone visible.

### Passenger

| Code | Use Case | Method | Endpoint | Priority |
|---|---|---|---|---|
| UC-P01 | Search trips | GET | `/trips?origin=&destination=&date=` | High |
| UC-P02 | View trip details | GET | `/trips/:id` | High |
| UC-P03 | Book a trip | POST | `/reservations` | High |
| UC-P04 | Cancel reservation | DELETE | `/reservations/:id` | High |
| UC-P05 | View my reservations | GET | `/reservations/me` | High |
| UC-P06 | Leave driver review | POST | `/reservations/:id/review` | Medium |
| UC-P07 | Trip map | GET | `/trips/map` | Medium |

**UC-P01 — Search trips:** Origin + destination (fuzzy match on city names). Optional date filter. Returns paginated `AVAILABLE` trips with at least one free seat.

**UC-P02 — View trip details:** Driver name, car details, origin, destination, departure time, price per seat, seats left, options (pets, smoking), ratings.

**UC-P03 — Book a trip:** Passenger selects number of seats. Creates `Reservation` with `PENDING` status. Driver must accept. Seats reserved immediately (transaction). Cannot be reversed.

**UC-P04 — Cancel reservation:** Cancels only if trip is still `AVAILABLE` or at least 2 hours before departure (see R03). Otherwise blocked. Refund: N/A (demo).

**UC-P05 — View my reservations:** List of bookings with statuses. Sorted by departure date. Links to trip details and driver profile.

**UC-P06 — Leave driver review:** Available only after trip is `COMPLETED`. Rating 1–5 + optional comment. Recalculates driver's `avg_rating`. One review per reservation.

**UC-P07 — Map of trips:** `AVAILABLE` trips with free seats displayed on a France map. Line between origin and destination. Click → summary + book button.

---

### Driver

| Code | Use Case | Method | Endpoint | Priority |
|---|---|---|---|---|
| UC-D01 | Add a car | POST | `/cars` | High |
| UC-D01b | Delete a car | DELETE | `/cars/:id` | High |
| UC-D04 | Publish a trip | POST | `/trips` | High |
| UC-D05 | Edit a trip | PUT | `/trips/:id` | Medium |
| UC-D06 | Cancel a trip | DELETE | `/trips/:id` | High |
| UC-D07 | View my published trips | GET | `/trips/me` | High |
| UC-D08 | View trip passengers | GET | `/trips/:id/reservations` | High |
| UC-D09 | Leave passenger review | POST | `/reservations/:id/review` | Medium |
| UC-D10 | View my trips on map | GET | `/trips/map/me` | Medium |
| UC-D11 | Upload document | POST | `/documents` | High |
| UC-D12 | View document status | GET | `/documents/me` | High |

**UC-D01 — Add a car:** Brand, model, color, license plate, number of seats. Available immediately in the trip creation dropdown.

**UC-D01b — Delete a car:** Blocked if a future trip (`AVAILABLE`) is using this vehicle. Allowed if only past trips or no trips.

**UC-D04 — Publish a trip:** Driver selects car from dropdown (`GET /cars/me`). If no car registered or not verified: creation blocked. Enter origin/destination (geocoded via Nominatim; lat/lng stored). Choose date, time, price, seats, options.

**UC-D05 — Edit a trip:** Free modification if no `CONFIRMED` reservations. If passengers are booked: only the exact meeting point is editable (with automatic notification).

**UC-D06 — Cancel a trip:** Status becomes `CANCELLED`. All associated reservations cancelled in cascade. Passengers notified. Not reversible.

**UC-D08 — View passengers:** First name, rating, seats booked, status. Phone number visible for `CONFIRMED` reservations.

**UC-D09 — Leave passenger review:** Available after trip `COMPLETED`. Rating 1–5 + optional comment. One review per reservation (opposite direction).

**UC-D11 — Upload document:** Photo of driver's license (type `LICENSE`) or photo of registration/plate (type `CAR_REGISTRATION`, linked to car). File stored privately, status `PENDING`. Validation: JPEG/PNG/PDF, max 5 MB, magic signature verified, renamed with UUID.

---

### Admin

| Code | Use Case | Method | Endpoint | Priority |
|---|---|---|---|---|
| UC-A01 | List all users | GET | `/admin/users` | High |
| UC-A02 | View full profile | GET | `/admin/users/:id` | High |
| UC-A03 | Change role | PUT | `/admin/users/:id/role` | Medium |
| UC-A04 | Suspend account | PUT | `/admin/users/:id/status` | High |
| UC-A05 | Reactivate account | PUT | `/admin/users/:id/status` | High |
| UC-A06 | Delete account | DELETE | `/admin/users/:id` | Medium |
| UC-A07 | List all trips | GET | `/admin/trips` | High |
| UC-A08 | Delete reported trip | DELETE | `/admin/trips/:id` | Medium |
| UC-A09 | List all reservations | GET | `/admin/reservations` | Medium |
| UC-A10 | Moderate review | PUT/DELETE | `/admin/reviews/:id` | Low |
| UC-A11 | Stats dashboard | GET | `/admin/stats` | Medium |
| UC-A12 | Global admin map | GET | `/admin/trips/map` | Medium |
| UC-A13 | List pending documents | GET | `/admin/documents?status=PENDING` | High |
| UC-A14 | Review document | PUT | `/admin/documents/:id/review` | High |
| UC-A15 | Re-notify driver | POST | `/admin/documents/:id/notify` | Low |

**UC-A04 — Suspend account:** `is_active = false`. Refresh tokens invalidated. Driver's future trips cancelled automatically. Passengers notified.

**UC-A06 — Delete account:** Soft delete — email anonymized (`deleted_XXXX@void.local`), personal data erased, trip/review history preserved with anonymous author.

**UC-A13 — Pending documents:** List sorted by upload date. Indication of wait time. Priority to drivers with trips already pending verification.

**UC-A14 — Review document:** Admin opens file via secured endpoint. Approves (`APPROVED`) or rejects (`REJECTED` + reason). Updates `driver.license_verified` or `car.registration_verified` depending on type.

**UC-A11 — Stats dashboard:** User count by role, trips published/30 days, booking rate, average platform rating, top 5 origin cities.

---

## 7. Business Rules

These constraints are verified on the **backend in the service layer**, not only on the frontend.

| # | Rule | Use Case |
|---|---|---|
| R01 | A passenger cannot book their own trip | UC-P03 |
| R02 | Reservation blocked if `seats_available < seats_requested` (transaction) | UC-P03 |
| R03 | Cancellation blocked less than 2 hours before departure | UC-P05 |
| R04 | Review only possible after trip is `COMPLETED` | UC-P06, UC-D09 |
| R05 | One review per reservation, per direction (passenger→driver and reverse) | UC-P06, UC-D09 |
| R06 | Cancelling a trip cancels all its reservations in cascade | UC-D06 |
| R07 | Editing a trip with reservations: only meeting point is editable | UC-D05 |
| R08 | Publishing a trip requires `license_verified = true` AND car verified | UC-D04 |
| R09 | Deleting a car blocked if future `AVAILABLE` trip is attached | UC-D01b |
| R10 | Refresh token must be stored and revocable in database | UC-C03, UC-C04 |
| R11 | Account suspension invalidates all its refresh tokens | UC-A04 |

---

## 8. Document Verification

### Workflow

```
Driver uploads document
        │
        ▼
  status = PENDING
        │
        ▼
Admin sees PENDING list
Admin opens file (secured endpoint)
        │
   ┌────┴────┐
   ▼         ▼
APPROVED   REJECTED (+ reason)
   │         │
   ▼         ▼
license_verified   Notification
= true             driver
                   → can re-submit
```

### Upload Security

- Allowed types: `image/jpeg`, `image/png`, `application/pdf`
- Max size: 5 MB
- Magic signature validation (not just extension)
- Systematic renaming with UUID (never original name in path)
- Storage outside server's public folder
- File access only via secured endpoint with JWT verification + role

### File Endpoints

```
POST   /documents                        → upload (driver)
GET    /users/me/documents               → list own documents
GET    /users/me/documents/:id/file      → download own file
GET    /admin/documents?status=PENDING   → admin list
GET    /admin/documents/:id/file         → view file (admin)
PUT    /admin/documents/:id/review       → approve or reject
```

---

## 9. Project Structure

```
covosio/
├── docker-compose.yml
├── README.md
│
├── frontend/                        ← React + Vite + TailwindCSS
│   ├── src/
│   │   ├── pages/
│   │   │   ├── Home.jsx             → home page + search
│   │   │   ├── Search.jsx           → results + passenger map
│   │   │   ├── TripDetail.jsx       → trip detail + booking
│   │   │   ├── MyReservations.jsx   → passenger area
│   │   │   ├── MyTrips.jsx          → driver area
│   │   │   ├── PublishTrip.jsx      → form + car dropdown
│   │   │   ├── MyCars.jsx           → car management
│   │   │   ├── MyDocuments.jsx      → upload + verification status
│   │   │   ├── Profile.jsx          → profile + reviews
│   │   │   └── admin/
│   │   │       ├── Dashboard.jsx    → stats + global map
│   │   │       ├── Users.jsx        → user management
│   │   │       ├── Trips.jsx        → trip management
│   │   │       └── Documents.jsx    → document verification
│   │   ├── components/
│   │   │   ├── MapView.jsx          → Leaflet map (role-aware)
│   │   │   ├── TripCard.jsx
│   │   │   ├── CarDropdown.jsx
│   │   │   └── DocumentUpload.jsx
│   │   └── api/                     → fetch wrappers + JWT interceptor
│
└── backend/                         ← Spring Boot 3 + Java 21
    └── src/main/
        ├── java/com/covosio/
        │   ├── config/
        │   ├── controller/
        │   ├── service/
        │   ├── repository/
        │   ├── entity/
        │   ├── dto/
        │   └── security/
        └── resources/
            ├── application.yml
            └── db/migration/
                ├── V1__init_users.sql
                ├── V2__init_trips.sql
                ├── V3__init_reservations.sql
                └── V4__init_documents.sql
```

---
