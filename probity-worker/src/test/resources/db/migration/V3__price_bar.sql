CREATE TABLE price_bars
(
    id         UUID PRIMARY KEY,

    ticker     VARCHAR(20) NOT NULL,
    bar_date   DATE        NOT NULL,

    open       NUMERIC(19, 4),
    high       NUMERIC(19, 4),
    low        NUMERIC(19, 4),
    close      NUMERIC(19, 4),
    adj_close  NUMERIC(19, 4),

    volume     BIGINT,

    -- Audit
    created_at TIMESTAMP   NOT NULL,
    updated_at TIMESTAMP,

    -- Soft-delete
    deleted    BOOLEAN     NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,

    -- Optimistic locking
    version    BIGINT      NOT NULL DEFAULT 0,

    -- One bar per ticker per day
    CONSTRAINT uq_price_bars_symbol_date UNIQUE (ticker, bar_date)
);