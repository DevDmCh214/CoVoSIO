-- Migration V7 : create v_platform_stats view for admin dashboard (UC-A11)
-- Author : CoVoSIO
-- Date   : 2026-03-27

CREATE VIEW v_platform_stats AS
SELECT
    (SELECT COUNT(*) FROM users)                                        AS total_users,
    (SELECT COUNT(*) FROM users WHERE dtype = 'DRIVER')                AS total_drivers,
    (SELECT COUNT(*) FROM users WHERE dtype = 'PASSENGER')             AS total_passengers,
    (SELECT COUNT(*) FROM users WHERE dtype = 'ADMIN')                 AS total_admins,
    (SELECT COUNT(*) FROM trips)                                        AS total_trips,
    (SELECT COUNT(*) FROM trips WHERE status = 'AVAILABLE')            AS total_available_trips,
    (SELECT COUNT(*) FROM trips WHERE status = 'COMPLETED')            AS total_completed_trips,
    (SELECT COUNT(*) FROM reservations)                                 AS total_reservations,
    (SELECT COUNT(*) FROM reservations WHERE status = 'CONFIRMED')     AS total_confirmed_reservations,
    (SELECT COUNT(*) FROM reviews)                                      AS total_reviews,
    (SELECT COUNT(*) FROM driver_documents WHERE status = 'PENDING')   AS total_pending_documents;
