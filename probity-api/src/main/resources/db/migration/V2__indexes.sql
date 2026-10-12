-- =============================================================================
-- Indexes
-- =============================================================================

-- users: email lookup during authentication
CREATE INDEX idx_users_email ON users (email);

-- portfolios: all portfolios owned by a user
CREATE INDEX idx_portfolios_user_id ON portfolios (user_id);

-- positions: all positions in a given portfolio (most common query path)
CREATE INDEX idx_positions_portfolio ON portfolio_positions (portfolio_id);

-- positions: all positions holding a given asset (risk/correlation queries)
CREATE INDEX idx_positions_asset ON portfolio_positions (asset_id);

-- assets: fast ticker lookup from market data ingestion
CREATE INDEX idx_assets_ticker ON assets (ticker);

-- =============================================================================
-- Partial index: enforce uniqueness only on ACTIVE positions
-- =============================================================================
-- The table-level UNIQUE constraint above is intentionally coarse —
-- it prevents even soft-deleted duplicates. If you want to allow
-- re-opening a closed position (deleted=true) while enforcing uniqueness
-- on active ones only, replace the table constraint with this partial index:
--
-- DROP CONSTRAINT uq_position_portfolio_asset;
-- CREATE UNIQUE INDEX uq_active_position_portfolio_asset
--     ON portfolio_positions (portfolio_id, asset_id)
--     WHERE deleted = FALSE;
--
-- This is the recommended approach for production. Commented out here to keep
-- the V1 migration simple; enable in V2 if re-opening positions is a feature.