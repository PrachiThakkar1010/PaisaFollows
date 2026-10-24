USE stock_trading_db;

-- DDL (5(c)(i)): change the foreign key so deleting a watchlist deletes its items
ALTER TABLE WATCHLIST_ITEM DROP FOREIGN KEY WATCHLIST_ITEM_ibfk_1;

ALTER TABLE WATCHLIST_ITEM
    ADD CONSTRAINT WATCHLIST_ITEM_ibfk_1
    FOREIGN KEY (watchlist_id) REFERENCES WATCHLIST (watchlist_id)
    ON DELETE CASCADE;

-- Check: DELETE_RULE should now be CASCADE
SELECT constraint_name, table_name, referenced_table_name, delete_rule
FROM information_schema.referential_constraints
WHERE constraint_schema = 'stock_trading_db'
  AND table_name = 'WATCHLIST_ITEM';