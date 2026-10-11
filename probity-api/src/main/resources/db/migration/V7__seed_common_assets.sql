-- =============================================================================
-- V?__seed_common_assets.sql
-- Seed a curated list of commonly traded assets.
-- ON CONFLICT DO NOTHING makes this safe to re-run.
-- Sector and type must match your sector_type and asset_type enums exactly.
-- =============================================================================

INSERT INTO assets (id, ticker, name, type, sector, active, created_at, updated_at, version)
VALUES

-- ── US Large Cap Stocks ───────────────────────────────────────────────────────
(gen_random_uuid(), 'AAPL',  'Apple Inc.',                    'STOCK', 'TECHNOLOGY',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'MSFT',  'Microsoft Corporation',         'STOCK', 'TECHNOLOGY',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'GOOGL', 'Alphabet Inc.',                 'STOCK', 'TECHNOLOGY',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'GOOG',  'Alphabet Inc. (Class C)',       'STOCK', 'TECHNOLOGY',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'NVDA',  'NVIDIA Corporation',            'STOCK', 'TECHNOLOGY',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'META',  'Meta Platforms Inc.',           'STOCK', 'TECHNOLOGY',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'AMZN',  'Amazon.com Inc.',               'STOCK', 'CONSUMER',    true, NOW(), NOW(), 0),
(gen_random_uuid(), 'TSLA',  'Tesla Inc.',                    'STOCK', 'CONSUMER',    true, NOW(), NOW(), 0),
(gen_random_uuid(), 'NFLX',  'Netflix Inc.',                  'STOCK', 'TECHNOLOGY',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'ADBE',  'Adobe Inc.',                    'STOCK', 'TECHNOLOGY',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'CRM',   'Salesforce Inc.',               'STOCK', 'TECHNOLOGY',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'ORCL',  'Oracle Corporation',            'STOCK', 'TECHNOLOGY',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'AMD',   'Advanced Micro Devices Inc.',   'STOCK', 'TECHNOLOGY',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'INTC',  'Intel Corporation',             'STOCK', 'TECHNOLOGY',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'QCOM',  'Qualcomm Inc.',                 'STOCK', 'TECHNOLOGY',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'PYPL',  'PayPal Holdings Inc.',          'STOCK', 'FINANCIAL',   true, NOW(), NOW(), 0),
(gen_random_uuid(), 'SQ',    'Block Inc.',                    'STOCK', 'FINANCIAL',   true, NOW(), NOW(), 0),
(gen_random_uuid(), 'SHOP',  'Shopify Inc.',                  'STOCK', 'TECHNOLOGY',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'SPOT',  'Spotify Technology S.A.',       'STOCK', 'TECHNOLOGY',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'UBER',  'Uber Technologies Inc.',        'STOCK', 'TECHNOLOGY',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'LYFT',  'Lyft Inc.',                     'STOCK', 'TECHNOLOGY',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'SNAP',  'Snap Inc.',                     'STOCK', 'TECHNOLOGY',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'PINS',  'Pinterest Inc.',                'STOCK', 'TECHNOLOGY',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'TWLO',  'Twilio Inc.',                   'STOCK', 'TECHNOLOGY',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'ZM',    'Zoom Video Communications',     'STOCK', 'TECHNOLOGY',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'DOCU',  'DocuSign Inc.',                 'STOCK', 'TECHNOLOGY',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'NET',   'Cloudflare Inc.',               'STOCK', 'TECHNOLOGY',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'DDOG',  'Datadog Inc.',                  'STOCK', 'TECHNOLOGY',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'SNOW',  'Snowflake Inc.',                'STOCK', 'TECHNOLOGY',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'PLTR',  'Palantir Technologies Inc.',    'STOCK', 'TECHNOLOGY',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'CRWD',  'CrowdStrike Holdings Inc.',     'STOCK', 'TECHNOLOGY',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'OKTA',  'Okta Inc.',                     'STOCK', 'TECHNOLOGY',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'MDB',   'MongoDB Inc.',                  'STOCK', 'TECHNOLOGY',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'TEAM',  'Atlassian Corporation',         'STOCK', 'TECHNOLOGY',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'NOW',   'ServiceNow Inc.',               'STOCK', 'TECHNOLOGY',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'WDAY',  'Workday Inc.',                  'STOCK', 'TECHNOLOGY',  true, NOW(), NOW(), 0),

