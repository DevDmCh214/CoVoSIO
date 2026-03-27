# CLAUDE.md — CoVoSIO Project Rules

This file defines the conventions, constraints, and requirements to be respected
in the entire CoVoSIO project.

---

## 1. Project Identity

- **Name**: CoVoSIO
- **Type**: Full-stack ridesharing web application
- **Stack**: Java 21 + Spring Boot 3 / React + Vite + TailwindCSS / PostgreSQL 16
- **Repo**: Monorepo with `backend/`, `frontend/`, `docs/`, `backups/` folders

---

## 2. Code Rules — Backend (Java / Spring Boot)

### 2.1 Layered Architecture — Always Respected

```
Controller → Service → Repository → Entity
```

- **Controllers** contain no business logic. They receive the HTTP request, call the service,
  return the response DTO.
- **Services** contain all business logic and rule verification (permissions, constraints,
  data consistency).
- **Repositories** contain only data queries (Spring Data derived methods or `@Query` JPQL/native).
- **Entities** contain no business logic. No calculations, no calls to other classes in entities.

### 2.2 DTOs Mandatory

- Never expose a JPA entity directly in a controller.
- Each endpoint returns a dedicated DTO (`TripDto`, `ReservationDto`, etc.).
- Request DTOs (`TripRequest`) and response DTOs (`TripResponse`) are in separate classes
  if their fields differ.

### 2.3 Error Handling

- Always use a `GlobalExceptionHandler` class with `@RestControllerAdvice`.
- Business exceptions inherit from `BusinessException` (runtime, explicit message).
- 404 exceptions use `ResourceNotFoundException`.
- 403 exceptions use `AccessDeniedException` from Spring Security.
- Never return stack traces in HTTP responses in production.
- Standardized error response format:
  ```json
  {
    "status": 400,
    "error": "BUSINESS_RULE_VIOLATION",
    "message": "You cannot book your own trip",
    "timestamp": "2025-06-01T10:30:00"
  }
  ```

### 2.4 Security — Spring Security

- Each new endpoint must have its rule in `SecurityConfig` in the **same commit**
  as the endpoint. Never leave an endpoint unsecured.
- Use `@PreAuthorize("hasRole('ROLE')")` on controller methods for fine-grained rules.
- Always verify in the service that the user is the owner of the resource they are modifying:
  ```java
  if (!resource.getOwner().getId().equals(currentUserId))
      throw new AccessDeniedException("Action not authorized");
  ```
- Never call `findAll()` in a controller for end users. Always filter by `userId` for personal data.

### 2.5 Transactions

- Any service method that performs multiple write operations receives `@Transactional`.
- Read-only methods can use `@Transactional(readOnly = true)` to optimize performance.

### 2.6 Pagination

- All endpoints returning a list use `Page<T>` and accept `Pageable` as parameter.
- Default values: `page=0`, `size=10`, `sort=createdAt,desc`.
- Never return a non-paginated list on a public endpoint.

### 2.7 Business Rules — Verified in Service, Never Frontend-Only

| Code | Rule |
|------|------|
| R01 | A passenger cannot book their own trip |
| R02 | Booking blocked if `seats_available < seats_requested` (transaction) |
| R04 | Review possible only after trip `COMPLETED` |
| R05 | One review per reservation, per direction |
| R06 | Cancelling a trip cancels all its reservations in cascade |
| R07 | Editing a trip with reservations: only meeting point is editable |
| R08 | Publishing a trip requires `license_verified = true` AND car verified |
| R09 | Deleting a car blocked if future `AVAILABLE` trip is attached |
| R10 | Refresh token revocable in database |
| R11 | Account suspension invalidates all its refresh tokens |

### 2.8 Code Documentation

- All public methods of **services** have complete Javadoc: `@param`, `@return`, `@throws` documented.
- Controllers have SpringDoc/OpenAPI annotations: `@Operation`, `@ApiResponse`, `@Parameter`.
- Entities have comments on non-obvious fields.
- Controllers, services, and repositories have **no** redundant comments (no `// get user by id` before `getUserById()`).

