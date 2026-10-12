CREATE TYPE sector_type AS ENUM (
    'TECHNOLOGY',
    'FINANCIAL',
    'HEALTHCARE',
    'CRYPTO',
    'COMMODITIES',
    'ENERGY',
    'CONSUMER',
    'UNKNOWN'
    );

ALTER TABLE assets
    ADD COLUMN sector sector_type NOT NULL DEFAULT 'UNKNOWN';