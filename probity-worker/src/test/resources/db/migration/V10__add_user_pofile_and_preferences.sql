-- Add profile fields to users table
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS first_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS last_name  VARCHAR(100);

-- User preferences (one-to-one with users)
CREATE TABLE IF NOT EXISTS user_preferences
(
    id                       UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    user_id                  UUID        NOT NULL UNIQUE REFERENCES users (id) ON DELETE CASCADE,
    default_currency         VARCHAR(10) NOT NULL DEFAULT 'usd',
    default_confidence_level INTEGER     NOT NULL DEFAULT 95
        CHECK (default_confidence_level IN (90, 95, 99)),
    default_time_horizon     VARCHAR(10) NOT NULL DEFAULT '1d'
        CHECK (default_time_horizon IN ('1d', '1w', '1m', '1y')),
    version                  BIGINT      NOT NULL DEFAULT 0,
    created_at               TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at               TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_user_preferences_user_id ON user_preferences (user_id);