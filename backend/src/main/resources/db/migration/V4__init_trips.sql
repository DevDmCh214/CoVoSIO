-- Migration V4 : init trips table (UC-D04 to UC-D07, UC-P01, UC-P02, UC-P07, UC-D10)
-- Author : CoVoSIO
-- Date   : 2026-03-27

CREATE TABLE trips (
    id                UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id         UUID           NOT NULL REFERENCES drivers(user_id) ON DELETE CASCADE,
    car_id            UUID                       REFERENCES cars(id)      ON DELETE SET NULL,
    origin_label      VARCHAR(255)   NOT NULL,
    origin_lat        DECIMAL(9,6)   NOT NULL,
    origin_lng        DECIMAL(9,6)   NOT NULL,
    destination_label VARCHAR(255)   NOT NULL,
    dest_lat          DECIMAL(9,6)   NOT NULL,
    dest_lng          DECIMAL(9,6)   NOT NULL,
    departure_at      TIMESTAMP      NOT NULL,
    seats_available   INTEGER        NOT NULL,
    price_per_seat    DECIMAL(8,2)   NOT NULL,
    pets_allowed      BOOLEAN        DEFAULT FALSE NOT NULL,
    smoking_allowed   BOOLEAN        DEFAULT FALSE NOT NULL,
    status            VARCHAR(20)    DEFAULT 'AVAILABLE' NOT NULL,
    created_at        TIMESTAMP      DEFAULT NOW() NOT NULL
);

CREATE INDEX idx_trips_driver_id    ON trips(driver_id);
CREATE INDEX idx_trips_status       ON trips(status);
CREATE INDEX idx_trips_departure_at ON trips(departure_at);
