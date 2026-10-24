-- =====================================================================
-- Every query type listed in the project guidelines, on real project data.
--
-- PREREQUISITES: 01, 02, 03 have been run (50 stocks, prices, 100 demo users).
--
-- HOW TO RUN: in MySQL Workbench, run ONE section at a time:
-- select the lines of a section and press Ctrl+Shift+Enter (or the
-- lightning bolt). Each section prints its own results.
--
-- Lines marked  "EXPECTED ERROR"  are commented out on purpose: they show
-- what the database REFUSES. Copy such a line into a new tab and run it
-- to see (and screenshot) the error.
--
-- Section numbers match the guideline document: 5(a), 5(b), 5(c)(i)...
-- =====================================================================

USE stock_trading_db;
SET SQL_SAFE_UPDATES = 0;   -- Workbench blocks multi-row UPDATE/DELETE without this


-- #####################################################################
-- 5(c)(i)  DDL: CREATE (with integrity constraints), ALTER, RENAME,
--          TRUNCATE, DROP
-- #####################################################################

-- CREATE: a new feature table "price alerts" with every kind of constraint
DROP TABLE IF EXISTS PRICE_ALERT;
CREATE TABLE PRICE_ALERT (
    alert_id      BINARY(16)              NOT NULL,                       -- domain + NOT NULL
    account_id    BINARY(16)              NOT NULL,
    symbol        VARCHAR(20)             NOT NULL,
    direction     ENUM('ABOVE','BELOW')   NOT NULL,                       -- domain constraint
    target_price  DECIMAL(10,2)           NOT NULL,
    status        ENUM('ACTIVE','TRIGGERED') NOT NULL DEFAULT 'ACTIVE',   -- DEFAULT
    created_at    DATETIME                NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_price_alert PRIMARY KEY (alert_id),                     -- PRIMARY KEY
    CONSTRAINT uq_price_alert UNIQUE (account_id, symbol, direction, target_price),   -- UNIQUE
    CONSTRAINT chk_alert_price CHECK (target_price > 0),                  -- CHECK
    CONSTRAINT fk_alert_account FOREIGN KEY (account_id)                  -- REFERENTIAL INTEGRITY
        REFERENCES ACCOUNT (account_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_alert_symbol FOREIGN KEY (symbol)
        REFERENCES INSTRUMENT (symbol) ON DELETE CASCADE ON UPDATE CASCADE
);
DESCRIBE PRICE_ALERT;

-- ALTER: add, change, rename and drop a column; add an index
ALTER TABLE PRICE_ALERT ADD COLUMN note VARCHAR(50) NULL AFTER target_price;
ALTER TABLE PRICE_ALERT MODIFY COLUMN note VARCHAR(200) NULL;
ALTER TABLE PRICE_ALERT RENAME COLUMN note TO remark;
ALTER TABLE PRICE_ALERT ADD INDEX idx_alert_symbol_status (symbol, status);
DESCRIBE PRICE_ALERT;
ALTER TABLE PRICE_ALERT DROP COLUMN remark;

-- ALTER on a real table: deleting a watchlist should delete its items too
-- (drop the plain foreign key, add it back with ON DELETE CASCADE)
ALTER TABLE WATCHLIST_ITEM DROP FOREIGN KEY WATCHLIST_ITEM_ibfk_1;
ALTER TABLE WATCHLIST_ITEM ADD CONSTRAINT WATCHLIST_ITEM_ibfk_1
    FOREIGN KEY (watchlist_id) REFERENCES WATCHLIST (watchlist_id) ON DELETE CASCADE;

-- RENAME: table rename (and back)
RENAME TABLE PRICE_ALERT TO PRICE_ALERTS;
SHOW TABLES LIKE 'PRICE_ALERT%';
RENAME TABLE PRICE_ALERTS TO PRICE_ALERT;

-- TRUNCATE and DROP, on a scratch copy (never on live data)
DROP TABLE IF EXISTS HISTORY_ARCHIVE;
CREATE TABLE HISTORY_ARCHIVE LIKE MARKET_PRICE_HISTORY;          -- same columns + primary key, no foreign keys
INSERT INTO HISTORY_ARCHIVE (price_update_id, instrument_id, last_traded_price, exchange_timestamp, received_at, event_type)
SELECT UUID_TO_BIN(UUID()), instrument_id, last_traded_price, NOW(), NOW(), 'SNAPSHOT'
FROM CURRENT_MARKET_PRICE;                                        -- one snapshot row per stock (50 rows)
SELECT COUNT(*) AS rows_before_truncate FROM HISTORY_ARCHIVE;
TRUNCATE TABLE HISTORY_ARCHIVE;                                   -- removes all rows at once, keeps the table
SELECT COUNT(*) AS rows_after_truncate FROM HISTORY_ARCHIVE;
DROP TABLE HISTORY_ARCHIVE;                                       -- removes the table itself
-- EXPECTED ERROR (TRUNCATE is refused on a table other tables point to):
-- TRUNCATE TABLE INSTRUMENT;
--   -> Error 1701: Cannot truncate a table referenced in a foreign key constraint


-- #####################################################################
-- 5(c)(ii)  DML: INSERT, UPDATE (with and without cascade),
--           DELETE (with and without cascade)
-- #####################################################################

-- Pick one demo account to work with
SET @acct = (SELECT a.account_id FROM ACCOUNT a JOIN `USER` u ON u.user_id = a.user_id
             WHERE u.email = 'demo1@paisafollows.test');

-- INSERT: single row
INSERT INTO PRICE_ALERT (alert_id, account_id, symbol, direction, target_price)
VALUES (UUID_TO_BIN(UUID()), @acct, 'RELIANCE', 'ABOVE', 1500.00);

-- INSERT: several rows in one statement
INSERT INTO PRICE_ALERT (alert_id, account_id, symbol, direction, target_price) VALUES
    (UUID_TO_BIN(UUID()), @acct, 'TCS',      'BELOW', 3000.00),
    (UUID_TO_BIN(UUID()), @acct, 'INFY',     'ABOVE', 2000.00),
    (UUID_TO_BIN(UUID()), @acct, 'HDFCBANK', 'BELOW', 1500.00);

-- INSERT ... SELECT: an alert for every demo user on every stock they hold,
-- at 5% below today's price (hundreds of rows in one statement)
INSERT INTO PRICE_ALERT (alert_id, account_id, symbol, direction, target_price)
SELECT UUID_TO_BIN(UUID()), p.account_id, i.symbol, 'BELOW', ROUND(c.last_traded_price * 0.95, 2)
FROM HOLDING h
JOIN PORTFOLIO p            ON p.portfolio_id  = h.portfolio_id
JOIN INSTRUMENT i           ON i.instrument_id = h.instrument_id
JOIN CURRENT_MARKET_PRICE c ON c.instrument_id = h.instrument_id
WHERE p.account_id <> @acct;
SELECT COUNT(*) AS alerts_created FROM PRICE_ALERT;

-- UPDATE without cascade: mark alerts whose condition is met as TRIGGERED (UPDATE with JOIN)
UPDATE PRICE_ALERT pa
JOIN CURRENT_MARKET_PRICE c ON c.instrument_id = (SELECT instrument_id FROM INSTRUMENT WHERE symbol = pa.symbol)
SET pa.status = 'TRIGGERED'
WHERE pa.status = 'ACTIVE'
  AND ((pa.direction = 'ABOVE' AND c.last_traded_price >= pa.target_price)
    OR (pa.direction = 'BELOW' AND c.last_traded_price <= pa.target_price));
SELECT status, COUNT(*) AS alerts FROM PRICE_ALERT GROUP BY status;

-- UPDATE with cascade: a company changes its NSE symbol (this really happens,
-- e.g. Tata Motors' passenger business became TMPV). ON UPDATE CASCADE
-- rewrites the symbol in PRICE_ALERT automatically. Done inside a
-- transaction and rolled back, so the real symbol is not changed.
START TRANSACTION;
UPDATE INSTRUMENT SET symbol = 'RELIANCE_NEW' WHERE symbol = 'RELIANCE';
SELECT symbol, direction, target_price FROM PRICE_ALERT WHERE symbol LIKE 'RELIANCE%';   -- shows RELIANCE_NEW
ROLLBACK;
SELECT symbol, direction, target_price FROM PRICE_ALERT WHERE symbol LIKE 'RELIANCE%';   -- back to RELIANCE

-- DELETE without cascade: a plain delete of chosen rows
DELETE FROM PRICE_ALERT WHERE status = 'TRIGGERED';
-- EXPECTED ERROR (no cascade = RESTRICT): a stock that other tables point to
-- (prices, holdings, watchlists...) cannot be deleted
-- DELETE FROM INSTRUMENT WHERE symbol = 'RELIANCE';
--   -> Error 1451: Cannot delete or update a parent row: a foreign key constraint fails

-- DELETE with cascade: deleting a watchlist removes its items (cascade added in 5(c)(i))
SET @wl = UUID_TO_BIN(UUID());
INSERT INTO WATCHLIST (watchlist_id, account_id, name) VALUES (@wl, @acct, 'Temporary list');
INSERT INTO WATCHLIST_ITEM (watchlist_item_id, watchlist_id, instrument_id)
SELECT UUID_TO_BIN(UUID()), @wl, instrument_id FROM INSTRUMENT WHERE symbol IN ('TCS', 'INFY', 'HCLTECH');
SELECT COUNT(*) AS items_before FROM WATCHLIST_ITEM WHERE watchlist_id = @wl;
DELETE FROM WATCHLIST WHERE watchlist_id = @wl;
SELECT COUNT(*) AS items_after_cascade FROM WATCHLIST_ITEM WHERE watchlist_id = @wl;


-- #####################################################################
-- 5(c)(iv)  TCL: COMMIT, SAVEPOINT, ROLLBACK
-- #####################################################################

SET @wallet = (SELECT wallet_id FROM WALLET WHERE account_id = @acct);
SELECT available_balance AS balance_at_start FROM WALLET WHERE wallet_id = @wallet;

START TRANSACTION;
    -- Step 1: a deposit of 5,000 (balance + the record that explains it)
    UPDATE WALLET SET available_balance = available_balance + 5000 WHERE wallet_id = @wallet;
    INSERT INTO WALLET_TRANSACTION (transaction_id, wallet_id, transaction_type, amount, status, description, processed_at)
    VALUES (UUID_TO_BIN(UUID()), @wallet, 'DEPOSIT', 5000, 'COMPLETED', 'TCL demo deposit', NOW());

    SAVEPOINT after_deposit;

    -- Step 2: a mistaken withdrawal of 2,000
    UPDATE WALLET SET available_balance = available_balance - 2000 WHERE wallet_id = @wallet;
    INSERT INTO WALLET_TRANSACTION (transaction_id, wallet_id, transaction_type, amount, status, description, processed_at)
    VALUES (UUID_TO_BIN(UUID()), @wallet, 'WITHDRAWAL', 2000, 'COMPLETED', 'TCL demo mistake', NOW());
    SELECT available_balance AS balance_after_mistake FROM WALLET WHERE wallet_id = @wallet;

    -- Undo only step 2; step 1 stays
    ROLLBACK TO SAVEPOINT after_deposit;
    SELECT available_balance AS balance_after_rollback_to_savepoint FROM WALLET WHERE wallet_id = @wallet;
COMMIT;   -- makes step 1 permanent

-- A full ROLLBACK: nothing in the transaction survives
START TRANSACTION;
    UPDATE WALLET SET available_balance = 0 WHERE wallet_id = @wallet;
    SELECT available_balance AS inside_transaction FROM WALLET WHERE wallet_id = @wallet;
ROLLBACK;
SELECT available_balance AS after_full_rollback FROM WALLET WHERE wallet_id = @wallet;


-- #####################################################################
-- 5(c)(v)  DQL: SELECT
-- #####################################################################

-- 1. WHERE: Financial Services stocks priced above 1,000
SELECT i.symbol, i.name, c.last_traded_price
FROM INSTRUMENT i
JOIN STOCK s                ON s.instrument_id = i.instrument_id
JOIN CURRENT_MARKET_PRICE c ON c.instrument_id = i.instrument_id
WHERE s.sector = 'Financial Services' AND c.last_traded_price > 1000;

-- 2. Aggregate functions: COUNT, SUM, AVG, MIN, MAX
SELECT COUNT(*)                         AS stocks,
       MIN(last_traded_price)           AS cheapest,
       MAX(last_traded_price)           AS costliest,
       ROUND(AVG(last_traded_price), 2) AS average_price
FROM CURRENT_MARKET_PRICE;

SELECT COUNT(*)                         AS wallets,
       SUM(available_balance)           AS total_cash,
       ROUND(AVG(available_balance), 2) AS average_cash
FROM WALLET;

-- 3. IN, LIKE, DISTINCT
SELECT DISTINCT sector FROM STOCK ORDER BY sector;

SELECT symbol, name FROM INSTRUMENT
WHERE name LIKE '%Bank%' OR symbol LIKE 'TATA%';

SELECT name, email, city FROM `USER`
WHERE city IN ('Ahmedabad', 'Gandhinagar')
ORDER BY name;

-- 4. ORDER BY, LIMIT, OFFSET: top gainers, page 1 and page 2 (10 per page)
SELECT i.symbol,
       c.last_traded_price,
       ROUND((c.last_traded_price - c.previous_close) * 100 / c.previous_close, 2) AS change_pct
FROM CURRENT_MARKET_PRICE c
JOIN INSTRUMENT i ON i.instrument_id = c.instrument_id
ORDER BY change_pct DESC
LIMIT 10 OFFSET 0;

SELECT i.symbol,
       c.last_traded_price,
       ROUND((c.last_traded_price - c.previous_close) * 100 / c.previous_close, 2) AS change_pct
FROM CURRENT_MARKET_PRICE c
JOIN INSTRUMENT i ON i.instrument_id = c.instrument_id
ORDER BY change_pct DESC
LIMIT 10 OFFSET 10;

-- 5. GROUP BY, and GROUP BY with HAVING
SELECT s.sector, COUNT(*) AS stocks, ROUND(AVG(c.last_traded_price), 2) AS avg_price
FROM STOCK s
JOIN CURRENT_MARKET_PRICE c ON c.instrument_id = s.instrument_id
GROUP BY s.sector
ORDER BY stocks DESC;

SELECT s.sector, COUNT(*) AS stocks
FROM STOCK s
GROUP BY s.sector
HAVING COUNT(*) >= 4;

-- Investors who have put more than 20,000 into the market
SELECT u.email, ROUND(SUM(h.quantity * h.average_buy_price), 2) AS invested
FROM `USER` u
JOIN ACCOUNT a   ON a.user_id      = u.user_id
JOIN PORTFOLIO p ON p.account_id   = a.account_id
JOIN HOLDING h   ON h.portfolio_id = p.portfolio_id
GROUP BY u.email
HAVING invested > 20000
ORDER BY invested DESC;

-- 6. Joins: all types
-- INNER JOIN: who holds what
SELECT u.email, i.symbol, h.quantity, h.average_buy_price
FROM HOLDING h
INNER JOIN PORTFOLIO p  ON p.portfolio_id  = h.portfolio_id
INNER JOIN ACCOUNT a    ON a.account_id    = p.account_id
INNER JOIN `USER` u     ON u.user_id       = a.user_id
INNER JOIN INSTRUMENT i ON i.instrument_id = h.instrument_id
ORDER BY u.email
LIMIT 20;

-- LEFT JOIN: every stock and how many watchlists include it (0 included)
SELECT i.symbol, COUNT(wi.watchlist_item_id) AS on_watchlists
FROM INSTRUMENT i
LEFT JOIN WATCHLIST_ITEM wi ON wi.instrument_id = i.instrument_id
WHERE i.instrument_type = 'STOCK'
GROUP BY i.symbol
ORDER BY on_watchlists, i.symbol;

-- RIGHT JOIN: every stock and how many people hold it (0 included)
SELECT i.symbol, COUNT(h.holding_id) AS holders
FROM HOLDING h
RIGHT JOIN INSTRUMENT i ON i.instrument_id = h.instrument_id
WHERE i.instrument_type = 'STOCK'
GROUP BY i.symbol
ORDER BY holders DESC, i.symbol;

-- FULL OUTER JOIN: MySQL has no FULL OUTER JOIN keyword, so it is
-- written as LEFT JOIN  UNION  RIGHT JOIN (rows from both sides, matched or not)
WITH watched AS (
    SELECT instrument_id, COUNT(*) AS watchers FROM WATCHLIST_ITEM GROUP BY instrument_id
), held AS (
    SELECT instrument_id, COUNT(*) AS holders FROM HOLDING GROUP BY instrument_id
)
SELECT w.instrument_id AS id, w.watchers, h.holders
FROM watched w LEFT JOIN held h ON h.instrument_id = w.instrument_id
UNION
SELECT h.instrument_id, w.watchers, h.holders
FROM watched w RIGHT JOIN held h ON h.instrument_id = w.instrument_id;

-- CROSS JOIN: every combination: 3 investors x 3 popular stocks
SELECT u.email, i.symbol
FROM (SELECT email FROM `USER` ORDER BY email LIMIT 3) u
CROSS JOIN (SELECT symbol FROM INSTRUMENT WHERE symbol IN ('RELIANCE', 'TCS', 'INFY')) i;

-- SELF JOIN: pairs of stocks from the same sector (STOCK joined with itself)
SELECT ia.symbol AS stock_1, ib.symbol AS stock_2, sa.sector
FROM STOCK sa
JOIN STOCK sb       ON sb.sector = sa.sector AND sa.instrument_id < sb.instrument_id
JOIN INSTRUMENT ia  ON ia.instrument_id = sa.instrument_id
JOIN INSTRUMENT ib  ON ib.instrument_id = sb.instrument_id
ORDER BY sa.sector, stock_1;

-- NATURAL JOIN: joins on the column the tables share (instrument_id)
SELECT symbol, company_name, sector
FROM INSTRUMENT NATURAL JOIN STOCK
ORDER BY symbol
LIMIT 10;

-- 7. UNION, UNION ALL, INTERSECT
-- Stocks that are watched OR held (UNION removes duplicates)
SELECT i.symbol FROM WATCHLIST_ITEM wi JOIN INSTRUMENT i ON i.instrument_id = wi.instrument_id
UNION
SELECT i.symbol FROM HOLDING h JOIN INSTRUMENT i ON i.instrument_id = h.instrument_id
ORDER BY symbol;

-- Same, keeping duplicates (UNION ALL), counted per stock
SELECT symbol, COUNT(*) AS appearances FROM (
    SELECT i.symbol FROM WATCHLIST_ITEM wi JOIN INSTRUMENT i ON i.instrument_id = wi.instrument_id
    UNION ALL
    SELECT i.symbol FROM HOLDING h JOIN INSTRUMENT i ON i.instrument_id = h.instrument_id
) AS everything
GROUP BY symbol
ORDER BY appearances DESC;

-- Stocks that are watched AND held (INTERSECT, MySQL 8.0.31+)
SELECT i.symbol FROM WATCHLIST_ITEM wi JOIN INSTRUMENT i ON i.instrument_id = wi.instrument_id
INTERSECT
SELECT i.symbol FROM HOLDING h JOIN INSTRUMENT i ON i.instrument_id = h.instrument_id;

-- 8. EXCEPT, ANY, ALL
-- Stocks people watch but nobody holds (EXCEPT, MySQL 8.0.31+)
SELECT i.symbol FROM WATCHLIST_ITEM wi JOIN INSTRUMENT i ON i.instrument_id = wi.instrument_id
EXCEPT
SELECT i.symbol FROM HOLDING h JOIN INSTRUMENT i ON i.instrument_id = h.instrument_id;

-- ALL: stocks priced higher than EVERY Information Technology stock
SELECT i.symbol, c.last_traded_price
FROM INSTRUMENT i JOIN CURRENT_MARKET_PRICE c ON c.instrument_id = i.instrument_id
WHERE c.last_traded_price > ALL (
    SELECT c2.last_traded_price
    FROM CURRENT_MARKET_PRICE c2 JOIN STOCK s2 ON s2.instrument_id = c2.instrument_id
    WHERE s2.sector = 'Information Technology');

-- ANY: stocks priced higher than AT LEAST ONE Information Technology stock
SELECT i.symbol, c.last_traded_price
FROM INSTRUMENT i JOIN CURRENT_MARKET_PRICE c ON c.instrument_id = i.instrument_id
WHERE c.last_traded_price > ANY (
    SELECT c2.last_traded_price
    FROM CURRENT_MARKET_PRICE c2 JOIN STOCK s2 ON s2.instrument_id = c2.instrument_id
    WHERE s2.sector = 'Information Technology')
ORDER BY c.last_traded_price;


-- #####################################################################
-- 5(c)(vi)  Subqueries
-- #####################################################################

-- Scalar subquery: stocks priced above the market average
SELECT i.symbol, c.last_traded_price
FROM INSTRUMENT i JOIN CURRENT_MARKET_PRICE c ON c.instrument_id = i.instrument_id
WHERE c.last_traded_price > (SELECT AVG(last_traded_price) FROM CURRENT_MARKET_PRICE)
ORDER BY c.last_traded_price DESC;

-- Correlated subquery: stocks priced above the average OF THEIR OWN SECTOR
-- (the inner query re-runs for each outer row, using s.sector)
SELECT i.symbol, s.sector, c.last_traded_price
FROM INSTRUMENT i
JOIN STOCK s                ON s.instrument_id = i.instrument_id
JOIN CURRENT_MARKET_PRICE c ON c.instrument_id = i.instrument_id
WHERE c.last_traded_price > (
    SELECT AVG(c2.last_traded_price)
    FROM STOCK s2 JOIN CURRENT_MARKET_PRICE c2 ON c2.instrument_id = s2.instrument_id
    WHERE s2.sector = s.sector)
ORDER BY s.sector;

-- IN with a subquery: investors who hold RELIANCE
SELECT u.name, u.email FROM `USER` u
WHERE u.user_id IN (
    SELECT a.user_id FROM ACCOUNT a
    JOIN PORTFOLIO p ON p.account_id   = a.account_id
    JOIN HOLDING h   ON h.portfolio_id = p.portfolio_id
    JOIN INSTRUMENT i ON i.instrument_id = h.instrument_id
    WHERE i.symbol = 'RELIANCE');

-- EXISTS: investors holding at least one IT stock
SELECT u.email FROM `USER` u
WHERE EXISTS (
    SELECT 1 FROM ACCOUNT a
    JOIN PORTFOLIO p ON p.account_id   = a.account_id
    JOIN HOLDING h   ON h.portfolio_id = p.portfolio_id
    JOIN STOCK s     ON s.instrument_id = h.instrument_id
    WHERE a.user_id = u.user_id AND s.sector = 'Information Technology');

-- NOT EXISTS: users who have never traded
SELECT u.email FROM `USER` u
WHERE NOT EXISTS (
    SELECT 1 FROM ACCOUNT a JOIN orders o ON o.account_id = a.account_id
    WHERE a.user_id = u.user_id AND o.status = 'FILLED');

-- Subquery in FROM (derived table): the 5 biggest portfolios by cost
SELECT ranked.email, ranked.invested
FROM (
    SELECT u.email, SUM(h.quantity * h.average_buy_price) AS invested
    FROM `USER` u
    JOIN ACCOUNT a   ON a.user_id      = u.user_id
    JOIN PORTFOLIO p ON p.account_id   = a.account_id
    JOIN HOLDING h   ON h.portfolio_id = p.portfolio_id
    GROUP BY u.email
) AS ranked
ORDER BY ranked.invested DESC
LIMIT 5;


-- #####################################################################
-- 5(c)(viii)  Functions (created before the views, which use them)
-- #####################################################################
-- Functions and triggers need permission to be created on a server with
-- binary logging (Aiven has it on). First check:
SHOW VARIABLES LIKE 'log_bin_trust_function_creators';
-- ON  -> everything below works.
-- OFF -> CREATE FUNCTION / CREATE TRIGGER fail with error 1419. Then run
--        this section and the Triggers section on a local MySQL Server
--        (where you are root), or ask Aiven support to enable it.

DROP FUNCTION IF EXISTS fn_age;
DELIMITER $$
CREATE FUNCTION fn_age(p_dob DATE) RETURNS INT
NOT DETERMINISTIC NO SQL
BEGIN
    RETURN TIMESTAMPDIFF(YEAR, p_dob, CURDATE());
END $$
DELIMITER ;

DROP FUNCTION IF EXISTS fn_portfolio_value;
DELIMITER $$
CREATE FUNCTION fn_portfolio_value(p_portfolio_id BINARY(16)) RETURNS DECIMAL(18,2)
NOT DETERMINISTIC READS SQL DATA
BEGIN
    DECLARE v_value DECIMAL(18,2);
    SELECT COALESCE(SUM(h.quantity * c.last_traded_price), 0)
      INTO v_value
    FROM HOLDING h
    JOIN CURRENT_MARKET_PRICE c ON c.instrument_id = h.instrument_id
    WHERE h.portfolio_id = p_portfolio_id;
    RETURN v_value;
END $$
DELIMITER ;

SELECT u.email, fn_age(u.date_of_birth) AS age, fn_portfolio_value(p.portfolio_id) AS portfolio_value
FROM `USER` u
JOIN ACCOUNT a   ON a.user_id    = u.user_id
JOIN PORTFOLIO p ON p.account_id = a.account_id
ORDER BY portfolio_value DESC
LIMIT 10;


-- #####################################################################
-- 5(c)(vii)  Views and Materialized Views
-- #####################################################################

-- View 1: each holding with its LIVE value and profit/loss
-- (current value is calculated, not stored: the HOLDING design decision)
CREATE OR REPLACE VIEW v_portfolio_holdings AS
SELECT u.email,
       i.symbol,
       s.sector,
       h.quantity,
       h.average_buy_price,
       c.last_traded_price                                         AS ltp,
       ROUND(h.quantity * c.last_traded_price, 2)                  AS current_value,
       ROUND(h.quantity * (c.last_traded_price - h.average_buy_price), 2) AS unrealised_pnl
FROM HOLDING h
JOIN PORTFOLIO p                 ON p.portfolio_id  = h.portfolio_id
JOIN ACCOUNT a                   ON a.account_id    = p.account_id
JOIN `USER` u                    ON u.user_id       = a.user_id
JOIN INSTRUMENT i                ON i.instrument_id = h.instrument_id
LEFT JOIN STOCK s                ON s.instrument_id = i.instrument_id
LEFT JOIN CURRENT_MARKET_PRICE c ON c.instrument_id = i.instrument_id;

SELECT * FROM v_portfolio_holdings WHERE email = 'demo1@paisafollows.test';

-- View 2: market movers
CREATE OR REPLACE VIEW v_market_movers AS
SELECT i.symbol, i.name, s.sector, c.last_traded_price, c.previous_close,
       ROUND(c.last_traded_price - c.previous_close, 2)                            AS change_amount,
       ROUND((c.last_traded_price - c.previous_close) * 100 / c.previous_close, 2) AS change_pct
FROM INSTRUMENT i
JOIN STOCK s                ON s.instrument_id = i.instrument_id
JOIN CURRENT_MARKET_PRICE c ON c.instrument_id = i.instrument_id
WHERE i.status = 'ACTIVE';

SELECT * FROM v_market_movers ORDER BY change_pct DESC LIMIT 5;

-- View 3: one row per investor, using both functions
CREATE OR REPLACE VIEW v_user_overview AS
SELECT u.email,
       u.name,
       fn_age(u.date_of_birth)                                          AS age,
       CASE WHEN fn_age(u.date_of_birth) < 18 THEN 'MINOR' ELSE 'MAJOR' END AS investor_type,
       (SELECT GROUP_CONCAT(seg.segment ORDER BY seg.segment)
          FROM ACCOUNT_SEGMENT seg WHERE seg.account_id = a.account_id)  AS segments,
       w.available_balance,
       fn_portfolio_value(p.portfolio_id)                                AS portfolio_value
FROM `USER` u
JOIN ACCOUNT a   ON a.user_id    = u.user_id
JOIN WALLET w    ON w.account_id = a.account_id
JOIN PORTFOLIO p ON p.account_id = a.account_id AND p.name = 'Main';

SELECT * FROM v_user_overview ORDER BY portfolio_value DESC LIMIT 10;

-- View 4: an updatable view WITH CHECK OPTION
CREATE OR REPLACE VIEW v_active_alerts AS
SELECT alert_id, account_id, symbol, direction, target_price, status
FROM PRICE_ALERT
WHERE status = 'ACTIVE'
WITH CHECK OPTION;

UPDATE v_active_alerts SET target_price = target_price + 10 WHERE symbol = 'TCS';   -- allowed
-- EXPECTED ERROR (the row would leave the view, so CHECK OPTION refuses):
-- UPDATE v_active_alerts SET status = 'TRIGGERED' WHERE symbol = 'TCS';
--   -> Error 1369: CHECK OPTION failed

-- MATERIALIZED VIEW: MySQL has no MATERIALIZED VIEW command. The standard
-- workaround is a real table holding a query's stored result, plus a
-- procedure that refreshes it (optionally on a schedule with an EVENT).
DROP TABLE IF EXISTS MV_SECTOR_SUMMARY;
CREATE TABLE MV_SECTOR_SUMMARY (
    sector          VARCHAR(50)   NOT NULL PRIMARY KEY,
    stocks          INT           NOT NULL,
    avg_price       DECIMAL(18,2) NOT NULL,
    holders         INT           NOT NULL,
    total_invested  DECIMAL(18,2) NOT NULL,
    refreshed_at    DATETIME      NOT NULL
);

DROP PROCEDURE IF EXISTS sp_refresh_mv_sector_summary;
DELIMITER $$
CREATE PROCEDURE sp_refresh_mv_sector_summary()
BEGIN
    -- DELETE (not TRUNCATE) because TRUNCATE commits on its own and would
    -- break the all-or-nothing refresh
    START TRANSACTION;
    DELETE FROM MV_SECTOR_SUMMARY;
    INSERT INTO MV_SECTOR_SUMMARY (sector, stocks, avg_price, holders, total_invested, refreshed_at)
    SELECT s.sector,
           COUNT(DISTINCT s.instrument_id),
           ROUND(AVG(c.last_traded_price), 2),
           COUNT(h.holding_id),
           ROUND(COALESCE(SUM(h.quantity * h.average_buy_price), 0), 2),
           NOW()
    FROM STOCK s
    JOIN CURRENT_MARKET_PRICE c ON c.instrument_id = s.instrument_id
    LEFT JOIN HOLDING h         ON h.instrument_id = s.instrument_id
    GROUP BY s.sector;
    COMMIT;
END $$
DELIMITER ;

CALL sp_refresh_mv_sector_summary();
SELECT * FROM MV_SECTOR_SUMMARY ORDER BY total_invested DESC;

-- Optional automatic refresh every 10 minutes (needs event_scheduler = ON;
-- check with: SHOW VARIABLES LIKE 'event_scheduler';)
DROP EVENT IF EXISTS ev_refresh_mv_sector_summary;
CREATE EVENT ev_refresh_mv_sector_summary
    ON SCHEDULE EVERY 10 MINUTE
    DO CALL sp_refresh_mv_sector_summary();


-- #####################################################################
-- 5(c)(viii)  Stored Procedures
-- (sp_seed_demo_data in 03-demo-data.sql and sp_refresh_mv_sector_summary
--  above are procedures too)
-- #####################################################################

-- Account statement: balance + every money movement for one user
DROP PROCEDURE IF EXISTS sp_account_statement;
DELIMITER $$
CREATE PROCEDURE sp_account_statement(IN p_email VARCHAR(255))
BEGIN
    DECLARE v_wallet BINARY(16);

    SELECT w.wallet_id INTO v_wallet
    FROM `USER` u
    JOIN ACCOUNT a ON a.user_id    = u.user_id
    JOIN WALLET w  ON w.account_id = a.account_id
    WHERE u.email = p_email
    LIMIT 1;

    IF v_wallet IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'No wallet found for this email';
    END IF;

    SELECT available_balance, reserved_balance FROM WALLET WHERE wallet_id = v_wallet;

    SELECT created_at, transaction_type, amount, status, description
    FROM WALLET_TRANSACTION
    WHERE wallet_id = v_wallet
    ORDER BY created_at;
END $$
DELIMITER ;

CALL sp_account_statement('demo1@paisafollows.test');


-- #####################################################################
-- 5(c)(viii)  Triggers
-- #####################################################################

-- Trigger 1 (BEFORE INSERT): store email in lower case and PAN in upper case,
-- whoever inserts the row
DROP TRIGGER IF EXISTS trg_user_normalise;
DELIMITER $$
CREATE TRIGGER trg_user_normalise
BEFORE INSERT ON `USER`
FOR EACH ROW
BEGIN
    SET NEW.email = LOWER(TRIM(NEW.email));
    SET NEW.pan   = UPPER(TRIM(NEW.pan));
END $$
DELIMITER ;

-- Trigger 2 (BEFORE UPDATE): a finished order can never be changed again
DROP TRIGGER IF EXISTS trg_orders_protect_closed;
DELIMITER $$
CREATE TRIGGER trg_orders_protect_closed
BEFORE UPDATE ON orders
FOR EACH ROW
BEGIN
    IF OLD.status <> 'OPEN' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Only OPEN orders can be changed';
    END IF;
END $$
DELIMITER ;

-- Trigger 3 (AFTER UPDATE): audit log of every wallet balance change
DROP TABLE IF EXISTS WALLET_AUDIT;
CREATE TABLE WALLET_AUDIT (
    audit_id        BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    wallet_id       BINARY(16)    NOT NULL,
    old_available   DECIMAL(18,2) NOT NULL,
    new_available   DECIMAL(18,2) NOT NULL,
    old_reserved    DECIMAL(18,2) NOT NULL,
    new_reserved    DECIMAL(18,2) NOT NULL,
    changed_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

DROP TRIGGER IF EXISTS trg_wallet_audit;
DELIMITER $$
CREATE TRIGGER trg_wallet_audit
AFTER UPDATE ON WALLET
FOR EACH ROW
BEGIN
    IF OLD.available_balance <> NEW.available_balance OR OLD.reserved_balance <> NEW.reserved_balance THEN
        INSERT INTO WALLET_AUDIT (wallet_id, old_available, new_available, old_reserved, new_reserved)
        VALUES (NEW.wallet_id, OLD.available_balance, NEW.available_balance, OLD.reserved_balance, NEW.reserved_balance);
    END IF;
END $$
DELIMITER ;

-- See the triggers work
-- Trigger 3: change a balance, then read the audit log
UPDATE WALLET SET available_balance = available_balance + 1 WHERE wallet_id = @wallet;
UPDATE WALLET SET available_balance = available_balance - 1 WHERE wallet_id = @wallet;
SELECT old_available, new_available, changed_at FROM WALLET_AUDIT ORDER BY audit_id DESC LIMIT 2;

-- Trigger 2: EXPECTED ERROR (changing a FILLED order is refused):
-- UPDATE orders SET quantity = 999 WHERE status = 'FILLED' LIMIT 1;
--   -> Error 1644: Only OPEN orders can be changed

SHOW TRIGGERS;


-- #####################################################################
-- 5(c)(iii)  DCL: GRANT and REVOKE
-- A read-only "analyst" login that may see market data and alerts.
-- If Aiven refuses CREATE USER, create the user in the Aiven console
-- (your service -> Users -> Add user) and run only the GRANT/REVOKE lines.
-- #####################################################################

CREATE USER IF NOT EXISTS 'pf_analyst'@'%' IDENTIFIED BY 'Analyst#2026pf';

GRANT SELECT ON stock_trading_db.v_market_movers TO 'pf_analyst'@'%';
GRANT SELECT ON stock_trading_db.INSTRUMENT      TO 'pf_analyst'@'%';
GRANT SELECT, INSERT ON stock_trading_db.PRICE_ALERT TO 'pf_analyst'@'%';
SHOW GRANTS FOR 'pf_analyst'@'%';

REVOKE INSERT ON stock_trading_db.PRICE_ALERT FROM 'pf_analyst'@'%';
SHOW GRANTS FOR 'pf_analyst'@'%';

-- Optional clean-up when the demo is over:
-- DROP USER 'pf_analyst'@'%';

SET SQL_SAFE_UPDATES = 1;