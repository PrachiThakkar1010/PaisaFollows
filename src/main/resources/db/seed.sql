USE stock_trading_db;

-- Workbench blocks UPDATEs without a key in WHERE ("safe updates").
-- Turn it off for this script only; it is turned back on at the end.
SET SQL_SAFE_UPDATES = 0;

-- 1. The list, in a temporary table that disappears when you disconnect
DROP TEMPORARY TABLE IF EXISTS tmp_stock;
CREATE TEMPORARY TABLE tmp_stock (
  symbol  VARCHAR(20)  NOT NULL,
  name    VARCHAR(150) NOT NULL,
  sector  VARCHAR(50)  NOT NULL,
  PRIMARY KEY (symbol)
);

INSERT INTO tmp_stock (symbol, name, sector) VALUES
('ADANIENT',   'Adani Enterprises Ltd',                     'Metals & Mining'),
('ADANIPORTS', 'Adani Ports and Special Economic Zone Ltd', 'Services'),
('APOLLOHOSP', 'Apollo Hospitals Enterprise Ltd',           'Healthcare'),
('ASIANPAINT', 'Asian Paints Ltd',                          'Consumer Durables'),
('AXISBANK',   'Axis Bank Ltd',                             'Financial Services'),
('BAJAJ-AUTO', 'Bajaj Auto Ltd',                            'Automobile and Auto Components'),
('BAJFINANCE', 'Bajaj Finance Ltd',                         'Financial Services'),
('BAJAJFINSV', 'Bajaj Finserv Ltd',                         'Financial Services'),
('BEL',        'Bharat Electronics Ltd',                    'Capital Goods'),
('BHARTIARTL', 'Bharti Airtel Ltd',                         'Telecommunication'),
('BSE',        'BSE Ltd',                                   'Financial Services'),
('CIPLA',      'Cipla Ltd',                                 'Healthcare'),
('COALINDIA',  'Coal India Ltd',                            'Oil, Gas & Consumable Fuels'),
('DRREDDY',    'Dr. Reddy''s Laboratories Ltd',             'Healthcare'),
('EICHERMOT',  'Eicher Motors Ltd',                         'Automobile and Auto Components'),
('ETERNAL',    'Eternal Ltd',                               'Consumer Services'),
('GRASIM',     'Grasim Industries Ltd',                     'Construction Materials'),
('HCLTECH',    'HCL Technologies Ltd',                      'Information Technology'),
('HDFCBANK',   'HDFC Bank Ltd',                             'Financial Services'),
('HDFCLIFE',   'HDFC Life Insurance Company Ltd',           'Financial Services'),
('HINDALCO',   'Hindalco Industries Ltd',                   'Metals & Mining'),
('HINDUNILVR', 'Hindustan Unilever Ltd',                    'Fast Moving Consumer Goods'),
('ICICIBANK',  'ICICI Bank Ltd',                            'Financial Services'),
('INDIGO',     'InterGlobe Aviation Ltd',                   'Services'),
('INFY',       'Infosys Ltd',                               'Information Technology'),
('ITC',        'ITC Ltd',                                   'Fast Moving Consumer Goods'),
('JIOFIN',     'Jio Financial Services Ltd',                'Financial Services'),
('JSWSTEEL',   'JSW Steel Ltd',                             'Metals & Mining'),
('KOTAKBANK',  'Kotak Mahindra Bank Ltd',                   'Financial Services'),
('LT',         'Larsen & Toubro Ltd',                       'Construction'),
('M&M',        'Mahindra & Mahindra Ltd',                   'Automobile and Auto Components'),
('MARUTI',     'Maruti Suzuki India Ltd',                   'Automobile and Auto Components'),
('MAXHEALTH',  'Max Healthcare Institute Ltd',              'Healthcare'),
('NESTLEIND',  'Nestle India Ltd',                          'Fast Moving Consumer Goods'),
('NTPC',       'NTPC Ltd',                                  'Power'),
('ONGC',       'Oil and Natural Gas Corporation Ltd',       'Oil, Gas & Consumable Fuels'),
('POWERGRID',  'Power Grid Corporation of India Ltd',       'Power'),
('RELIANCE',   'Reliance Industries Ltd',                   'Oil, Gas & Consumable Fuels'),
('SBILIFE',    'SBI Life Insurance Company Ltd',            'Financial Services'),
('SBIN',       'State Bank of India',                       'Financial Services'),
('SHRIRAMFIN', 'Shriram Finance Ltd',                       'Financial Services'),
('SUNPHARMA',  'Sun Pharmaceutical Industries Ltd',         'Healthcare'),
('TATACONSUM', 'Tata Consumer Products Ltd',                'Fast Moving Consumer Goods'),
('TATASTEEL',  'Tata Steel Ltd',                            'Metals & Mining'),
('TCS',        'Tata Consultancy Services Ltd',             'Information Technology'),
('TECHM',      'Tech Mahindra Ltd',                         'Information Technology'),
('TITAN',      'Titan Company Ltd',                         'Consumer Durables'),
('TMPV',       'Tata Motors Passenger Vehicles Ltd',        'Automobile and Auto Components'),
('TRENT',      'Trent Ltd',                                 'Consumer Services'),
('ULTRACEMCO', 'UltraTech Cement Ltd',                      'Construction Materials');

