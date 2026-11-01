-- Passwords are stored only as a BCrypt hash. Rows created before this
-- migration get an empty hash, which never matches, so they can't sign in.
ALTER TABLE users ADD COLUMN password_hash VARCHAR(100) NOT NULL DEFAULT '';
ALTER TABLE users ALTER COLUMN password_hash DROP DEFAULT;

-- Emails are compared case-insensitively. The app stores them lowercased;
-- this index enforces uniqueness either way.
DROP INDEX idx_users_email;
CREATE UNIQUE INDEX ux_users_email_lower ON users (lower(email));
