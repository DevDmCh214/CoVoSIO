-- Migration V10 : fix seed data — dtype case and password hashes
-- Author : CoVoSIO
-- Date   : 2026-03-28
-- Reason : V8 used mixed-case dtype ('Admin','Driver','Passenger') but
--          JPA @DiscriminatorValue expects 'ADMIN','DRIVER','PASSENGER'.
--          Also replaces the placeholder password_hash with a verified
--          BCrypt(cost=10) hash of the string "password".

-- Fix dtype discriminator values
UPDATE users SET dtype = 'ADMIN'     WHERE dtype = 'Admin';
UPDATE users SET dtype = 'DRIVER'    WHERE dtype = 'Driver';
UPDATE users SET dtype = 'PASSENGER' WHERE dtype = 'Passenger';

-- Replace all seed password hashes with a verified BCrypt hash of "password"
-- Hash generated with Python bcrypt cost=10, accepted by Spring BCryptPasswordEncoder
UPDATE users
SET password_hash = '$2b$10$df7ewRniSk03L.g/tA0z5OODH0zYwfUPdR6qn9YGeBOiUNkeieMRa'
WHERE id IN (
  '00000000-0000-0000-0000-000000000001',
  '00000000-0000-0000-0000-000000000002',
  '00000000-0000-0000-0000-000000000003',
  '00000000-0000-0000-0000-000000000004',
  '00000000-0000-0000-0000-000000000005',
  '00000000-0000-0000-0000-000000000006'
);