---

## 3. Code Rules — Database

### 3.1 Flyway Migrations — Absolute Rule

- **Never** modify an existing migration after it has been executed once.
- Always create a new numbered migration for any change.
- Naming: `V{N}__{description_in_lowercase}.sql`
  ```
  V1__init_users.sql
  V2__init_trips.sql
  V3__init_reservations.sql
  V4__init_documents.sql
  V5__add_favorites.sql       ← new feature
  ```
- Each migration contains a header comment:
  ```sql
  -- Migration V5 : add favorites table (UC-P08)
  -- Author : [name]
  -- Date   : 2025-06-01
  ```

### 3.2 SQL Conventions

- Table names in `snake_case` plural: `users`, `driver_documents`.
- Column names in `snake_case`: `created_at`, `is_active`.
- Every table has a UUID PK: `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`.
- Every table has `created_at TIMESTAMP DEFAULT NOW()`.
- Foreign keys are named after the referenced table: `driver_id`, `trip_id`.
- Foreign keys always have `ON DELETE CASCADE` or `ON DELETE SET NULL` — never without explicit clause.
- Junction tables have a `UNIQUE(col_a, col_b)` constraint.

### 3.3 Inheritance Strategy — TPT (Table-Per-Type)

- User inheritance is implemented as **Joined Table** JPA.
- Parent table: `users` with discriminator `dtype`.
- Subtables: `passengers`, `drivers`, `admins` (PK = FK to `users.id`).
- Never change this strategy without complete migration.

### 3.4 Database Components

- The project maintains a **SQL view** `v_platform_stats` for the admin dashboard.
- Any new complex aggregation repeated should become a view or named query in the repository.
- Scripts `backups/backup.sh` and `backups/restore.sh` are maintained and functional.

### 3.5 Data Security

- Uploaded files (driver documents) are stored in `uploads/documents/{driver_id}/` outside the public folder.
- Files are renamed with UUID — never the original name.
- Allowed types: `image/jpeg`, `image/png`, `application/pdf`.
- Max size: 5 MB. Magic signature verification mandatory.
- Files are never served via direct URL — only via secured endpoint with JWT + role verification.

---

## 4. Code Rules — Frontend (React / Vite)

### 4.1 API Calls

- All HTTP calls go through wrappers in `src/api/`.
- The JWT interceptor automatically adds the `Authorization` header.
- The interceptor handles automatic token refresh before expiration.
- Never make `fetch()` calls directly in a component.

### 4.2 Frontend Security

- User role is read from the decoded JWT (never from localStorage directly without validation).
- Protected routes use a `PrivateRoute` component that verifies the role before displaying the page.
- The Leaflet map filters displayed data by role:
  - Passenger: `AVAILABLE` trips with free seats only
  - Driver: their trips + `AVAILABLE` trips from others
  - Admin: all trips, all statuses

### 4.3 Map (Leaflet + OpenStreetMap)

- OpenStreetMap tiles only (no Google Maps, no API key).
- Geocoding via Nominatim only.
- Respect Nominatim usage terms: one request at a time, User-Agent identifying the project,
  no bulk geocoding.

---

## 5. Git Rules — Mandatory Conventions

### 5.1 Branch Structure

```
main          ← stable code, always deployable
└── develop   ← integration branch
    ├── feature/{feature-name}
    ├── fix/{bug-name}
    └── docs/{doc-name}
```

- Never commit directly to `main`.
- Features go through `develop` before `main`.

### 5.2 Commit Convention (Conventional Commits)

Mandatory format:
```
<type>(<scope>): <description in lowercase>
```

Allowed types:
```
feat      → new feature
fix       → bug fix
docs      → documentation only
test      → test addition or modification
refactor  → refactoring without behavior change
style     → formatting, spaces (no logic)
chore     → maintenance tasks (deps, config)
migration → new Flyway migration
```

