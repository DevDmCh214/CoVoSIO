-- Migration V2 : seed data for development/testing
-- Author : CoVoSIO
-- Date   : 2026-03-28
--
-- Fixed UUID prefix: 00000000-0000-0000-0000-0000000000XX
-- Users: 01–05 | Admin: 00 | Driver profiles: 01 | Cars: 10
-- Trips: 20–23 | Reservations: 30–33 | Reviews: 40–41
-- Applications: 60–61 | Documents: 50–53
--
-- All passwords: 'password' (BCrypt)
-- BCrypt hash for 'password': $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LPVdPdDkNa2

-- Admin (separate table)
INSERT INTO admins (id, email, password_hash, first_name, last_name, permissions, is_active)
VALUES (
    '00000000-0000-0000-0000-000000000000',
    'admin@covosio.fr',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LPVdPdDkNa2',
    'Admin', 'CoVoSIO', 'ALL', TRUE
);

-- Platform users
INSERT INTO users (id, email, password_hash, first_name, last_name, is_active) VALUES
('00000000-0000-0000-0000-000000000001', 'driver1@covosio.fr',    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LPVdPdDkNa2', 'Alice',   'Martin',   TRUE),
('00000000-0000-0000-0000-000000000002', 'driver2@covosio.fr',    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LPVdPdDkNa2', 'Bob',     'Dupont',   TRUE),
('00000000-0000-0000-0000-000000000003', 'passenger1@covosio.fr', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LPVdPdDkNa2', 'Chloe',   'Bernard',  TRUE),
('00000000-0000-0000-0000-000000000004', 'passenger2@covosio.fr', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LPVdPdDkNa2', 'David',   'Petit',    TRUE),
('00000000-0000-0000-0000-000000000005', 'suspended@covosio.fr',  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LPVdPdDkNa2', 'Eve',     'Leblanc',  FALSE);

-- All users get passenger_profiles
INSERT INTO passenger_profiles (user_id, avg_rating, total_trips_done) VALUES
('00000000-0000-0000-0000-000000000001', 0.00, 0),
('00000000-0000-0000-0000-000000000002', 0.00, 0),
('00000000-0000-0000-0000-000000000003', 4.50, 3),
('00000000-0000-0000-0000-000000000004', 0.00, 1),
('00000000-0000-0000-0000-000000000005', 0.00, 0);

-- driver1 has approved application -> driver_profile
INSERT INTO driver_applications (id, user_id, status, reviewed_by, reviewed_at, applied_at) VALUES
('00000000-0000-0000-0000-000000000060',
 '00000000-0000-0000-0000-000000000001',
 'APPROVED',
 '00000000-0000-0000-0000-000000000000',
 NOW() - INTERVAL '10 days',
 NOW() - INTERVAL '11 days');

INSERT INTO driver_profiles (user_id, license_number, avg_rating, total_trips_driven) VALUES
('00000000-0000-0000-0000-000000000001', 'FR-123456', 4.80, 5);

-- driver2 has pending application (no driver_profile yet)
INSERT INTO driver_applications (id, user_id, status, applied_at) VALUES
('00000000-0000-0000-0000-000000000061',
 '00000000-0000-0000-0000-000000000002',
 'PENDING',
 NOW() - INTERVAL '2 days');

-- Cars for driver1
INSERT INTO cars (id, driver_id, brand, model, color, plate, total_seats, registration_verified, is_active)
VALUES
('00000000-0000-0000-0000-000000000010',
 '00000000-0000-0000-0000-000000000001',
 'Peugeot', '308', 'Gris', 'AB-123-CD', 5, TRUE, TRUE);

-- Trips (driver1)
INSERT INTO trips (id, driver_id, car_id, origin_label, origin_lat, origin_lng, destination_label, dest_lat, dest_lng, departure_at, seats_available, price_per_seat, status) VALUES
('00000000-0000-0000-0000-000000000020',
 '00000000-0000-0000-0000-000000000001',
 '00000000-0000-0000-0000-000000000010',
 'Paris, France', 48.8566, 2.3522,
 'Lyon, France',  45.7578, 4.8320,
 NOW() + INTERVAL '3 days', 3, 25.00, 'AVAILABLE'),

('00000000-0000-0000-0000-000000000021',
 '00000000-0000-0000-0000-000000000001',
 '00000000-0000-0000-0000-000000000010',
 'Lyon, France',  45.7578, 4.8320,
 'Marseille, France', 43.2965, 5.3698,
 NOW() + INTERVAL '7 days', 4, 20.00, 'AVAILABLE'),

('00000000-0000-0000-0000-000000000022',
 '00000000-0000-0000-0000-000000000001',
 '00000000-0000-0000-0000-000000000010',
 'Paris, France', 48.8566, 2.3522,
 'Bordeaux, France', 44.8378, -0.5792,
 NOW() - INTERVAL '5 days', 0, 30.00, 'COMPLETED'),

('00000000-0000-0000-0000-000000000023',
 '00000000-0000-0000-0000-000000000001',
 '00000000-0000-0000-0000-000000000010',
 'Nantes, France', 47.2184, -1.5536,
 'Paris, France', 48.8566, 2.3522,
 NOW() - INTERVAL '2 days', 2, 15.00, 'CANCELLED');

-- Reservations
INSERT INTO reservations (id, trip_id, passenger_id, seats_booked, status) VALUES
('00000000-0000-0000-0000-000000000030',
 '00000000-0000-0000-0000-000000000020',
 '00000000-0000-0000-0000-000000000003',
 1, 'CONFIRMED'),

('00000000-0000-0000-0000-000000000031',
 '00000000-0000-0000-0000-000000000020',
 '00000000-0000-0000-0000-000000000004',
 1, 'PENDING'),

('00000000-0000-0000-0000-000000000032',
 '00000000-0000-0000-0000-000000000022',
 '00000000-0000-0000-0000-000000000003',
 2, 'CONFIRMED'),

('00000000-0000-0000-0000-000000000033',
 '00000000-0000-0000-0000-000000000022',
 '00000000-0000-0000-0000-000000000004',
 1, 'CONFIRMED');

-- Reviews (on completed trip 22)
INSERT INTO reviews (id, reservation_id, author_id, target_id, direction, rating, comment) VALUES
('00000000-0000-0000-0000-000000000040',
 '00000000-0000-0000-0000-000000000032',
 '00000000-0000-0000-0000-000000000003',
 '00000000-0000-0000-0000-000000000001',
 'PASSENGER_TO_DRIVER', 5, 'Great driver!'),

('00000000-0000-0000-0000-000000000041',
 '00000000-0000-0000-0000-000000000032',
 '00000000-0000-0000-0000-000000000001',
 '00000000-0000-0000-0000-000000000003',
 'DRIVER_TO_PASSENGER', 4, 'Punctual passenger.');
