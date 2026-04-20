-- Soft-delete support for users.
-- Session/invitation history is preserved via existing FKs (no CASCADE),
-- so hard-deletes fail. Instead, mark users as deleted_at and filter them
-- out everywhere at the ORM layer (@SQLDelete + @SQLRestriction).

ALTER TABLE users ADD COLUMN deleted_at TIMESTAMP NULL;

-- Replace the plain UNIQUE(email) constraint with a partial unique index
-- so that a soft-deleted user's email can be reused by a new registration.
ALTER TABLE users DROP CONSTRAINT users_email_key;

CREATE UNIQUE INDEX users_email_unique_active
    ON users (email)
    WHERE deleted_at IS NULL;