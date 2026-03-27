-- Migration V2 : init cars table (UC-D01, UC-D01b)
-- Author : CoVoSIO
-- Date   : 2026-03-27

CREATE TABLE cars (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    driver_id             UUID         NOT NULL REFERENCES drivers(user_id) ON DELETE CASCADE,
    brand                 VARCHAR(100) NOT NULL,
    model                 VARCHAR(100) NOT NULL,
    color                 VARCHAR(50)  NOT NULL,
    plate                 VARCHAR(20)  NOT NULL UNIQUE,
    total_seats           INTEGER      NOT NULL,
    registration_verified BOOLEAN      DEFAULT FALSE NOT NULL,
    is_active             BOOLEAN      DEFAULT TRUE  NOT NULL,
    created_at            TIMESTAMP    DEFAULT NOW() NOT NULL
);

CREATE INDEX idx_cars_driver_id ON cars(driver_id);
