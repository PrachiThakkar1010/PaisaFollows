USE stock_trading_db;

-- VIEW (5(c)(vii)): every holding with its LIVE value and profit/loss.
-- Current value is calculated from the latest price, never stored.
CREATE OR REPLACE VIEW v_portfolio_holdings AS
SELECT h.portfolio_id,
       h.instrument_id,
       i.symbol,
       i.name,
       s.sector,
       h.quantity,
       h.average_buy_price,
       ROUND(c.last_traded_price, 2)                                          AS ltp,
       ROUND(c.previous_close, 2)                                             AS previous_close,
       ROUND(h.quantity * h.average_buy_price, 2)                             AS invested,
       ROUND(h.quantity * c.last_traded_price, 2)                             AS current_value,
       ROUND(h.quantity * (c.last_traded_price - h.average_buy_price), 2)     AS unrealised_pnl,
       ROUND((c.last_traded_price - h.average_buy_price) * 100
             / NULLIF(h.average_buy_price, 0), 2)                             AS pnl_pct,
       ROUND(h.quantity * (c.last_traded_price - c.previous_close), 2)        AS day_change
FROM HOLDING h
JOIN INSTRUMENT i                ON i.instrument_id = h.instrument_id
LEFT JOIN STOCK s                ON s.instrument_id = h.instrument_id
LEFT JOIN CURRENT_MARKET_PRICE c ON c.instrument_id = h.instrument_id;

-- FUNCTION (5(c)(viii)): total market value of one portfolio
DROP FUNCTION IF EXISTS fn_portfolio_value;
DELIMITER $$
CREATE FUNCTION fn_portfolio_value(p_portfolio_id BINARY(16))
RETURNS DECIMAL(18,2)
NOT DETERMINISTIC
READS SQL DATA
BEGIN
    DECLARE v_value DECIMAL(18,2);
    SELECT COALESCE(SUM(current_value), 0)
      INTO v_value
    FROM v_portfolio_holdings
    WHERE portfolio_id = p_portfolio_id;
    RETURN v_value;
END $$
DELIMITER ;

-- STORED PROCEDURE (5(c)(viii)): account statement for one wallet between two dates
-- flow: IN = money came in, OUT = money left, HOLD = moved between
--       available and reserved (limit orders)
DROP PROCEDURE IF EXISTS sp_account_statement;
DELIMITER $$
CREATE PROCEDURE sp_account_statement(IN p_wallet_id BINARY(16), IN p_from DATE, IN p_to DATE)
BEGIN
    SELECT transaction_type,
           CASE
               WHEN transaction_type IN ('DEPOSIT', 'TRADE_CREDIT', 'REFUND') THEN 'IN'
               WHEN transaction_type IN ('WITHDRAWAL', 'TRADE_DEBIT')         THEN 'OUT'
               ELSE 'HOLD'
           END AS flow,
           amount,
           description,
           created_at
    FROM WALLET_TRANSACTION
    WHERE wallet_id = p_wallet_id
      AND created_at >= p_from
      AND created_at <  p_to + INTERVAL 1 DAY
    ORDER BY created_at DESC;
END $$
DELIMITER ;

-- TRIGGER (5(c)(viii)): audit log of every wallet balance change
CREATE TABLE IF NOT EXISTS WALLET_AUDIT (
    audit_id       BIGINT        NOT NULL AUTO_INCREMENT,
    wallet_id      BINARY(16)    NOT NULL,
    old_available  DECIMAL(18,2) NOT NULL,
    new_available  DECIMAL(18,2) NOT NULL,
    old_reserved   DECIMAL(18,2) NOT NULL,
    new_reserved   DECIMAL(18,2) NOT NULL,
    changed_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (audit_id),
    KEY idx_wallet_audit_wallet (wallet_id, audit_id),
    CONSTRAINT fk_wallet_audit_wallet FOREIGN KEY (wallet_id) REFERENCES WALLET (wallet_id)
);

DROP TRIGGER IF EXISTS trg_wallet_audit;
DELIMITER $$
CREATE TRIGGER trg_wallet_audit
AFTER UPDATE ON WALLET
FOR EACH ROW
BEGIN
    IF OLD.available_balance <> NEW.available_balance
       OR OLD.reserved_balance <> NEW.reserved_balance THEN
        INSERT INTO WALLET_AUDIT (wallet_id, old_available, new_available, old_reserved, new_reserved)
        VALUES (NEW.wallet_id, OLD.available_balance, NEW.available_balance,
                OLD.reserved_balance, NEW.reserved_balance);
    END IF;
END $$
DELIMITER ;

-- Checks
SELECT symbol, quantity, average_buy_price, ltp, current_value, unrealised_pnl
FROM v_portfolio_holdings
LIMIT 5;

SHOW FUNCTION STATUS WHERE Db = 'stock_trading_db';
SHOW PROCEDURE STATUS WHERE Db = 'stock_trading_db';
SHOW TRIGGERS;