-- ── Financials ────────────────────────────────────────────────────────────────
(gen_random_uuid(), 'JPM',   'JPMorgan Chase & Co.',          'STOCK', 'FINANCIAL',   true, NOW(), NOW(), 0),
(gen_random_uuid(), 'BAC',   'Bank of America Corporation',   'STOCK', 'FINANCIAL',   true, NOW(), NOW(), 0),
(gen_random_uuid(), 'WFC',   'Wells Fargo & Company',         'STOCK', 'FINANCIAL',   true, NOW(), NOW(), 0),
(gen_random_uuid(), 'GS',    'Goldman Sachs Group Inc.',      'STOCK', 'FINANCIAL',   true, NOW(), NOW(), 0),
(gen_random_uuid(), 'MS',    'Morgan Stanley',                'STOCK', 'FINANCIAL',   true, NOW(), NOW(), 0),
(gen_random_uuid(), 'C',     'Citigroup Inc.',                'STOCK', 'FINANCIAL',   true, NOW(), NOW(), 0),
(gen_random_uuid(), 'V',     'Visa Inc.',                     'STOCK', 'FINANCIAL',   true, NOW(), NOW(), 0),
(gen_random_uuid(), 'MA',    'Mastercard Incorporated',       'STOCK', 'FINANCIAL',   true, NOW(), NOW(), 0),
(gen_random_uuid(), 'AXP',   'American Express Company',      'STOCK', 'FINANCIAL',   true, NOW(), NOW(), 0),
(gen_random_uuid(), 'BLK',   'BlackRock Inc.',                'STOCK', 'FINANCIAL',   true, NOW(), NOW(), 0),
(gen_random_uuid(), 'SCHW',  'Charles Schwab Corporation',    'STOCK', 'FINANCIAL',   true, NOW(), NOW(), 0),
(gen_random_uuid(), 'COF',   'Capital One Financial Corp.',   'STOCK', 'FINANCIAL',   true, NOW(), NOW(), 0),

-- ── Healthcare ────────────────────────────────────────────────────────────────
(gen_random_uuid(), 'JNJ',   'Johnson & Johnson',             'STOCK', 'HEALTHCARE',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'UNH',   'UnitedHealth Group Inc.',       'STOCK', 'HEALTHCARE',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'PFE',   'Pfizer Inc.',                   'STOCK', 'HEALTHCARE',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'ABBV',  'AbbVie Inc.',                   'STOCK', 'HEALTHCARE',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'MRK',   'Merck & Co. Inc.',              'STOCK', 'HEALTHCARE',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'LLY',   'Eli Lilly and Company',         'STOCK', 'HEALTHCARE',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'AMGN',  'Amgen Inc.',                    'STOCK', 'HEALTHCARE',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'GILD',  'Gilead Sciences Inc.',          'STOCK', 'HEALTHCARE',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'ISRG',  'Intuitive Surgical Inc.',       'STOCK', 'HEALTHCARE',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'MRNA',  'Moderna Inc.',                  'STOCK', 'HEALTHCARE',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'BNTX',  'BioNTech SE',                   'STOCK', 'HEALTHCARE',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'TMO',   'Thermo Fisher Scientific',      'STOCK', 'HEALTHCARE',  true, NOW(), NOW(), 0),

-- ── Energy ────────────────────────────────────────────────────────────────────
(gen_random_uuid(), 'XOM',   'Exxon Mobil Corporation',       'STOCK', 'ENERGY',      true, NOW(), NOW(), 0),
(gen_random_uuid(), 'CVX',   'Chevron Corporation',           'STOCK', 'ENERGY',      true, NOW(), NOW(), 0),
(gen_random_uuid(), 'COP',   'ConocoPhillips',                'STOCK', 'ENERGY',      true, NOW(), NOW(), 0),
(gen_random_uuid(), 'SLB',   'SLB (Schlumberger)',            'STOCK', 'ENERGY',      true, NOW(), NOW(), 0),
(gen_random_uuid(), 'OXY',   'Occidental Petroleum Corp.',    'STOCK', 'ENERGY',      true, NOW(), NOW(), 0),
(gen_random_uuid(), 'BP',    'BP p.l.c.',                     'STOCK', 'ENERGY',      true, NOW(), NOW(), 0),
(gen_random_uuid(), 'SHEL',  'Shell plc',                     'STOCK', 'ENERGY',      true, NOW(), NOW(), 0),

-- ── Consumer ──────────────────────────────────────────────────────────────────
(gen_random_uuid(), 'WMT',   'Walmart Inc.',                  'STOCK', 'CONSUMER',    true, NOW(), NOW(), 0),
(gen_random_uuid(), 'COST',  'Costco Wholesale Corporation',  'STOCK', 'CONSUMER',    true, NOW(), NOW(), 0),
(gen_random_uuid(), 'TGT',   'Target Corporation',            'STOCK', 'CONSUMER',    true, NOW(), NOW(), 0),
(gen_random_uuid(), 'HD',    'The Home Depot Inc.',           'STOCK', 'CONSUMER',    true, NOW(), NOW(), 0),
(gen_random_uuid(), 'LOW',   'Lowe''s Companies Inc.',        'STOCK', 'CONSUMER',    true, NOW(), NOW(), 0),
(gen_random_uuid(), 'NKE',   'NIKE Inc.',                     'STOCK', 'CONSUMER',    true, NOW(), NOW(), 0),
(gen_random_uuid(), 'SBUX',  'Starbucks Corporation',         'STOCK', 'CONSUMER',    true, NOW(), NOW(), 0),
(gen_random_uuid(), 'MCD',   'McDonald''s Corporation',       'STOCK', 'CONSUMER',    true, NOW(), NOW(), 0),
(gen_random_uuid(), 'KO',    'The Coca-Cola Company',         'STOCK', 'CONSUMER',    true, NOW(), NOW(), 0),
(gen_random_uuid(), 'PEP',   'PepsiCo Inc.',                  'STOCK', 'CONSUMER',    true, NOW(), NOW(), 0),
(gen_random_uuid(), 'PG',    'Procter & Gamble Company',      'STOCK', 'CONSUMER',    true, NOW(), NOW(), 0),
(gen_random_uuid(), 'DIS',   'The Walt Disney Company',       'STOCK', 'CONSUMER',    true, NOW(), NOW(), 0),

