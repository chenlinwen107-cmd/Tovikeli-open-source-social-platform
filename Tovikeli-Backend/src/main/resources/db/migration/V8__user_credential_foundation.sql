-- V8: User credential foundation for Productization P1.
--
-- V1-V7 are frozen. This migration is additive only.
-- Existing users may have NULL password_hash until they complete
-- the production registration/authentication flow.

ALTER TABLE platform_user
    ADD COLUMN password_hash TEXT;
