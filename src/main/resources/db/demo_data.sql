-- =====================================================================
-- Creates a stored procedure that signs up N demo users exactly like the
-- app does (USER, ACCOUNT, ACCOUNT_SEGMENT, WALLET, deposit, PORTFOLIO,
-- WATCHLIST), adds 3 watchlist stocks each, and makes 2 market buys each
-- (orders, TRADE, HOLDING, WALLET debit, WALLET_TRANSACTION).
-- Demo users: demo1@paisafollows.test, demo2@..., password: password123
-- (stored as a BCrypt hash, so they can log in to the app too).
--
-- Run with Execute All (lightning bolt). Running it again ADDS more users.
-- =====================================================================

USE stock_trading_db;

DROP PROCEDURE IF EXISTS sp_seed_demo_data;

DELIMITER $$

CREATE PROCEDURE sp_seed_demo_data(IN p_users INT)
BEGIN
    DECLARE v_n          INT DEFAULT 1;
    DECLARE v_buy        INT;
    DECLARE v_offset     INT DEFAULT 0;
    DECLARE v_number     INT;
    DECLARE v_city       INT;
    DECLARE v_user, v_account, v_wallet, v_portfolio, v_watchlist, v_order, v_instrument BINARY(16);
    DECLARE v_dob        DATE;
    DECLARE v_price      DECIMAL(18,2);
    DECLARE v_qty        INT;
    DECLARE v_cost       DECIMAL(18,2);
    DECLARE v_start      DECIMAL(18,2) DEFAULT 100000.00;

    -- If anything fails for a user, undo that user's rows and stop (TCL inside a procedure)
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    IF (SELECT COUNT(*) FROM CURRENT_MARKET_PRICE WHERE last_traded_price IS NOT NULL) = 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'No prices yet: start the app once (Module 6) before generating demo data';
    END IF;

    -- Continue numbering after demo users that already exist
    SELECT COUNT(*) INTO v_offset FROM `USER` WHERE email LIKE 'demo%@paisafollows.test';

    WHILE v_n <= p_users DO
        START TRANSACTION;

        SET v_number    = v_offset + v_n;
        SET v_user      = UUID_TO_BIN(UUID());
        SET v_account   = UUID_TO_BIN(UUID());
        SET v_wallet    = UUID_TO_BIN(UUID());
        SET v_portfolio = UUID_TO_BIN(UUID());
        SET v_watchlist = UUID_TO_BIN(UUID());
        SET v_dob       = DATE_SUB(CURDATE(), INTERVAL (14 + FLOOR(RAND() * 50)) YEAR);   -- ages 14 to 63
        SET v_city      = 1 + FLOOR(RAND() * 6);

        -- 1. The person (PAN pattern: 5 letters, 4 digits, 1 letter, unique per number)
        INSERT INTO `USER` (user_id, name, email, phone, password, date_of_birth, pan, city, state, country, created_at)
        VALUES (v_user,
                CONCAT('Demo User ', v_number),
                CONCAT('demo', v_number, '@paisafollows.test'),
                CONCAT('9', LPAD(FLOOR(RAND() * 1000000000), 9, '0')),
                '$2a$10$NoinEpbeHUR3qwSm3KswEOsBKQmIEeJo7LWHpCwQREY0ezs2jg05i',
                v_dob,
                CONCAT('DEMO', CHAR(65 + MOD(v_number, 26)), LPAD(MOD(v_number, 10000), 4, '0'), 'Z'),
                ELT(v_city, 'Ahmedabad', 'Mumbai', 'Bengaluru', 'New Delhi', 'Pune', 'Gandhinagar'),
                ELT(v_city, 'Gujarat', 'Maharashtra', 'Karnataka', 'Delhi', 'Maharashtra', 'Gujarat'),
                'India',
                NOW() - INTERVAL FLOOR(RAND() * 90) DAY);

        -- 2. Account + segments (minors: EQUITY only, same rule as the app)
        INSERT INTO ACCOUNT (account_id, user_id, status) VALUES (v_account, v_user, 'ACTIVE');
        INSERT INTO ACCOUNT_SEGMENT (account_id, segment) VALUES (v_account, 'EQUITY');
        IF TIMESTAMPDIFF(YEAR, v_dob, CURDATE()) >= 18 THEN
            INSERT INTO ACCOUNT_SEGMENT (account_id, segment) VALUES (v_account, 'COMMODITY'), (v_account, 'F_AND_O');
        END IF;

        -- 3. Wallet + the deposit that explains its money
        INSERT INTO WALLET (wallet_id, account_id, currency, available_balance, reserved_balance)
        VALUES (v_wallet, v_account, 'INR', v_start, 0);
        INSERT INTO WALLET_TRANSACTION (transaction_id, wallet_id, transaction_type, amount, status, description, processed_at)
        VALUES (UUID_TO_BIN(UUID()), v_wallet, 'DEPOSIT', v_start, 'COMPLETED', 'Starting virtual balance', NOW());

        -- 4. Portfolio and watchlist with 3 random stocks
        INSERT INTO PORTFOLIO (portfolio_id, account_id, name) VALUES (v_portfolio, v_account, 'Main');
        INSERT INTO WATCHLIST (watchlist_id, account_id, name) VALUES (v_watchlist, v_account, 'My watchlist');
        INSERT INTO WATCHLIST_ITEM (watchlist_item_id, watchlist_id, instrument_id)
        SELECT UUID_TO_BIN(UUID()), v_watchlist, instrument_id
        FROM INSTRUMENT
        WHERE instrument_type = 'STOCK' AND status = 'ACTIVE'
        ORDER BY RAND()
        LIMIT 3;

        -- 5. Two market buys at the current price
        SET v_buy = 1;
        WHILE v_buy <= 2 DO
            SELECT c.instrument_id, ROUND(c.last_traded_price, 2)
              INTO v_instrument, v_price
            FROM CURRENT_MARKET_PRICE c
            JOIN INSTRUMENT inst ON inst.instrument_id = c.instrument_id
            WHERE inst.status = 'ACTIVE' AND c.last_traded_price IS NOT NULL
            ORDER BY RAND()
            LIMIT 1;

            SET v_qty  = 1 + FLOOR(RAND() * 10);
            SET v_cost = v_qty * v_price;

            IF v_cost <= (SELECT available_balance FROM WALLET WHERE wallet_id = v_wallet) THEN
                SET v_order = UUID_TO_BIN(UUID());

                INSERT INTO orders (order_id, account_id, portfolio_id, instrument_id, order_type, transaction_type,
                                    quantity, price, status, fill_price, filled_at)
                VALUES (v_order, v_account, v_portfolio, v_instrument, 'MARKET', 'BUY',
                        v_qty, NULL, 'FILLED', v_price, NOW());

                INSERT INTO TRADE (trade_id, buy_order_id, sell_order_id, instrument_id, quantity, execution_price)
                VALUES (UUID_TO_BIN(UUID()), v_order, NULL, v_instrument, v_qty, v_price);

                -- New stock: insert. Same stock bought again: new weighted average, then add quantity.
                INSERT INTO HOLDING (holding_id, portfolio_id, instrument_id, quantity, average_buy_price)
                VALUES (UUID_TO_BIN(UUID()), v_portfolio, v_instrument, v_qty, v_price) AS new
                ON DUPLICATE KEY UPDATE
                    average_buy_price = ROUND((HOLDING.quantity * HOLDING.average_buy_price
                                               + new.quantity * new.average_buy_price)
                                              / (HOLDING.quantity + new.quantity), 2),
                    quantity = HOLDING.quantity + new.quantity;

                UPDATE WALLET SET available_balance = available_balance - v_cost WHERE wallet_id = v_wallet;

                INSERT INTO WALLET_TRANSACTION (transaction_id, wallet_id, transaction_type, amount, status,
                                                reference_id, description, processed_at)
                VALUES (UUID_TO_BIN(UUID()), v_wallet, 'TRADE_DEBIT', v_cost, 'COMPLETED',
                        v_order, CONCAT('Bought ', v_qty, ' shares'), NOW());
            END IF;

            SET v_buy = v_buy + 1;
        END WHILE;

        COMMIT;
        SET v_n = v_n + 1;
    END WHILE;
END $$

DELIMITER ;

-- Create 100 demo users (about 1,500 rows across 11 tables)
CALL sp_seed_demo_data(100);

-- Check: how many rows each table now has
SELECT 'USER' AS table_name, COUNT(*) AS row_count FROM `USER`
UNION ALL SELECT 'ACCOUNT',            COUNT(*) FROM ACCOUNT
UNION ALL SELECT 'ACCOUNT_SEGMENT',    COUNT(*) FROM ACCOUNT_SEGMENT
UNION ALL SELECT 'WALLET',             COUNT(*) FROM WALLET
UNION ALL SELECT 'WALLET_TRANSACTION', COUNT(*) FROM WALLET_TRANSACTION
UNION ALL SELECT 'PORTFOLIO',          COUNT(*) FROM PORTFOLIO
UNION ALL SELECT 'WATCHLIST',          COUNT(*) FROM WATCHLIST
UNION ALL SELECT 'WATCHLIST_ITEM',     COUNT(*) FROM WATCHLIST_ITEM
UNION ALL SELECT 'orders',             COUNT(*) FROM orders
UNION ALL SELECT 'TRADE',              COUNT(*) FROM TRADE
UNION ALL SELECT 'HOLDING',            COUNT(*) FROM HOLDING;