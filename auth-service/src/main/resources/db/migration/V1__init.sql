-- ============================================================
--  Auth Service — Initial Schema
--  V1__init.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS users (
    id          UUID                     PRIMARY KEY,
    email       VARCHAR(255)             NOT NULL UNIQUE,
    password    VARCHAR(255)             NOT NULL,
    first_name  VARCHAR(100),
    last_name   VARCHAR(100),
    role        VARCHAR(50)              NOT NULL,
    is_active   BOOLEAN                  NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users (email);

-- ============================================================

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id          UUID                     PRIMARY KEY,
    token       VARCHAR(255)             NOT NULL UNIQUE,
    user_id     UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expires_at  TIMESTAMP WITH TIME ZONE,
    is_revoked  BOOLEAN                  NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token   ON refresh_tokens (token);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens (user_id);
