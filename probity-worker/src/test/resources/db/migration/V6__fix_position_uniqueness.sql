ALTER TABLE portfolio_positions
    DROP CONSTRAINT uq_position_portfolio_asset;

CREATE UNIQUE INDEX uq_active_position_portfolio_asset
    ON portfolio_positions (portfolio_id, asset_id)
    WHERE deleted = FALSE;