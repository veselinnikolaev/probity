ALTER TABLE simulations
    ADD COLUMN IF NOT EXISTS dispatch_generation INTEGER NOT NULL DEFAULT 1;

ALTER TABLE simulation_outbox
    ADD COLUMN IF NOT EXISTS dispatch_generation INTEGER NOT NULL DEFAULT 1;

ALTER TABLE simulation_dlt_outbox
    ADD COLUMN IF NOT EXISTS dispatch_generation INTEGER DEFAULT 1,
    ADD COLUMN IF NOT EXISTS dispatch_generation_raw VARCHAR(64),
    ADD COLUMN IF NOT EXISTS dispatch_generation_source VARCHAR(20);

ALTER TABLE simulations
    ADD CONSTRAINT ck_simulations_dispatch_generation
        CHECK (dispatch_generation >= 1) NOT VALID;

ALTER TABLE simulation_outbox
    ADD CONSTRAINT ck_simulation_outbox_dispatch_generation
        CHECK (dispatch_generation >= 1) NOT VALID;

COMMENT ON COLUMN simulations.dispatch_generation IS
    'Monotonic dispatch intent generation. Starts at 1 and changes only for replay.';

COMMENT ON COLUMN simulation_outbox.dispatch_generation IS
    'Generation carried by the pending dispatch and its Kafka message.';

COMMENT ON COLUMN simulation_dlt_outbox.dispatch_generation IS
    'Parsed dispatch generation, or NULL when the carrier was malformed or absent.';

COMMENT ON COLUMN simulation_dlt_outbox.dispatch_generation_raw IS
    'Raw dispatch-generation carrier, truncated to 64 characters.';

COMMENT ON COLUMN simulation_dlt_outbox.dispatch_generation_source IS
    'HEADER, ENVELOPE, LEGACY_DEFAULT, FUTURE, MALFORMED, ABSENT, or CORRUPT.';
