ALTER TABLE portfolio_positions
    ADD COLUMN avg_buy_price NUMERIC(19, 4) NOT NULL DEFAULT 0;