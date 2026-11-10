ALTER TABLE simulations
    VALIDATE CONSTRAINT ck_simulations_dispatch_generation;

ALTER TABLE simulation_outbox
    VALIDATE CONSTRAINT ck_simulation_outbox_dispatch_generation;

ALTER TABLE simulation_outbox
    VALIDATE CONSTRAINT fk_simulation_outbox_dispatch_generation;

ALTER TABLE simulation_outbox
    VALIDATE CONSTRAINT ck_simulation_outbox_payload_generation;

ANALYZE simulations;
ANALYZE simulation_outbox;
