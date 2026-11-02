CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    -- Stored lowercased by the app; unique case-insensitively (index below).
    email VARCHAR(255) NOT NULL,
    -- Only an Argon2id hash, never the password itself.
    password_hash VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX ux_users_email_lower ON users (lower(email));
