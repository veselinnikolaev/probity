DO $$
BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'probity_worker') THEN
        GRANT SELECT ON simulation_outbox TO probity_worker;
        GRANT UPDATE (claim_token, claimed_until, published_at, error_count)
            ON simulation_outbox TO probity_worker;
        GRANT INSERT (dispatch_generation, dispatch_generation_raw, dispatch_generation_source)
            ON simulation_dlt_outbox TO probity_worker;
    END IF;

    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'probity_api') THEN
        GRANT SELECT, INSERT, UPDATE (dispatch_generation, payload)
            ON simulation_outbox TO probity_api;
    END IF;
END $$;