-- 2. INSTRUMENT: add only the stocks that are not there yet, each with a
--    new UUID. NOT EXISTS skips symbols already present (e.g. from an
--    earlier run), so the UNIQUE key on symbol is never violated.
INSERT INTO INSTRUMENT (instrument_id, symbol, name, instrument_type, exchange, status)
SELECT UUID_TO_BIN(UUID()), t.symbol, t.name, 'STOCK', 'NSE', 'ACTIVE'
FROM tmp_stock t
WHERE NOT EXISTS (SELECT 1 FROM INSTRUMENT i WHERE i.symbol = t.symbol);

--    Stocks that already existed: refresh name and make sure they are active.
--    Their id is kept, so nothing that points to them breaks.
UPDATE INSTRUMENT i
JOIN tmp_stock t ON t.symbol = i.symbol
SET i.name = t.name, i.status = 'ACTIVE';

-- 3. STOCK: the subtype row, reusing the INSTRUMENT id found by symbol.
INSERT INTO STOCK (instrument_id, company_name, sector, face_value)
SELECT i.instrument_id, t.name, t.sector, 1.00
FROM tmp_stock t
JOIN INSTRUMENT i ON i.symbol = t.symbol
WHERE NOT EXISTS (SELECT 1 FROM STOCK s WHERE s.instrument_id = i.instrument_id);

--    Stock rows that already existed: refresh name and sector.
UPDATE STOCK s
JOIN INSTRUMENT i ON i.instrument_id = s.instrument_id
JOIN tmp_stock t ON t.symbol = i.symbol
SET s.company_name = t.name, s.sector = t.sector;

-- 4. Stocks that are no longer in the NIFTY 50 (e.g. WIPRO, if you ran the
--    earlier 36-stock seed) are marked INACTIVE instead of deleted, so any
--    order or holding that refers to them stays valid.
UPDATE INSTRUMENT i
LEFT JOIN tmp_stock t ON t.symbol = i.symbol
SET i.status = 'INACTIVE'
WHERE i.instrument_type = 'STOCK' AND t.symbol IS NULL;

DROP TEMPORARY TABLE tmp_stock;
SET SQL_SAFE_UPDATES = 1;

-- 5. Check: should return 50 rows
SELECT BIN_TO_UUID(i.instrument_id) AS id, i.symbol, i.name, s.sector
FROM INSTRUMENT i
JOIN STOCK s ON s.instrument_id = i.instrument_id
WHERE i.instrument_type = 'STOCK' AND i.status = 'ACTIVE'
ORDER BY s.sector, i.symbol;