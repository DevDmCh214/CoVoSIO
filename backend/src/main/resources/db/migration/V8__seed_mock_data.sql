-- Migration V8 : seed mock data for endpoint testing
-- Author : CoVoSIO
-- Date   : 2026-03-28
--
-- All user passwords = "password"  (BCrypt cost 10)
-- Fixed UUIDs allow cross-referencing between INSERT blocks.
--
-- Users
--   00000001  admin@covosio.fr         ADMIN
--   00000002  driver1@covosio.fr       DRIVER  verified (license + car)
--   00000003  driver2@covosio.fr       DRIVER  unverified
--   00000004  passenger1@covosio.fr    PASSENGER
--   00000005  passenger2@covosio.fr    PASSENGER
--   00000006  suspended@covosio.fr     PASSENGER  is_active=false
--
-- Cars
--   00000010  driver1 — Peugeot 308    registration_verified=true
--   00000011  driver1 — Renault Clio   registration_verified=false
--   00000012  driver2 — Citroën C3     registration_verified=false
--
-- Trips
--   00000020  AVAILABLE  Paris→Lyon         driver1/car1   future
--   00000021  AVAILABLE  Lyon→Marseille     driver1/car1   future  pets_allowed
--   00000022  COMPLETED  Paris→Bordeaux     driver1/car1   past
--   00000023  CANCELLED  Nantes→Rennes      driver1/car1
--
-- Reservations
--   00000030  trip20  passenger1  CONFIRMED  1 seat
--   00000031  trip20  passenger2  PENDING    2 seats
--   00000032  trip22  passenger1  CONFIRMED  1 seat  (completed trip → reviews possible)
--   00000033  trip23  passenger2  CANCELLED  1 seat  (cancelled trip)
--
-- Reviews  (only on completed reservation 00000032)
--   00000040  passenger1 → driver1   PASSENGER_TO_DRIVER  rating 4
--   00000041  driver1    → passenger1  DRIVER_TO_PASSENGER  rating 5
--
-- Documents
--   00000050  driver1  LICENSE          APPROVED  (reviewed by admin)
--   00000051  driver1  CAR_REGISTRATION  car1  APPROVED
--   00000052  driver2  LICENSE          PENDING
--   00000053  driver2  LICENSE          REJECTED  (previous rejected attempt)

-- ─────────────────────────────────────────────────────────────────────────────
-- USERS
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO users (id, dtype, email, password_hash, first_name, last_name, phone, avg_rating, is_active) VALUES
  ('00000000-0000-0000-0000-000000000001', 'Admin',
   'admin@covosio.fr',
   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
   'Alice', 'Admin', '+33600000001', 0.00, true),

  ('00000000-0000-0000-0000-000000000002', 'Driver',
   'driver1@covosio.fr',
   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
   'David', 'Dupont', '+33600000002', 4.00, true),

  ('00000000-0000-0000-0000-000000000003', 'Driver',
   'driver2@covosio.fr',
   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
   'Marc', 'Martin', '+33600000003', 0.00, true),

  ('00000000-0000-0000-0000-000000000004', 'Passenger',
   'passenger1@covosio.fr',
   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
   'Sophie', 'Bernard', '+33600000004', 0.00, true),

  ('00000000-0000-0000-0000-000000000005', 'Passenger',
   'passenger2@covosio.fr',
   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
   'Lucas', 'Petit', '+33600000005', 0.00, true),

  ('00000000-0000-0000-0000-000000000006', 'Passenger',
   'suspended@covosio.fr',
   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
   'Eve', 'Suspendue', '+33600000006', 0.00, false);

INSERT INTO admins (user_id, permissions, last_login_at) VALUES
  ('00000000-0000-0000-0000-000000000001', 'ALL', NOW());

INSERT INTO drivers (user_id, license_number, license_verified, total_trips_driven, acceptance_rate) VALUES
  ('00000000-0000-0000-0000-000000000002', 'FR-123456-AB', true,  3, 100.00),
  ('00000000-0000-0000-0000-000000000003', 'FR-654321-CD', false, 0,   0.00);

INSERT INTO passengers (user_id, total_trips_done) VALUES
  ('00000000-0000-0000-0000-000000000004', 2),
  ('00000000-0000-0000-0000-000000000005', 0),
  ('00000000-0000-0000-0000-000000000006', 0);

-- ─────────────────────────────────────────────────────────────────────────────
-- CARS
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO cars (id, driver_id, brand, model, color, plate, total_seats, registration_verified, is_active) VALUES
  ('00000000-0000-0000-0000-000000000010',
   '00000000-0000-0000-0000-000000000002',
   'Peugeot', '308', 'Gris', 'AB-123-CD', 4, true,  true),

  ('00000000-0000-0000-0000-000000000011',
   '00000000-0000-0000-0000-000000000002',
   'Renault', 'Clio', 'Rouge', 'EF-456-GH', 3, false, true),

  ('00000000-0000-0000-0000-000000000012',
   '00000000-0000-0000-0000-000000000003',
   'Citroën', 'C3', 'Bleu', 'IJ-789-KL', 4, false, true);

