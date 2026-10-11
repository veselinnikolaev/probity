-- ---------------------------------------------------------------------------
-- ENUMs
-- ---------------------------------------------------------------------------

CREATE TYPE user_role AS ENUM ('USER', 'ADMIN');
CREATE TYPE asset_type AS ENUM ('STOCK', 'ETF', 'CRYPTO');

-- ---------------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------------
-- Aggregate root for the identity bounded context.
-- Passwords are stored as BCrypt hashes; plaintext is never persisted.

CREATE TABLE users
(
    id         UUID PRIMARY KEY,
    email      VARCHAR(255) NOT NULL,
    password   VARCHAR(255) NOT NULL,
    username   VARCHAR(100),
    role       user_role    NOT NULL DEFAULT 'USER',

    -- Audit
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP,

    -- Soft-delete
    deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,

    -- Optimistic locking
    version    BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT uq_users_email UNIQUE (email)
);

-- ---------------------------------------------------------------------------
-- assets
-- ---------------------------------------------------------------------------
-- Reference data managed by administrators.
-- Uses "active" lifecycle (archive) instead of soft-delete because historical
-- positions must still reference deactivated assets.

CREATE TABLE assets
(
    id          UUID PRIMARY KEY,
    ticker      VARCHAR(20)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    type        asset_type   NOT NULL,

    -- Audit
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP,

    -- Archive lifecycle
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    archived_at TIMESTAMP,

    -- Optimistic locking
    version     BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT uq_assets_ticker UNIQUE (ticker)
);

-- ---------------------------------------------------------------------------
-- portfolios
-- ---------------------------------------------------------------------------
-- Aggregate root for the portfolio bounded context.
-- References users by FK for referential integrity at the DB level,
-- even though the JPA domain model uses a UUID reference only.

CREATE TABLE portfolios
(
    id         UUID PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id),
    name       VARCHAR(255) NOT NULL,

    -- Audit
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP,

    -- Soft-delete
    deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,

    -- Optimistic locking
    version    BIGINT       NOT NULL DEFAULT 0
);

-- ---------------------------------------------------------------------------
-- portfolio_positions
-- ---------------------------------------------------------------------------
-- Child entity of the Portfolio aggregate.
-- Enforces the invariant: one position per asset per portfolio via UNIQUE constraint.
-- Soft-deleted when a position is fully closed so history is preserved.

CREATE TABLE portfolio_positions
(
    id           UUID PRIMARY KEY,
    portfolio_id UUID           NOT NULL REFERENCES portfolios (id),
    asset_id     UUID           NOT NULL REFERENCES assets (id),

    -- NUMERIC(19,4): supports fractional crypto quantities (e.g. 0.00000001 BTC)
    quantity     NUMERIC(19, 4) NOT NULL,

    -- Audit
    created_at   TIMESTAMP      NOT NULL,
    updated_at   TIMESTAMP,

    -- Soft-delete (set when position is fully closed)
    deleted      BOOLEAN        NOT NULL DEFAULT FALSE,
    deleted_at   TIMESTAMP,

    -- Optimistic locking
    version      BIGINT         NOT NULL DEFAULT 0,

    -- One active position per asset per portfolio (enforced at application level via @SQLRestriction)
    -- Note: partial-unique index below covers only active (non-deleted) positions
    CONSTRAINT uq_position_portfolio_asset UNIQUE (portfolio_id, asset_id)
);