-- ── ETFs ──────────────────────────────────────────────────────────────────────
(gen_random_uuid(), 'SPY',   'SPDR S&P 500 ETF Trust',        'ETF',   'UNKNOWN',     true, NOW(), NOW(), 0),
(gen_random_uuid(), 'QQQ',   'Invesco QQQ Trust',             'ETF',   'UNKNOWN',     true, NOW(), NOW(), 0),
(gen_random_uuid(), 'IWM',   'iShares Russell 2000 ETF',      'ETF',   'UNKNOWN',     true, NOW(), NOW(), 0),
(gen_random_uuid(), 'VTI',   'Vanguard Total Stock Market',   'ETF',   'UNKNOWN',     true, NOW(), NOW(), 0),
(gen_random_uuid(), 'VOO',   'Vanguard S&P 500 ETF',          'ETF',   'UNKNOWN',     true, NOW(), NOW(), 0),
(gen_random_uuid(), 'VEA',   'Vanguard FTSE Developed Mkts',  'ETF',   'UNKNOWN',     true, NOW(), NOW(), 0),
(gen_random_uuid(), 'VWO',   'Vanguard FTSE Emerging Mkts',   'ETF',   'UNKNOWN',     true, NOW(), NOW(), 0),
(gen_random_uuid(), 'GLD',   'SPDR Gold Shares',              'ETF',   'COMMODITIES', true, NOW(), NOW(), 0),
(gen_random_uuid(), 'SLV',   'iShares Silver Trust',          'ETF',   'COMMODITIES', true, NOW(), NOW(), 0),
(gen_random_uuid(), 'XLK',   'Technology Select Sector SPDR', 'ETF',   'TECHNOLOGY',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'XLF',   'Financial Select Sector SPDR',  'ETF',   'FINANCIAL',   true, NOW(), NOW(), 0),
(gen_random_uuid(), 'XLE',   'Energy Select Sector SPDR',     'ETF',   'ENERGY',      true, NOW(), NOW(), 0),
(gen_random_uuid(), 'XLV',   'Health Care Select Sector SPDR','ETF',   'HEALTHCARE',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'ARKK',  'ARK Innovation ETF',            'ETF',   'TECHNOLOGY',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'ARKG',  'ARK Genomic Revolution ETF',    'ETF',   'HEALTHCARE',  true, NOW(), NOW(), 0),
(gen_random_uuid(), 'VIG',   'Vanguard Dividend Appreciation','ETF',   'UNKNOWN',     true, NOW(), NOW(), 0),
(gen_random_uuid(), 'SCHD',  'Schwab US Dividend Equity ETF', 'ETF',   'UNKNOWN',     true, NOW(), NOW(), 0),

-- ── Crypto ────────────────────────────────────────────────────────────────────
(gen_random_uuid(), 'BTC-USD', 'Bitcoin USD',                 'CRYPTO','CRYPTO',      true, NOW(), NOW(), 0),
(gen_random_uuid(), 'ETH-USD', 'Ethereum USD',                'CRYPTO','CRYPTO',      true, NOW(), NOW(), 0),
(gen_random_uuid(), 'SOL-USD', 'Solana USD',                  'CRYPTO','CRYPTO',      true, NOW(), NOW(), 0),
(gen_random_uuid(), 'BNB-USD', 'Binance Coin USD',            'CRYPTO','CRYPTO',      true, NOW(), NOW(), 0),
(gen_random_uuid(), 'XRP-USD', 'XRP USD',                     'CRYPTO','CRYPTO',      true, NOW(), NOW(), 0),
(gen_random_uuid(), 'ADA-USD', 'Cardano USD',                 'CRYPTO','CRYPTO',      true, NOW(), NOW(), 0),
(gen_random_uuid(), 'AVAX-USD','Avalanche USD',               'CRYPTO','CRYPTO',      true, NOW(), NOW(), 0),
(gen_random_uuid(), 'DOGE-USD','Dogecoin USD',                'CRYPTO','CRYPTO',      true, NOW(), NOW(), 0),
(gen_random_uuid(), 'LINK-USD','Chainlink USD',               'CRYPTO','CRYPTO',      true, NOW(), NOW(), 0),
(gen_random_uuid(), 'DOT-USD', 'Polkadot USD',                'CRYPTO','CRYPTO',      true, NOW(), NOW(), 0)

ON CONFLICT (ticker) DO NOTHING;