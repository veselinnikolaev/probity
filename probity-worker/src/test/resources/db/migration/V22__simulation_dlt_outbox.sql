-- =============================================================================
-- V22__simulation_dlt_outbox.sql
-- Durable, immutable per-failure DLT event history (M3).
-- =============================================================================

ALTER TABLE simulations
    ADD COLUMN IF NOT EXISTS attempt_id UUID;

COMMENT ON COLUMN simulations.attempt_id
    IS 'Claim attempt that currently owns (or last owned) this row. Written by claimForProcessing; '
       'retained on release; fenced by the DLT recoverer. M4 reset must rotate or clear it.';

CREATE TABLE IF NOT EXISTS simulation_dlt_outbox (
    dlt_event_id UUID NOT NULL,
    simulation_id UUID,
    attempt_id UUID NOT NULL,
    source_topic VARCHAR(249) NOT NULL,
    source_partition INTEGER NOT NULL,
    source_offset BIGINT NOT NULL,
    source_key TEXT,
    payload BYTEA,
    exception_type VARCHAR(255) NOT NULL,
    error_description TEXT,
    failed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    claim_token UUID,
    claimed_until TIMESTAMPTZ,
    publish_attempt_count INTEGER NOT NULL DEFAULT 0,
    last_publish_error TEXT,
    dlt_published_at TIMESTAMPTZ,

    CONSTRAINT pk_simulation_dlt_outbox PRIMARY KEY (dlt_event_id),
    CONSTRAINT uq_simulation_dlt_source_attempt
        UNIQUE (source_topic, source_partition, source_offset, attempt_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_simulation_dlt_attempt
    ON simulation_dlt_outbox (simulation_id, attempt_id)
    WHERE simulation_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_simulation_dlt_outbox_pending
    ON simulation_dlt_outbox (created_at)
    WHERE dlt_published_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_simulation_dlt_outbox_simulation
    ON simulation_dlt_outbox (simulation_id, failed_at DESC);

COMMENT ON TABLE simulation_dlt_outbox
    IS 'Immutable history of terminal simulation failures. One row per failure attempt. '
       'Written atomically with the simulations FAILED transition.';

COMMENT ON COLUMN simulation_dlt_outbox.dlt_event_id
    IS 'Immutable DLT event identity. Never reused or updated.';
COMMENT ON COLUMN simulation_dlt_outbox.attempt_id
    IS 'Claim attempt UUID (listener failure) or deterministic synthetic UUID (deserialisation '
       'or unattributable failure). Part of the idempotency key.';
COMMENT ON COLUMN simulation_dlt_outbox.payload
    IS 'Original record value as bytes. Poison payloads are byte-exact from the '
       'DeserializationException data; listener failures use canonical JSON bytes.';
COMMENT ON COLUMN simulation_dlt_outbox.dlt_published_at
    IS 'Kafka acknowledgement only. Does not mean a downstream consumer processed the event. '
       'NULL is not a universal replay prerequisite.';

DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'probity_worker') THEN
        GRANT SELECT, INSERT ON simulation_dlt_outbox TO probity_worker;
        GRANT UPDATE (claim_token, claimed_until, publish_attempt_count,
                      last_publish_error, dlt_published_at)
            ON simulation_dlt_outbox TO probity_worker;
    END IF;
END $$;
