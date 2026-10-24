DROP TRIGGER IF EXISTS trg_orders_protect_closed;

DELIMITER $$
CREATE TRIGGER trg_orders_protect_closed
BEFORE UPDATE ON orders
FOR EACH ROW
BEGIN
    IF OLD.status <> 'OPEN' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Only OPEN orders can be changed';
    END IF;
END $$
DELIMITER ;