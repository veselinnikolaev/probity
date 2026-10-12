CREATE TYPE user_status AS ENUM (
    'PENDING_VERIFICATION',
    'ACTIVE'
    );

ALTER TABLE users
    ADD COLUMN status user_status NOT NULL DEFAULT 'PENDING_VERIFICATION',
    ALTER COLUMN username SET NOT NULL,
    ADD CONSTRAINT uq_users_username UNIQUE (username);