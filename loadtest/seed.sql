-- =============================================================================
-- PR-D0 load-test seed (Phase 4 §2.9 baseline)
-- One verified admin user, one portfolio with AAPL + GOOGL positions, and
-- enough price bars to close the on-read sync window (latest-price-lookback-days
-- is 14; 30 calendar days covers even the weekend clamping in MarketDataService)
-- so the baseline never waits on an outbound Yahoo Finance call.
--
-- Run against the compose postgres AFTER the app has started once (Flyway V7
-- seeds the AAPL/GOOGL assets):
--   Get-Content loadtest/seed.sql -Raw | docker exec -i probity-postgres-1 psql -U postgres -d probity
-- =============================================================================

DO $$
DECLARE
    uid      UUID;
    pid      UUID;
    aapl     UUID;
    googl    UUID;
    v_bar_date DATE;
    i        INT;
BEGIN
    SELECT id INTO uid FROM users WHERE username = 'admin';
    IF uid IS NULL THEN
        uid := gen_random_uuid();
        INSERT INTO users (id, email, password, username, role, status,
                           created_at, updated_at, deleted, version)
        VALUES (uid, 'admin@probity.test',
                '$2a$10$MchuKjuveKDWD8Z7W3yKXOn1oAetwC2T4bBNSQdf0JMHFM9D/1JMa', -- BCrypt("Password123!")
                'admin', 'ADMIN', 'ACTIVE', NOW(), NOW(), FALSE, 0);
    END IF;

    SELECT id INTO pid FROM portfolios WHERE user_id = uid AND name = 'Load Test Portfolio';
    IF pid IS NULL THEN
        pid := gen_random_uuid();
        INSERT INTO portfolios (id, user_id, name, created_at, updated_at, deleted, version)
        VALUES (pid, uid, 'Load Test Portfolio', NOW(), NOW(), FALSE, 0);
    END IF;

    SELECT id INTO aapl FROM assets WHERE ticker = 'AAPL';
    SELECT id INTO googl FROM assets WHERE ticker = 'GOOGL';

    INSERT INTO portfolio_positions (id, portfolio_id, asset_id, quantity, avg_buy_price,
                                     created_at, updated_at, deleted, version)
    VALUES (gen_random_uuid(), pid, aapl, 10, 148.0000, NOW(), NOW(), FALSE, 0)
    ON CONFLICT DO NOTHING;
    INSERT INTO portfolio_positions (id, portfolio_id, asset_id, quantity, avg_buy_price,
                                     created_at, updated_at, deleted, version)
    VALUES (gen_random_uuid(), pid, googl, 5, 130.0000, NOW(), NOW(), FALSE, 0)
    ON CONFLICT DO NOTHING;

    v_bar_date := CURRENT_DATE - 30;
    FOR i IN 0..30 LOOP
        v_bar_date := v_bar_date + 1;
        INSERT INTO price_bars (id, ticker, bar_date, open, high, low, close, adj_close,
                                volume, created_at, updated_at, deleted, version)
        VALUES (gen_random_uuid(), 'AAPL', v_bar_date,
                148.10, 149.50, 147.80, 149.20 + (i % 5) * 0.05, 149.20 + (i % 5) * 0.05,
                55000000, NOW(), NOW(), FALSE, 0)
        ON CONFLICT (ticker, bar_date) DO NOTHING;
        INSERT INTO price_bars (id, ticker, bar_date, open, high, low, close, adj_close,
                                volume, created_at, updated_at, deleted, version)
        VALUES (gen_random_uuid(), 'GOOGL', v_bar_date,
                130.00, 131.40, 129.60, 131.00 + (i % 5) * 0.04, 131.00 + (i % 5) * 0.04,
                22000000, NOW(), NOW(), FALSE, 0)
        ON CONFLICT (ticker, bar_date) DO NOTHING;
    END LOOP;
END $$;