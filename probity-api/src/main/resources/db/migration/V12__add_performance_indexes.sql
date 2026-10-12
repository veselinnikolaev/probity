-- Performance optimization indexes for user lookups and soft-deletes
-- These composite indexes optimize queries that filter by user_id and deleted status

-- Index for portfolio queries by user with soft-delete support
CREATE INDEX idx_portfolio_user_deleted ON portfolios(user_id, deleted);

-- Index for portfolio position queries by portfolio with soft-delete support
CREATE INDEX idx_positions_portfolio_deleted ON portfolio_positions(portfolio_id, deleted);
