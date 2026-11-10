CREATE TABLE IF NOT EXISTS idempotency_record (
    id                 UUID PRIMARY KEY,
    user_id            UUID         NOT NULL,
    scope_type         VARCHAR(40)  NOT NULL,
    scope_id           UUID         NOT NULL,
    idempotency_key    VARCHAR(255) NOT NULL,
    request_method     VARCHAR(10)  NOT NULL,
    request_path       TEXT         NOT NULL,
    request_hash       CHAR(64)     NOT NULL,
    state              VARCHAR(20)  NOT NULL,
    lease_token        UUID,
    lease_expires_at   TIMESTAMPTZ,
    response_status    INTEGER,
    response_body      TEXT,
    response_truncated BOOLEAN      NOT NULL DEFAULT FALSE,
    response_location  TEXT,
    response_content_type TEXT,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at         TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_idempotency_record
        UNIQUE (user_id, scope_type, scope_id, idempotency_key),
    CONSTRAINT ck_idempotency_state
        CHECK (state IN ('IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT ck_idempotency_lease
        CHECK ((state = 'IN_PROGRESS') = (lease_token IS NOT NULL)),
    CONSTRAINT ck_idempotency_completion
        CHECK (state <> 'COMPLETED' OR response_status IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_idempotency_record_expiry
    ON idempotency_record (expires_at)
    WHERE state <> 'IN_PROGRESS';

DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'probity_api') THEN
        GRANT SELECT, INSERT, UPDATE, DELETE ON idempotency_record TO probity_api;
    END IF;
END $$;
