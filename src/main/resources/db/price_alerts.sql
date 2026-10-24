USE stock_trading_db;

DROP TRIGGER IF EXISTS trg_price_alert_fire;
DROP TABLE IF EXISTS PRICE_ALERT;

-- Migration 1 (5(c)(i) DDL): CREATE TABLE with every kind of integrity constraint
CREATE TABLE PRICE_ALERT (
    alert_id      BINARY(16)                  NOT NULL,                  -- NOT NULL
    account_id    BINARY(16)                  NOT NULL,
    symbol        VARCHAR(20)                 NOT NULL,
    direction     ENUM('ABOVE', 'BELOW')      NOT NULL,                  -- domain (fixed list)
    target_price  DECIMAL(10,2)               NOT NULL,
    status        ENUM('ACTIVE', 'TRIGGERED') NOT NULL DEFAULT 'ACTIVE', -- DEFAULT
    created_at    DATETIME                    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_price_alert    PRIMARY KEY (alert_id),                                   -- PRIMARY KEY
    CONSTRAINT uq_price_alert    UNIQUE (account_id, symbol, direction, target_price),    -- UNIQUE
    CONSTRAINT chk_alert_target  CHECK (target_price > 0),                                -- CHECK
    CONSTRAINT fk_alert_account  FOREIGN KEY (account_id)                                 -- REFERENTIAL
        REFERENCES ACCOUNT (account_id) ON DELETE CASCADE,
    CONSTRAINT fk_alert_symbol   FOREIGN KEY (symbol)
        REFERENCES INSTRUMENT (symbol) ON UPDATE CASCADE
);

-- Migration 2 (5(c)(i) DDL): ALTER TABLE - remember WHEN and AT WHAT PRICE an alert
-- fired, and index the columns the trigger searches on
ALTER TABLE PRICE_ALERT
    ADD COLUMN triggered_price DECIMAL(10,2) NULL AFTER status,
    ADD COLUMN triggered_at    DATETIME      NULL AFTER triggered_price,
    ADD INDEX idx_alert_symbol_status (symbol, status);

-- TRIGGER (5(c)(viii)): every time the app saves a new price (every 5 seconds),
-- mark the ACTIVE alerts that this price has reached as TRIGGERED.
-- The app's upsert (INSERT ... ON DUPLICATE KEY UPDATE) fires AFTER UPDATE.
DELIMITER $$
CREATE TRIGGER trg_price_alert_fire
AFTER UPDATE ON CURRENT_MARKET_PRICE
FOR EACH ROW
BEGIN
    UPDATE PRICE_ALERT pa
    JOIN INSTRUMENT i ON i.symbol = pa.symbol
    SET pa.status          = 'TRIGGERED',
        pa.triggered_price = NEW.last_traded_price,
        pa.triggered_at    = NOW()
    WHERE i.instrument_id = NEW.instrument_id
      AND pa.status = 'ACTIVE'
      AND (   (pa.direction = 'ABOVE' AND NEW.last_traded_price >= pa.target_price)
           OR (pa.direction = 'BELOW' AND NEW.last_traded_price <= pa.target_price));
END $$
DELIMITER ;

-- Checks
DESCRIBE PRICE_ALERT;
SHOW TRIGGERS WHERE `Trigger` = 'trg_price_alert_fire';