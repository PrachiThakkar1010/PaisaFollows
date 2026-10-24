-- =====================================================================
-- PaisaFollows - Module 11: database objects used by the market screens
--   5(c)(vii)  View:               v_market_movers
--   5(c)(vii)  Materialized view:  MV_SECTOR_SUMMARY (table + refresh procedure)
--   5(c)(viii) Stored procedure:   sp_refresh_mv_sector_summary
--
-- Run once in MySQL Workbench (Execute All). Safe to run again.
-- =====================================================================

USE stock_trading_db;

-- ---------------------------------------------------------------------
-- VIEW: every active stock with today's change
-- ---------------------------------------------------------------------
CREATE OR REPLACE VIEW v_market_movers AS
SELECT i.instrument_id,
       i.symbol,
       i.name,
       s.sector,
       ROUND(c.last_traded_price, 2)                                         AS ltp,
       ROUND(c.previous_close, 2)                                            AS previous_close,
       ROUND(c.last_traded_price - c.previous_close, 2)                      AS change_amount,
       ROUND((c.last_traded_price - c.previous_close) * 100
             / NULLIF(c.previous_close, 0), 2)                               AS change_pct
FROM INSTRUMENT i
JOIN STOCK s                ON s.instrument_id = i.instrument_id
JOIN CURRENT_MARKET_PRICE c ON c.instrument_id = i.instrument_id
WHERE i.status = 'ACTIVE';

-- ---------------------------------------------------------------------
-- MATERIALIZED VIEW: MySQL has no MATERIALIZED VIEW command, so it is
-- built the standard way: a real table that stores a query's result,
-- plus a procedure that recalculates it. The app calls the procedure
-- every 5 minutes; screens read the stored result instantly.
-- ---------------------------------------------------------------------
-- Only holds calculated data, so it is safe to drop and rebuild
DROP TABLE IF EXISTS MV_SECTOR_SUMMARY;
CREATE TABLE MV_SECTOR_SUMMARY (
    sector          VARCHAR(50)   NOT NULL,
    stocks          INT           NOT NULL,
    avg_change_pct  DECIMAL(8,2)  NULL,
    gainers         INT           NOT NULL,
    losers          INT           NOT NULL,
    total_holders   INT           NOT NULL,
    total_invested  DECIMAL(18,2) NOT NULL,
    refreshed_at    DATETIME      NOT NULL,
    PRIMARY KEY (sector)
);

DROP PROCEDURE IF EXISTS sp_refresh_mv_sector_summary;
DELIMITER $$
CREATE PROCEDURE sp_refresh_mv_sector_summary()
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SET SESSION sql_safe_updates = @saved_safe_updates;
        RESIGNAL;
    END;

    -- Workbench's "safe update mode" blocks a DELETE without a key in WHERE
    -- (error 1175). Emptying the whole summary is intended here, so switch it
    -- off for this procedure only and restore it at the end.
    SET @saved_safe_updates = @@SESSION.sql_safe_updates;
    SET SESSION sql_safe_updates = 0;

    -- DELETE (not TRUNCATE): TRUNCATE commits on its own and would break
    -- the all-or-nothing refresh
    START TRANSACTION;
    DELETE FROM MV_SECTOR_SUMMARY;
    INSERT INTO MV_SECTOR_SUMMARY
        (sector, stocks, avg_change_pct, gainers, losers, total_holders, total_invested, refreshed_at)
    SELECT COALESCE(m.sector, 'Other'),
           COUNT(*),
           ROUND(AVG(m.change_pct), 2),
           SUM(m.change_pct > 0),
           SUM(m.change_pct < 0),
           COALESCE(SUM(hh.holders), 0),
           COALESCE(SUM(hh.invested), 0),
           NOW()
    FROM v_market_movers m
    LEFT JOIN (
        SELECT instrument_id,
               COUNT(*)                            AS holders,
               SUM(quantity * average_buy_price)   AS invested
        FROM HOLDING
        GROUP BY instrument_id
    ) AS hh ON hh.instrument_id = m.instrument_id
    GROUP BY COALESCE(m.sector, 'Other');
    COMMIT;

    SET SESSION sql_safe_updates = @saved_safe_updates;
END $$
DELIMITER ;

CALL sp_refresh_mv_sector_summary();

-- Checks
SELECT symbol, ltp, change_pct FROM v_market_movers ORDER BY change_pct DESC LIMIT 5;
SELECT * FROM MV_SECTOR_SUMMARY ORDER BY total_invested DESC;