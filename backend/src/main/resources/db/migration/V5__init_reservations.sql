-- Migration V5 : init reservations table (UC-P03 to UC-P05, R01, R02, R06)
-- Author : CoVoSIO
-- Date   : 2026-03-27

CREATE TABLE reservations (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id      UUID        NOT NULL REFERENCES trips(id)           ON DELETE CASCADE,
    passenger_id UUID        NOT NULL REFERENCES passengers(user_id) ON DELETE CASCADE,
    seats_booked INTEGER     NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMP   DEFAULT NOW() NOT NULL
);

CREATE INDEX idx_reservations_trip_id      ON reservations(trip_id);
CREATE INDEX idx_reservations_passenger_id ON reservations(passenger_id);
CREATE INDEX idx_reservations_status       ON reservations(status);