Correct examples:
```
feat(reservation): add seat limit validation per passenger
fix(auth): refresh token not revoked on logout
docs(api): add OpenAPI annotations to TripController
test(service): add unit tests for ReservationService
migration(db): add favorites table V5
refactor(dto): extract common fields to BaseResponseDto
```

### 5.3 What Must Never Be Committed

- `.env` files containing secrets (passwords, JWT secret)
- `uploads/` folder with user files
- `target/` or `node_modules/` folder
- IDE config files (`.idea/`, `.vscode/` except exceptions)
- Backup `.sql` files (except example file)
- Logs (`*.log`)

A `.env.example` file tracked in version control documents all necessary variables without their values:
```env
# CoVoSIO — environment variables (copy to .env and fill in)
POSTGRES_DB=covosio_db
POSTGRES_USER=covosio
POSTGRES_PASSWORD=
JWT_SECRET=
JWT_EXPIRATION_MS=900000
JWT_REFRESH_EXPIRATION_MS=604800000
UPLOAD_DIR=./uploads
```

---

## 6. Test Rules

### 6.1 Three Mandatory Levels

**Unit Tests** (`src/test/java/com/covosio/unit/`) :
- Test service business logic in isolation.
- Use Mockito to mock repositories.
- Each service has its dedicated test class.
- Cover nominal cases AND error cases (business exceptions).

**Integration Tests** (`src/test/java/com/covosio/integration/`) :
- Test complete endpoints with `@SpringBootTest` + `MockMvc`.
- Use in-memory H2 database (`application-test.yml`).
- Verify HTTP status, JSON format, and security rules.

**Non-Regression Tests** (`src/test/java/com/covosio/regression/`) :
- Verify that critical existing endpoints still work after each feature addition.
- Run systematically before any merge to `develop`.

### 6.2 Minimum Test Cases per Service

For each service method, test:
- The nominal case (valid data → success)
- Resource not found (→ `ResourceNotFoundException`)
- Access not authorized (→ `AccessDeniedException`)
- Business rule violation (→ `BusinessException`)

### 6.3 Test Naming

Format: `methodName_shouldExpectedBehavior_whenCondition`
```java
createReservation_shouldThrowBusinessException_whenPassengerReservesOwnTrip()
createReservation_shouldDecrementSeats_whenReservationIsValid()
cancelTrip_shouldCancelAllReservations_whenTripIsActive()
```

---

## 7. Documentation Rules

### 7.1 Mandatory Files in `docs/`

```
docs/
├── TECHNICAL_DOCUMENTATION.md       ← for developers
├── USER_GUIDE.md                    ← for end users
├── uml/
│   ├── class_diagram.png            ← JPA class diagram
│   ├── sequence_reservation.png     ← sequence UC-P03
│   └── sequence_auth.png            ← sequence UC-C02
└── api/
    └── openapi.json                 ← generated by SpringDoc
```

### 7.2 CHANGELOG.md

Maintained at project root. Updated with each feature or fix:
```markdown
## [1.2.0] - 2025-06-15
### Added
- UC-P08 : favorites system (favorites table, POST/GET/DELETE endpoints)
- UC-A15 : re-notification of pending driver

## [1.1.0] - 2025-06-01
### Added
- UC-D11 : upload driver verification documents
### Fixed
- R03 : cancellation correctly blocked 2h before departure
```

---

## 8. Checklist Before Each Commit

- [ ] Code compiles without error or warning
- [ ] Unit tests pass (`mvn test`)
- [ ] Integration tests pass
- [ ] New endpoint has its rule in `SecurityConfig`
- [ ] New table has its numbered Flyway migration
- [ ] DTOs updated if entity changed
- [ ] Javadoc present on service public methods
- [ ] No secrets in committed files
- [ ] `CHANGELOG.md` updated
- [ ] An existing endpoint tested manually (non-regression)

---

## 9. Development State

> Keep this section up to date after each phase. It lets Claude resume work
> without re-reading all commits from scratch.

