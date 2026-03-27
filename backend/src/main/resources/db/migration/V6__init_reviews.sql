-- Migration V6 : add reviews table (UC-P06, UC-D09)
-- Author : CoVoSIO
-- Date   : 2026-03-27

CREATE TABLE reviews (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    reservation_id UUID         NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    author_id      UUID         NOT NULL REFERENCES users(id)        ON DELETE CASCADE,
    target_id      UUID         NOT NULL REFERENCES users(id)        ON DELETE CASCADE,
    direction      VARCHAR(30)  NOT NULL,
    rating         SMALLINT     NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment        TEXT,
    created_at     TIMESTAMP    DEFAULT NOW() NOT NULL,
    UNIQUE(reservation_id, direction)
);

CREATE INDEX idx_reviews_reservation_id ON reviews(reservation_id);
CREATE INDEX idx_reviews_target_id      ON reviews(target_id);
