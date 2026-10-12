-- =============================================================================
-- V15__add_3m_asset.sql
-- Add 3M Company (MMM) to assets
-- =============================================================================

INSERT INTO assets (id, ticker, name, type, sector, active, created_at, updated_at, version)
VALUES (gen_random_uuid(), 'MMM', '3M Company', 'STOCK', 'CONSUMER', true, NOW(), NOW(), 0)
ON CONFLICT (ticker) DO NOTHING;
