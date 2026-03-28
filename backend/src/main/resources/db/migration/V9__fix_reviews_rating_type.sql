-- Migration V9 : fix reviews.rating column type SMALLINT → INTEGER
-- Author : CoVoSIO
-- Date   : 2026-03-28
-- Reason : Hibernate entity maps Integer to Types#INTEGER; V6 used SMALLINT
--          which passes H2 validation but fails PostgreSQL schema-validate.

ALTER TABLE reviews ALTER COLUMN rating TYPE INTEGER;
