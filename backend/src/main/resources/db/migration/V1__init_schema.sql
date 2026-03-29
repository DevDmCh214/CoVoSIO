-- Migration V1 : full schema — flat identity + capability profiles model
-- Author : CoVoSIO
-- Date   : 2026-03-28

-- Core identity
CREATE TABLE users (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    phone         VARCHAR(20),
    avatar_url    VARCHAR(500),
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_users_email ON users(email);

-- Passenger capability — created at registration
CREATE TABLE passenger_profiles (
    user_id          UUID         PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    avg_rating       DECIMAL(3,2) NOT NULL DEFAULT 0.00,
    total_trips_done INTEGER      NOT NULL DEFAULT 0,
    last_search_at   TIMESTAMP
);

-- Admin staff — fully separate, own auth
CREATE TABLE admins (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    permissions   TEXT,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    last_login_at TIMESTAMP
);
CREATE INDEX idx_admins_email ON admins(email);

-- Driver application — temp table, one active per user
CREATE TABLE driver_applications (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    rejection_reason TEXT,
    reviewed_by      UUID        REFERENCES admins(id) ON DELETE SET NULL,
    reviewed_at      TIMESTAMP,
    applied_at       TIMESTAMP   NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_driver_applications_user_id ON driver_applications(user_id);
CREATE INDEX idx_driver_applications_status  ON driver_applications(status);

-- Driver capability — created when application APPROVED
CREATE TABLE driver_profiles (
    user_id            UUID         PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    license_number     VARCHAR(50),
    avg_rating         DECIMAL(3,2) NOT NULL DEFAULT 0.00,
    total_trips_driven INTEGER      NOT NULL DEFAULT 0,
    acceptance_rate    DECIMAL(5,2) NOT NULL DEFAULT 0.00
);

-- Refresh tokens
CREATE TABLE refresh_tokens (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token      VARCHAR(512) NOT NULL UNIQUE,
    expires_at TIMESTAMP   NOT NULL,
    revoked    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP   NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_refresh_tokens_token   ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);

-- Cars — belong to a driver_profile
CREATE TABLE cars (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id             UUID         NOT NULL REFERENCES driver_profiles(user_id) ON DELETE CASCADE,
    brand                 VARCHAR(100) NOT NULL,
    model                 VARCHAR(100) NOT NULL,
    color                 VARCHAR(50)  NOT NULL,
    plate                 VARCHAR(20)  NOT NULL UNIQUE,
    total_seats           INTEGER      NOT NULL,
    registration_verified BOOLEAN      NOT NULL DEFAULT FALSE,
    is_active             BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at            TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_cars_driver_id ON cars(driver_id);

-- Driver documents — LICENSE linked to application; CAR_REGISTRATION linked to car
CREATE TABLE driver_documents (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id   UUID         REFERENCES driver_applications(id) ON DELETE CASCADE,
    car_id           UUID         REFERENCES cars(id) ON DELETE SET NULL,
    type             VARCHAR(30)  NOT NULL,
    file_path        VARCHAR(500) NOT NULL,
    mime_type        VARCHAR(100) NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    rejection_reason TEXT,
    reviewed_by      UUID         REFERENCES admins(id) ON DELETE SET NULL,
    reviewed_at      TIMESTAMP,
    uploaded_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_driver_documents_application_id ON driver_documents(application_id);
CREATE INDEX idx_driver_documents_car_id         ON driver_documents(car_id);
CREATE INDEX idx_driver_documents_status         ON driver_documents(status);

-- Trips
CREATE TABLE trips (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id         UUID         NOT NULL REFERENCES driver_profiles(user_id) ON DELETE CASCADE,
    car_id            UUID         REFERENCES cars(id) ON DELETE SET NULL,
    origin_label      VARCHAR(255) NOT NULL,
    origin_lat        DECIMAL(9,6) NOT NULL,
    origin_lng        DECIMAL(9,6) NOT NULL,
    destination_label VARCHAR(255) NOT NULL,
    dest_lat          DECIMAL(9,6) NOT NULL,
    dest_lng          DECIMAL(9,6) NOT NULL,
    departure_at      TIMESTAMP    NOT NULL,
    seats_available   INTEGER      NOT NULL,
    price_per_seat    DECIMAL(8,2) NOT NULL,
    pets_allowed      BOOLEAN      NOT NULL DEFAULT FALSE,
    smoking_allowed   BOOLEAN      NOT NULL DEFAULT FALSE,
    status            VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE',
    created_at        TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_trips_driver_id    ON trips(driver_id);
CREATE INDEX idx_trips_status       ON trips(status);
CREATE INDEX idx_trips_departure_at ON trips(departure_at);

-- Reservations — passenger_id references users.id (all users have passenger_profiles)
CREATE TABLE reservations (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id      UUID        NOT NULL REFERENCES trips(id)    ON DELETE CASCADE,
    passenger_id UUID        NOT NULL REFERENCES users(id)    ON DELETE CASCADE,
    seats_booked INTEGER     NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMP   NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_reservations_trip_id      ON reservations(trip_id);
CREATE INDEX idx_reservations_passenger_id ON reservations(passenger_id);
CREATE INDEX idx_reservations_status       ON reservations(status);

-- Reviews
CREATE TABLE reviews (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    reservation_id UUID        NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    author_id      UUID        NOT NULL REFERENCES users(id)        ON DELETE CASCADE,
    target_id      UUID        NOT NULL REFERENCES users(id)        ON DELETE CASCADE,
    direction      VARCHAR(30) NOT NULL,
    rating         INTEGER     NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment        TEXT,
    created_at     TIMESTAMP   NOT NULL DEFAULT NOW(),
    UNIQUE(reservation_id, direction)
);
CREATE INDEX idx_reviews_reservation_id ON reviews(reservation_id);
CREATE INDEX idx_reviews_target_id      ON reviews(target_id);

-- Platform stats view
CREATE OR REPLACE VIEW v_platform_stats AS
SELECT
    (SELECT COUNT(*) FROM users)                                          AS total_users,
    (SELECT COUNT(*) FROM driver_profiles)                                AS total_drivers,
    (SELECT COUNT(*) FROM passenger_profiles)                             AS total_passengers,
    (SELECT COUNT(*) FROM admins)                                         AS total_admins,
    (SELECT COUNT(*) FROM trips)                                          AS total_trips,
    (SELECT COUNT(*) FROM trips  WHERE status = 'AVAILABLE')              AS total_available_trips,
    (SELECT COUNT(*) FROM trips  WHERE status = 'COMPLETED')              AS total_completed_trips,
    (SELECT COUNT(*) FROM reservations)                                   AS total_reservations,
    (SELECT COUNT(*) FROM reservations WHERE status = 'CONFIRMED')        AS total_confirmed_reservations,
    (SELECT COUNT(*) FROM reviews)                                        AS total_reviews,
    (SELECT COUNT(*) FROM driver_applications WHERE status = 'PENDING')   AS total_pending_applications,
    (SELECT COUNT(*) FROM driver_documents WHERE status = 'PENDING' AND car_id IS NOT NULL) AS total_pending_car_docs;