-- ─────────────────────────────────────────────────────────────────────────────
-- TRIPS
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO trips (id, driver_id, car_id,
                   origin_label, origin_lat, origin_lng,
                   destination_label, dest_lat, dest_lng,
                   departure_at, seats_available, price_per_seat,
                   pets_allowed, smoking_allowed, status) VALUES

  -- AVAILABLE: Paris → Lyon  (1 confirmed + 2 pending booked out of 4 → 1 left)
  ('00000000-0000-0000-0000-000000000020',
   '00000000-0000-0000-0000-000000000002',
   '00000000-0000-0000-0000-000000000010',
   'Paris, France',    48.856614,  2.352222,
   'Lyon, France',     45.764043,  4.835659,
   '2026-04-05 09:00:00', 1, 12.00, false, false, 'AVAILABLE'),

  -- AVAILABLE: Lyon → Marseille  (no reservations yet, pets allowed)
  ('00000000-0000-0000-0000-000000000021',
   '00000000-0000-0000-0000-000000000002',
   '00000000-0000-0000-0000-000000000010',
   'Lyon, France',      45.764043,  4.835659,
   'Marseille, France', 43.296482,  5.381099,
   '2026-04-12 14:00:00', 3, 15.00, true, false, 'AVAILABLE'),

  -- COMPLETED: Paris → Bordeaux  (has reviews)
  ('00000000-0000-0000-0000-000000000022',
   '00000000-0000-0000-0000-000000000002',
   '00000000-0000-0000-0000-000000000010',
   'Paris, France',     48.856614,  2.352222,
   'Bordeaux, France',  44.837789, -0.579180,
   '2026-03-20 10:00:00', 0, 10.00, false, false, 'COMPLETED'),

  -- CANCELLED: Nantes → Rennes
  ('00000000-0000-0000-0000-000000000023',
   '00000000-0000-0000-0000-000000000002',
   '00000000-0000-0000-0000-000000000010',
   'Nantes, France', 47.218371, -1.553621,
   'Rennes, France', 48.117266, -1.677793,
   '2026-04-10 08:00:00', 2, 8.00, false, true, 'CANCELLED');

-- ─────────────────────────────────────────────────────────────────────────────
-- RESERVATIONS
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO reservations (id, trip_id, passenger_id, seats_booked, status) VALUES

  -- CONFIRMED on AVAILABLE trip
  ('00000000-0000-0000-0000-000000000030',
   '00000000-0000-0000-0000-000000000020',
   '00000000-0000-0000-0000-000000000004',
   1, 'CONFIRMED'),

  -- PENDING on AVAILABLE trip
  ('00000000-0000-0000-0000-000000000031',
   '00000000-0000-0000-0000-000000000020',
   '00000000-0000-0000-0000-000000000005',
   2, 'PENDING'),

  -- CONFIRMED on COMPLETED trip → reviews possible
  ('00000000-0000-0000-0000-000000000032',
   '00000000-0000-0000-0000-000000000022',
   '00000000-0000-0000-0000-000000000004',
   1, 'CONFIRMED'),

  -- CANCELLED on CANCELLED trip
  ('00000000-0000-0000-0000-000000000033',
   '00000000-0000-0000-0000-000000000023',
   '00000000-0000-0000-0000-000000000005',
   1, 'CANCELLED');

-- ─────────────────────────────────────────────────────────────────────────────
-- REVIEWS  (both directions on the completed reservation)
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO reviews (id, reservation_id, author_id, target_id, direction, rating, comment) VALUES

  ('00000000-0000-0000-0000-000000000040',
   '00000000-0000-0000-0000-000000000032',
   '00000000-0000-0000-0000-000000000004',   -- author: passenger1
   '00000000-0000-0000-0000-000000000002',   -- target: driver1
   'PASSENGER_TO_DRIVER', 4,
   'Très bon conducteur, ponctuel et sympathique.'),

  ('00000000-0000-0000-0000-000000000041',
   '00000000-0000-0000-0000-000000000032',
   '00000000-0000-0000-0000-000000000002',   -- author: driver1
   '00000000-0000-0000-0000-000000000004',   -- target: passenger1
   'DRIVER_TO_PASSENGER', 5,
   'Passager agréable, à l''heure. Je recommande.');

-- ─────────────────────────────────────────────────────────────────────────────
-- DOCUMENTS
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO driver_documents (id, driver_id, car_id, type, file_path, mime_type, status, rejection_reason, reviewed_by, reviewed_at) VALUES

  -- driver1 LICENSE — APPROVED
  ('00000000-0000-0000-0000-000000000050',
   '00000000-0000-0000-0000-000000000002',
   NULL,
   'LICENSE',
   'uploads/documents/00000000-0000-0000-0000-000000000002/license_mock-uuid-a.jpg',
   'image/jpeg', 'APPROVED', NULL,
   '00000000-0000-0000-0000-000000000001',
   '2026-03-15 10:00:00'),

  -- driver1 CAR_REGISTRATION car1 — APPROVED
  ('00000000-0000-0000-0000-000000000051',
   '00000000-0000-0000-0000-000000000002',
   '00000000-0000-0000-0000-000000000010',
   'CAR_REGISTRATION',
   'uploads/documents/00000000-0000-0000-0000-000000000002/car_mock-uuid-b.jpg',
   'image/jpeg', 'APPROVED', NULL,
   '00000000-0000-0000-0000-000000000001',
   '2026-03-15 10:05:00'),

  -- driver2 LICENSE — PENDING (waiting for admin review)
  ('00000000-0000-0000-0000-000000000052',
   '00000000-0000-0000-0000-000000000003',
   NULL,
   'LICENSE',
   'uploads/documents/00000000-0000-0000-0000-000000000003/license_mock-uuid-c.jpg',
   'image/jpeg', 'PENDING', NULL, NULL, NULL),

  -- driver2 LICENSE — REJECTED (previous attempt)
  ('00000000-0000-0000-0000-000000000053',
   '00000000-0000-0000-0000-000000000003',
   NULL,
   'LICENSE',
   'uploads/documents/00000000-0000-0000-0000-000000000003/license_mock-uuid-d.jpg',
   'image/jpeg', 'REJECTED',
   'Document illisible, veuillez soumettre une photo plus nette.',
   '00000000-0000-0000-0000-000000000001',
   '2026-03-10 14:30:00');
