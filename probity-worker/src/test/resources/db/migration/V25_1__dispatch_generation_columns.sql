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