### 9.1 Environment

| Tool | Path |
|------|------|
| JDK 21 | `C:\Program Files\Java\jdk-21.0.10` |
| Maven 3.9.14 | `C:\Program Files\apache-maven-3.9.14` |

Run tests:
```bash
JAVA_HOME="C:\Program Files\Java\jdk-21.0.10" \
  "C:\Program Files\apache-maven-3.9.14\bin\mvn" test
```

Current result: **40 tests — 0 failures** (as of 2026-03-27).

### 9.2 Completed Phases

#### Phase 1 — Authentication foundation (UC-C01–C04) ✅
Branch `feature/auth-core` → merged into `develop`.

| What | Files |
|------|-------|
| Entities (TPT) | `User`, `Passenger`, `Driver`, `Admin`, `RefreshToken` |
| Repositories | `UserRepository`, `RefreshTokenRepository` |
| Security | `JwtUtil`, `JwtFilter`, `UserDetailsServiceImpl`, `SecurityConfig` |
| Service | `AuthService` |
| Controller | `AuthController` — `POST /auth/register`, `/login`, `/refresh`, `/logout` |
| DTOs | `RegisterRequest`, `LoginRequest`, `RefreshTokenRequest`, `AuthResponse` |
| Tests | `AuthServiceTest` (11 unit), `AuthControllerTest` (9 integration) |
| Migration | `V1__init_users.sql` — tables `users`, `passengers`, `drivers`, `admins`, `refresh_tokens` |

#### Phase 2 — User profile (UC-C05–C08) ✅
Branch `feature/user-profile` → merged into `develop`.

| What | Files |
|------|-------|
| Service | `UserService` |
| Controller | `UserController` — `GET /users/me`, `PUT /users/me`, `PUT /users/me/password`, `GET /users/{id}` |
| DTOs | `UserProfileResponse`, `PublicUserResponse`, `UpdateProfileRequest`, `ChangePasswordRequest` |
| Tests | `UserServiceTest` (9 unit) |
| Fix | `/auth/logout` moved to `permitAll` in `SecurityConfig` (access token may be expired at logout) |

#### Phase 3 — Car management (UC-D01, UC-D01b) ✅
Branch `feature/cars` → merged into `develop`.

| What | Files |
|------|-------|
| Entity | `Car` |
| Repository | `CarRepository` — `findByDriver_IdAndIsActiveTrue`, `countFutureAvailableTripsForCar` (R09 native query) |
| Service | `CarService` — `addCar`, `deleteCar` (R09), `getMyCars` |
| Controller | `CarController` — `POST /cars`, `DELETE /cars/{id}`, `GET /cars/me` |
| DTOs | `CarRequest`, `CarResponse` |
| Tests | `CarServiceTest` (11 unit) |
| Migration | `V2__init_cars.sql` — table `cars` |
| Security | `/cars`, `/cars/**` → `ROLE_DRIVER` added to `SecurityConfig` |
| Note | R09 enforced via native query; soft-delete pattern (`is_active = false`) |

### 9.3 Next Phases (not yet started)

Suggested order — adjust to project priorities:

| Phase | Scope | Key UCs |
|-------|-------|---------|
| 4 | Trip management | Driver publishes/edits/cancels trips (R07, R08) |
| 5 | Reservation | Passenger books/cancels (R01, R02, R06) |
| 6 | Driver verification | Admin approves driver documents (R08, R11) |
| 7 | Reviews | Post-trip review, one per reservation per direction (R04, R05) |
| 8 | Admin dashboard | Stats via `v_platform_stats`, account suspension (R11) |
| 9 | Frontend | React + Vite + TailwindCSS, PrivateRoute, Leaflet map |

### 9.4 Active Branch

```
develop  ← current integration branch (all phases merged here)
main     ← not yet updated (nothing promoted to main yet)
```

### 9.5 Flyway Migration Counter

Last migration: **V2** (`V2__init_cars.sql`).
Next migration to create: **V3**.
