CREATE UNIQUE INDEX IF NOT EXISTS uq_simulations_id_dispatch_generation_idx
    ON simulations (id, dispatch_generation);

CREATE INDEX IF NOT EXISTS idx_simulation_dlt_simulation_generation
    ON simulation_dlt_outbox (simulation_id, dispatch_generation);
