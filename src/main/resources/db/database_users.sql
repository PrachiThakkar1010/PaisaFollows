USE stock_trading_db;

-- 1. The users
CREATE USER IF NOT EXISTS 'pf_app'@'%'    IDENTIFIED BY 'Change_Me_App_2026!';
CREATE USER IF NOT EXISTS 'pf_report'@'%' IDENTIFIED BY 'Change_Me_Report_2026!';

-- 2. pf_app: database-wide rights
GRANT SELECT, INSERT, EXECUTE                     ON stock_trading_db.* TO 'pf_app'@'%';
GRANT CREATE, DROP, ALTER, INDEX, REFERENCES      ON stock_trading_db.* TO 'pf_app'@'%';

-- 3. pf_app: UPDATE and DELETE only on the tables the app changes
GRANT UPDATE         ON stock_trading_db.WALLET               TO 'pf_app'@'%';
GRANT UPDATE         ON stock_trading_db.orders               TO 'pf_app'@'%';
GRANT UPDATE         ON stock_trading_db.CURRENT_MARKET_PRICE TO 'pf_app'@'%';
GRANT UPDATE, DELETE ON stock_trading_db.HOLDING              TO 'pf_app'@'%';
GRANT UPDATE, DELETE ON stock_trading_db.PRICE_ALERT          TO 'pf_app'@'%';
GRANT UPDATE, DELETE ON stock_trading_db.INSTRUMENT           TO 'pf_app'@'%';
GRANT DELETE         ON stock_trading_db.ORDER_BOOK           TO 'pf_app'@'%';
GRANT DELETE         ON stock_trading_db.WATCHLIST            TO 'pf_app'@'%';
GRANT DELETE         ON stock_trading_db.WATCHLIST_ITEM       TO 'pf_app'@'%';

-- 4. REVOKE: the money record must be append-only
GRANT  UPDATE, DELETE ON stock_trading_db.WALLET_TRANSACTION TO   'pf_app'@'%';
SHOW GRANTS FOR 'pf_app'@'%';
REVOKE UPDATE, DELETE ON stock_trading_db.WALLET_TRANSACTION FROM 'pf_app'@'%';

-- 5. pf_report: read-only, and only three objects
GRANT SELECT ON stock_trading_db.v_market_movers      TO 'pf_report'@'%';
GRANT SELECT ON stock_trading_db.v_portfolio_holdings TO 'pf_report'@'%';
GRANT SELECT ON stock_trading_db.MV_SECTOR_SUMMARY    TO 'pf_report'@'%';

-- Checks
SHOW GRANTS FOR 'pf_app'@'%';
SHOW GRANTS FOR 'pf_report'@'%';