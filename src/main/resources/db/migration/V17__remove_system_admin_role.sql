-- PRIZM no longer exposes administrator features or an administrator account bootstrap.
-- Preserve any legacy row and ownership references, but prevent the account from authenticating.
UPDATE users
SET enabled = FALSE,
    role = 'USER',
    updated_at = now()
WHERE role = 'SYSTEM_ADMIN';

ALTER TABLE users
    DROP CONSTRAINT ck_users_role;

ALTER TABLE users
    ADD CONSTRAINT ck_users_role CHECK (role = 'USER');
