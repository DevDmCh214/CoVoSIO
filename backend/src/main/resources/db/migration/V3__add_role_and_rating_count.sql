-- Migration V3 : add explicit role column to users, rating_count to profile tables, composite indexes
-- Author : CoVoSIO
-- Date   : 2026-03-29

-- 1. Add role column to users (PASSENGER by default)
ALTER TABLE users
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'PASSENGER'
        CHECK (role IN ('PASSENGER', 'DRIVER'));

-- 2. Backfill role for existing drivers
UPDATE users
SET role = 'DRIVER'
WHERE id IN (SELECT user_id FROM driver_profiles);

-- 3. Add rating_count to passenger_profiles
ALTER TABLE passenger_profiles
    ADD COLUMN rating_count INTEGER NOT NULL DEFAULT 0;

-- 4. Backfill passenger rating_count from existing reviews
UPDATE passenger_profiles pp
SET rating_count = (
    SELECT COUNT(*) FROM reviews r
    WHERE r.target_id = pp.user_id
      AND r.direction = 'DRIVER_TO_PASSENGER'
);

-- 5. Add rating_count to driver_profiles
ALTER TABLE driver_profiles
    ADD COLUMN rating_count INTEGER NOT NULL DEFAULT 0;

-- 6. Backfill driver rating_count from existing reviews
UPDATE driver_profiles dp
SET rating_count = (
    SELECT COUNT(*) FROM reviews r
    WHERE r.target_id = dp.user_id
      AND r.direction = 'PASSENGER_TO_DRIVER'
);

-- 7. Composite indexes for admin queries
CREATE INDEX idx_users_role_active_created   ON users(role, is_active, created_at DESC);
CREATE INDEX idx_users_email_role_active     ON users(email, role, is_active);
CREATE INDEX idx_driver_profiles_created     ON driver_profiles(user_id, rating_count);
CREATE INDEX idx_trips_driver_status_dept    ON trips(driver_id, status, departure_at DESC);
CREATE INDEX idx_reservations_pass_status    ON reservations(passenger_id, status, created_at DESC);
