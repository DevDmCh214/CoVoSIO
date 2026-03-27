-- Migration V1 : init users, passengers, drivers, admins, refresh_tokens tables (UC-C01-C04)
-- Author : CoVoSIO
-- Date   : 2026-03-27

CREATE TABLE users (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    dtype          VARCHAR(20)  NOT NULL,
    email          VARCHAR(255) NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,
    first_name     VARCHAR(100) NOT NULL,
    last_name      VARCHAR(100) NOT NULL,
    phone          VARCHAR(20),
    avatar_url     VARCHAR(500),
    avg_rating     DECIMAL(3,2) DEFAULT 0.0,
    is_active      BOOLEAN      DEFAULT TRUE NOT NULL,
    created_at     TIMESTAMP    DEFAULT NOW() NOT NULL
);

CREATE TABLE passengers (
    user_id          UUID    PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    total_trips_done INTEGER DEFAULT 0,
    last_search_at   TIMESTAMP
);

CREATE TABLE drivers (
    user_id             UUID          PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    license_number      VARCHAR(50),
    license_verified    BOOLEAN       DEFAULT FALSE NOT NULL,
    total_trips_driven  INTEGER       DEFAULT 0,
    acceptance_rate     DECIMAL(5,2)  DEFAULT 0.0
);

CREATE TABLE admins (
    user_id       UUID      PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    permissions   TEXT,
    last_login_at TIMESTAMP
);

CREATE TABLE refresh_tokens (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token      VARCHAR(500) NOT NULL UNIQUE,
    expires_at TIMESTAMP    NOT NULL,
    revoked    BOOLEAN      DEFAULT FALSE NOT NULL,
    created_at TIMESTAMP    DEFAULT NOW() NOT NULL
);

-- Indexes for performance
CREATE INDEX idx_users_email      ON users(email);
CREATE INDEX idx_users_dtype      ON users(dtype);
CREATE INDEX idx_refresh_token    ON refresh_tokens(token);
CREATE INDEX idx_refresh_user_id  ON refresh_tokens(user_id);
