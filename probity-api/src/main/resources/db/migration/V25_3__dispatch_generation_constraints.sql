ALTER TABLE simulations
    ADD CONSTRAINT uq_simulations_id_dispatch_generation
        UNIQUE USING INDEX uq_simulations_id_dispatch_generation_idx;

ALTER TABLE simulation_outbox
    ADD CONSTRAINT fk_simulation_outbox_dispatch_generation
        FOREIGN KEY (simulation_id, dispatch_generation)
        REFERENCES simulations (id, dispatch_generation)
        DEFERRABLE INITIALLY DEFERRED
        NOT VALID;

ALTER TABLE simulation_outbox
    ADD CONSTRAINT ck_simulation_outbox_payload_generation
        CHECK (
            payload->>'dispatchGeneration' IS NULL
            OR (payload->>'dispatchGeneration')::int = dispatch_generation
        )
        NOT VALID;
