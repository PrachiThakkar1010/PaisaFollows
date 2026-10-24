-- MySQL dump 10.13  Distrib 8.0.46, for Win64 (x86_64)
--
-- Host: mysql-20bb8841-paisafollows-3357.k.aivencloud.com    Database: stock_trading_db
-- ------------------------------------------------------
-- Server version	8.4.8

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
SET @MYSQLDUMP_TEMP_LOG_BIN = @@SESSION.SQL_LOG_BIN;
SET @@SESSION.SQL_LOG_BIN= 0;

--
-- GTID state at the beginning of the backup 
--

SET @@GLOBAL.GTID_PURGED=/*!80000 '+'*/ '03107873-cd13-11f1-b0f5-ce39e1fbfce7:1-44,
41e14f81-c33d-11f1-be7b-b6171ffe4403:1-38,
e054d4f1-cf99-11f1-9eb2-060861e716c5:1-34';

--
-- Table structure for table `ACCOUNT`
--

DROP TABLE IF EXISTS `ACCOUNT`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACCOUNT` (
  `account_id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `status` enum('ACTIVE','SUSPENDED','CLOSED') NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`account_id`),
  KEY `user_id` (`user_id`),
  CONSTRAINT `ACCOUNT_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `USER` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ACCOUNT_SEGMENT`
--

DROP TABLE IF EXISTS `ACCOUNT_SEGMENT`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ACCOUNT_SEGMENT` (
  `account_id` binary(16) NOT NULL,
  `segment` enum('EQUITY','COMMODITY','F_AND_O') NOT NULL,
  PRIMARY KEY (`account_id`,`segment`),
  CONSTRAINT `ACCOUNT_SEGMENT_ibfk_1` FOREIGN KEY (`account_id`) REFERENCES `ACCOUNT` (`account_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `COMMODITY`
--

DROP TABLE IF EXISTS `COMMODITY`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `COMMODITY` (
  `instrument_id` binary(16) NOT NULL,
  `commodity_name` varchar(50) NOT NULL,
  `commodity_type` varchar(30) NOT NULL,
  `unit` varchar(20) NOT NULL,
  `lot_size` decimal(12,6) DEFAULT NULL,
  PRIMARY KEY (`instrument_id`),
  CONSTRAINT `COMMODITY_ibfk_1` FOREIGN KEY (`instrument_id`) REFERENCES `INSTRUMENT` (`instrument_id`),
  CONSTRAINT `COMMODITY_chk_1` CHECK ((`lot_size` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `COMPANY`
--

DROP TABLE IF EXISTS `COMPANY`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `COMPANY` (
  `company_id` binary(16) NOT NULL,
  `gst_no` varchar(15) NOT NULL,
  `company_name` varchar(100) NOT NULL,
  `sector` varchar(50) NOT NULL,
  PRIMARY KEY (`company_id`),
  UNIQUE KEY `gst_no` (`gst_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `CURRENT_MARKET_PRICE`
--

DROP TABLE IF EXISTS `CURRENT_MARKET_PRICE`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `CURRENT_MARKET_PRICE` (
  `instrument_id` binary(16) NOT NULL,
  `last_traded_price` decimal(18,6) DEFAULT NULL,
  `bid_price` decimal(18,6) DEFAULT NULL,
  `ask_price` decimal(18,6) DEFAULT NULL,
  `open_price` decimal(18,6) DEFAULT NULL,
  `high_price` decimal(18,6) DEFAULT NULL,
  `low_price` decimal(18,6) DEFAULT NULL,
  `previous_close` decimal(18,6) DEFAULT NULL,
  `volume` bigint DEFAULT NULL,
  `exchange_timestamp` datetime DEFAULT NULL,
  `received_at` datetime NOT NULL,
  PRIMARY KEY (`instrument_id`),
  CONSTRAINT `CURRENT_MARKET_PRICE_ibfk_1` FOREIGN KEY (`instrument_id`) REFERENCES `INSTRUMENT` (`instrument_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ETF`
--

DROP TABLE IF EXISTS `ETF`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ETF` (
  `instrument_id` binary(16) NOT NULL,
  `fund_house` varchar(50) NOT NULL,
  `underlying_index_id` binary(16) DEFAULT NULL,
  `expense_ratio` decimal(12,6) DEFAULT NULL,
  PRIMARY KEY (`instrument_id`),
  KEY `underlying_index_id` (`underlying_index_id`),
  CONSTRAINT `ETF_ibfk_1` FOREIGN KEY (`instrument_id`) REFERENCES `INSTRUMENT` (`instrument_id`),
  CONSTRAINT `ETF_ibfk_2` FOREIGN KEY (`underlying_index_id`) REFERENCES `INSTRUMENT` (`instrument_id`),
  CONSTRAINT `ETF_chk_1` CHECK ((`expense_ratio` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `FUTURE`
--

DROP TABLE IF EXISTS `FUTURE`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `FUTURE` (
  `instrument_id` binary(16) NOT NULL,
  `underlying_instrument_id` binary(16) DEFAULT NULL,
  `expiry_date` date NOT NULL,
  `strike_price` decimal(12,6) DEFAULT NULL,
  `lot_size` decimal(12,6) DEFAULT NULL,
  `contract_size` decimal(12,6) DEFAULT NULL,
  PRIMARY KEY (`instrument_id`),
  KEY `underlying_instrument_id` (`underlying_instrument_id`),
  CONSTRAINT `FUTURE_ibfk_1` FOREIGN KEY (`instrument_id`) REFERENCES `INSTRUMENT` (`instrument_id`),
  CONSTRAINT `FUTURE_ibfk_2` FOREIGN KEY (`underlying_instrument_id`) REFERENCES `INSTRUMENT` (`instrument_id`),
  CONSTRAINT `FUTURE_chk_1` CHECK ((`strike_price` >= 0)),
  CONSTRAINT `FUTURE_chk_2` CHECK ((`lot_size` > 0)),
  CONSTRAINT `FUTURE_chk_3` CHECK ((`contract_size` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `HOLDING`
--

DROP TABLE IF EXISTS `HOLDING`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `HOLDING` (
  `holding_id` binary(16) NOT NULL,
  `portfolio_id` binary(16) NOT NULL,
  `instrument_id` binary(16) NOT NULL,
  `quantity` int NOT NULL,
  `average_buy_price` decimal(15,2) NOT NULL,
  PRIMARY KEY (`holding_id`),
  UNIQUE KEY `portfolio_id` (`portfolio_id`,`instrument_id`),
  KEY `instrument_id` (`instrument_id`),
  CONSTRAINT `HOLDING_ibfk_1` FOREIGN KEY (`portfolio_id`) REFERENCES `PORTFOLIO` (`portfolio_id`),
  CONSTRAINT `HOLDING_ibfk_2` FOREIGN KEY (`instrument_id`) REFERENCES `INSTRUMENT` (`instrument_id`),
  CONSTRAINT `HOLDING_chk_1` CHECK ((`quantity` > 0)),
  CONSTRAINT `HOLDING_chk_2` CHECK ((`average_buy_price` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `INDEX`
--

DROP TABLE IF EXISTS `INDEX`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `INDEX` (
  `instrument_id` binary(16) NOT NULL,
  `index_name` varchar(50) NOT NULL,
  `base_value` decimal(12,6) DEFAULT NULL,
  PRIMARY KEY (`instrument_id`),
  CONSTRAINT `INDEX_ibfk_1` FOREIGN KEY (`instrument_id`) REFERENCES `INSTRUMENT` (`instrument_id`),
  CONSTRAINT `INDEX_chk_1` CHECK ((`base_value` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `INSTRUMENT`
--

DROP TABLE IF EXISTS `INSTRUMENT`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `INSTRUMENT` (
  `instrument_id` binary(16) NOT NULL,
  `symbol` varchar(20) NOT NULL,
  `name` varchar(150) NOT NULL,
  `instrument_type` enum('STOCK','INDEX','ETF','MF','COMMODITY','FUTURE','OPTION','IPO') NOT NULL,
  `exchange` varchar(20) DEFAULT NULL,
  `status` enum('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`instrument_id`),
  UNIQUE KEY `symbol` (`symbol`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `IPO`
--

DROP TABLE IF EXISTS `IPO`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `IPO` (
  `ipo_id` binary(16) NOT NULL,
  `company_id` binary(16) NOT NULL,
  `ipo_type` enum('FIXED_PRICE','BOOK_BUILT') NOT NULL,
  `fixed_price` decimal(10,2) DEFAULT NULL,
  `lower_price` decimal(10,2) DEFAULT NULL,
  `upper_price` decimal(10,2) DEFAULT NULL,
  `open_date` date NOT NULL,
  `close_date` date NOT NULL,
  `listing_date` date NOT NULL,
  `total_shares` int NOT NULL,
  `minimum_lot` int NOT NULL,
  `status` enum('UPCOMING','OPEN','CLOSED','LISTED') NOT NULL,
  PRIMARY KEY (`ipo_id`),
  KEY `company_id` (`company_id`),
  CONSTRAINT `IPO_ibfk_1` FOREIGN KEY (`company_id`) REFERENCES `COMPANY` (`company_id`),
  CONSTRAINT `IPO_chk_1` CHECK ((`total_shares` > 0)),
  CONSTRAINT `IPO_chk_2` CHECK ((`minimum_lot` > 0)),
  CONSTRAINT `IPO_chk_3` CHECK ((`close_date` >= `open_date`)),
  CONSTRAINT `IPO_chk_4` CHECK (((`fixed_price` is null) or (`fixed_price` > 0))),
  CONSTRAINT `IPO_chk_5` CHECK (((`lower_price` is null) or (`lower_price` > 0))),
  CONSTRAINT `IPO_chk_6` CHECK (((`upper_price` is null) or (`upper_price` >= `lower_price`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `IPO_APPLICATION`
--

DROP TABLE IF EXISTS `IPO_APPLICATION`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `IPO_APPLICATION` (
  `application_id` binary(16) NOT NULL,
  `ipo_id` binary(16) NOT NULL,
  `account_id` binary(16) NOT NULL,
  `quantity` int NOT NULL,
  `bid_price` decimal(10,2) DEFAULT NULL,
  `application_date` date NOT NULL,
  `status` enum('PENDING','ALLOTTED','PARTIALLY_ALLOTTED','NOT_ALLOTTED','CANCELLED') NOT NULL,
  `allotted_quantity` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`application_id`),
  KEY `ipo_id` (`ipo_id`),
  KEY `account_id` (`account_id`),
  CONSTRAINT `IPO_APPLICATION_ibfk_1` FOREIGN KEY (`ipo_id`) REFERENCES `IPO` (`ipo_id`),
  CONSTRAINT `IPO_APPLICATION_ibfk_2` FOREIGN KEY (`account_id`) REFERENCES `ACCOUNT` (`account_id`),
  CONSTRAINT `IPO_APPLICATION_chk_1` CHECK ((`quantity` > 0)),
  CONSTRAINT `IPO_APPLICATION_chk_2` CHECK ((`allotted_quantity` >= 0)),
  CONSTRAINT `IPO_APPLICATION_chk_3` CHECK (((`bid_price` is null) or (`bid_price` > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `MARKET_PRICE_HISTORY`
--

DROP TABLE IF EXISTS `MARKET_PRICE_HISTORY`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `MARKET_PRICE_HISTORY` (
  `price_update_id` binary(16) NOT NULL,
  `instrument_id` binary(16) NOT NULL,
  `last_traded_price` decimal(18,6) DEFAULT NULL,
  `bid_price` decimal(18,6) DEFAULT NULL,
  `ask_price` decimal(18,6) DEFAULT NULL,
  `trade_quantity` bigint DEFAULT NULL,
  `cumulative_volume` bigint DEFAULT NULL,
  `exchange_timestamp` datetime NOT NULL,
  `received_at` datetime NOT NULL,
  `event_type` enum('TRADE','QUOTE','SNAPSHOT') NOT NULL,
  PRIMARY KEY (`price_update_id`),
  KEY `idx_price_history_instrument_time` (`instrument_id`,`exchange_timestamp`),
  CONSTRAINT `MARKET_PRICE_HISTORY_ibfk_1` FOREIGN KEY (`instrument_id`) REFERENCES `INSTRUMENT` (`instrument_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `MUTUAL_FUND`
--

DROP TABLE IF EXISTS `MUTUAL_FUND`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `MUTUAL_FUND` (
  `instrument_id` binary(16) NOT NULL,
  `fund_house` varchar(50) NOT NULL,
  `category` enum('EQUITY','DEBT','HYBRID','INDEX','LIQUID') DEFAULT NULL,
  `nav` decimal(12,6) DEFAULT NULL,
  `expense_ratio` decimal(12,6) DEFAULT NULL,
  PRIMARY KEY (`instrument_id`),
  CONSTRAINT `MUTUAL_FUND_ibfk_1` FOREIGN KEY (`instrument_id`) REFERENCES `INSTRUMENT` (`instrument_id`),
  CONSTRAINT `MUTUAL_FUND_chk_1` CHECK ((`nav` >= 0)),
  CONSTRAINT `MUTUAL_FUND_chk_2` CHECK ((`expense_ratio` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `NEWS`
--

DROP TABLE IF EXISTS `NEWS`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `NEWS` (
  `news_id` binary(16) NOT NULL,
  `title` varchar(255) NOT NULL,
  `description` text,
  `source` varchar(100) DEFAULT NULL,
  `article_url` varchar(2048) NOT NULL,
  `published_at` datetime NOT NULL,
  `fetched_at` datetime NOT NULL,
  PRIMARY KEY (`news_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `NEWS_INSTRUMENT`
--

DROP TABLE IF EXISTS `NEWS_INSTRUMENT`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `NEWS_INSTRUMENT` (
  `news_id` binary(16) NOT NULL,
  `instrument_id` binary(16) NOT NULL,
  PRIMARY KEY (`news_id`,`instrument_id`),
  KEY `instrument_id` (`instrument_id`),
  CONSTRAINT `NEWS_INSTRUMENT_ibfk_1` FOREIGN KEY (`news_id`) REFERENCES `NEWS` (`news_id`),
  CONSTRAINT `NEWS_INSTRUMENT_ibfk_2` FOREIGN KEY (`instrument_id`) REFERENCES `INSTRUMENT` (`instrument_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `OPTION_CONTRACT`
--

DROP TABLE IF EXISTS `OPTION_CONTRACT`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `OPTION_CONTRACT` (
  `instrument_id` binary(16) NOT NULL,
  `underlying_instrument_id` binary(16) DEFAULT NULL,
  `option_type` enum('CALL','PUT') NOT NULL,
  `strike_price` decimal(12,6) NOT NULL,
  `expiry_date` date NOT NULL,
  `lot_size` decimal(12,6) DEFAULT NULL,
  PRIMARY KEY (`instrument_id`),
  KEY `underlying_instrument_id` (`underlying_instrument_id`),
  CONSTRAINT `OPTION_CONTRACT_ibfk_1` FOREIGN KEY (`instrument_id`) REFERENCES `INSTRUMENT` (`instrument_id`),
  CONSTRAINT `OPTION_CONTRACT_ibfk_2` FOREIGN KEY (`underlying_instrument_id`) REFERENCES `INSTRUMENT` (`instrument_id`),
  CONSTRAINT `OPTION_CONTRACT_chk_1` CHECK ((`strike_price` > 0)),
  CONSTRAINT `OPTION_CONTRACT_chk_2` CHECK ((`lot_size` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `ORDER_BOOK`
--

DROP TABLE IF EXISTS `ORDER_BOOK`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ORDER_BOOK` (
  `order_book_id` binary(16) NOT NULL,
  `order_id` binary(16) NOT NULL,
  `instrument_id` binary(16) NOT NULL,
  `side` enum('BUY','SELL') NOT NULL,
  `quantity` int NOT NULL,
  `price` decimal(10,2) NOT NULL,
  `timestamp` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`order_book_id`),
  UNIQUE KEY `order_id` (`order_id`),
  KEY `instrument_id` (`instrument_id`),
  CONSTRAINT `ORDER_BOOK_ibfk_1` FOREIGN KEY (`order_id`) REFERENCES `orders` (`order_id`),
  CONSTRAINT `ORDER_BOOK_ibfk_2` FOREIGN KEY (`instrument_id`) REFERENCES `INSTRUMENT` (`instrument_id`),
  CONSTRAINT `ORDER_BOOK_chk_1` CHECK ((`quantity` > 0)),
  CONSTRAINT `ORDER_BOOK_chk_2` CHECK ((`price` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `PAYMENT`
--

DROP TABLE IF EXISTS `PAYMENT`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `PAYMENT` (
  `payment_id` binary(16) NOT NULL,
  `account_id` binary(16) NOT NULL,
  `amount` decimal(18,2) NOT NULL,
  `payment_type` enum('DEPOSIT','WITHDRAWAL') NOT NULL,
  `payment_status` enum('PENDING','SUCCESS','FAILED','CANCELLED') NOT NULL,
  `payment_date` datetime NOT NULL,
  `transaction_ref` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`payment_id`),
  KEY `account_id` (`account_id`),
  CONSTRAINT `PAYMENT_ibfk_1` FOREIGN KEY (`account_id`) REFERENCES `ACCOUNT` (`account_id`),
  CONSTRAINT `PAYMENT_chk_1` CHECK ((`amount` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `PORTFOLIO`
--

DROP TABLE IF EXISTS `PORTFOLIO`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `PORTFOLIO` (
  `portfolio_id` binary(16) NOT NULL,
  `account_id` binary(16) NOT NULL,
  `name` varchar(100) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`portfolio_id`),
  KEY `account_id` (`account_id`),
  CONSTRAINT `PORTFOLIO_ibfk_1` FOREIGN KEY (`account_id`) REFERENCES `ACCOUNT` (`account_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `SIP`
--

DROP TABLE IF EXISTS `SIP`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `SIP` (
  `sip_id` binary(16) NOT NULL,
  `account_id` binary(16) NOT NULL,
  `mutual_fund_id` binary(16) NOT NULL,
  `amount` decimal(15,2) NOT NULL,
  `frequency` enum('DAILY','WEEKLY','MONTHLY','QUARTERLY') NOT NULL,
  `start_date` date NOT NULL,
  `next_execution_date` date NOT NULL,
  `status` enum('ACTIVE','PAUSED','COMPLETED','CANCELLED') NOT NULL DEFAULT 'ACTIVE',
  PRIMARY KEY (`sip_id`),
  KEY `account_id` (`account_id`),
  KEY `mutual_fund_id` (`mutual_fund_id`),
  CONSTRAINT `SIP_ibfk_1` FOREIGN KEY (`account_id`) REFERENCES `ACCOUNT` (`account_id`),
  CONSTRAINT `SIP_ibfk_2` FOREIGN KEY (`mutual_fund_id`) REFERENCES `MUTUAL_FUND` (`instrument_id`),
  CONSTRAINT `SIP_chk_1` CHECK ((`amount` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `STOCK`
--

DROP TABLE IF EXISTS `STOCK`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `STOCK` (
  `instrument_id` binary(16) NOT NULL,
  `company_name` varchar(150) NOT NULL,
  `sector` varchar(50) DEFAULT NULL,
  `face_value` decimal(12,6) NOT NULL,
  PRIMARY KEY (`instrument_id`),
  CONSTRAINT `STOCK_ibfk_1` FOREIGN KEY (`instrument_id`) REFERENCES `INSTRUMENT` (`instrument_id`),
  CONSTRAINT `STOCK_chk_1` CHECK ((`face_value` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `TRADE`
--

DROP TABLE IF EXISTS `TRADE`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `TRADE` (
  `trade_id` binary(16) NOT NULL,
  `buy_order_id` binary(16) DEFAULT NULL,
  `sell_order_id` binary(16) DEFAULT NULL,
  `instrument_id` binary(16) NOT NULL,
  `quantity` int NOT NULL,
  `execution_price` decimal(10,2) NOT NULL,
  `trade_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `realised_pnl` decimal(18,2) DEFAULT NULL,
  PRIMARY KEY (`trade_id`),
  KEY `buy_order_id` (`buy_order_id`),
  KEY `sell_order_id` (`sell_order_id`),
  KEY `instrument_id` (`instrument_id`),
  CONSTRAINT `TRADE_ibfk_1` FOREIGN KEY (`buy_order_id`) REFERENCES `orders` (`order_id`),
  CONSTRAINT `TRADE_ibfk_2` FOREIGN KEY (`sell_order_id`) REFERENCES `orders` (`order_id`),
  CONSTRAINT `TRADE_ibfk_3` FOREIGN KEY (`instrument_id`) REFERENCES `INSTRUMENT` (`instrument_id`),
  CONSTRAINT `TRADE_chk_1` CHECK ((`quantity` > 0)),
  CONSTRAINT `TRADE_chk_2` CHECK ((`execution_price` >= 0)),
  CONSTRAINT `TRADE_chk_3` CHECK (((`buy_order_id` is null) <> (`sell_order_id` is null)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `USER`
--

DROP TABLE IF EXISTS `USER`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `USER` (
  `user_id` binary(16) NOT NULL,
  `name` varchar(100) NOT NULL,
  `email` varchar(255) NOT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `password` varchar(255) NOT NULL,
  `date_of_birth` date NOT NULL,
  `pan` char(10) NOT NULL,
  `street` varchar(150) DEFAULT NULL,
  `city` varchar(100) DEFAULT NULL,
  `state` varchar(100) DEFAULT NULL,
  `pin_code` varchar(10) DEFAULT NULL,
  `country` varchar(100) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uq_user_email` (`email`),
  UNIQUE KEY `uq_user_pan` (`pan`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `WALLET`
--

DROP TABLE IF EXISTS `WALLET`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `WALLET` (
  `wallet_id` binary(16) NOT NULL,
  `account_id` binary(16) NOT NULL,
  `currency` char(3) NOT NULL,
  `available_balance` decimal(18,2) NOT NULL DEFAULT '0.00',
  `reserved_balance` decimal(18,2) NOT NULL DEFAULT '0.00',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`wallet_id`),
  UNIQUE KEY `account_id` (`account_id`,`currency`),
  CONSTRAINT `WALLET_ibfk_1` FOREIGN KEY (`account_id`) REFERENCES `ACCOUNT` (`account_id`),
  CONSTRAINT `WALLET_chk_1` CHECK ((`available_balance` >= 0)),
  CONSTRAINT `WALLET_chk_2` CHECK ((`reserved_balance` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `WALLET_TRANSACTION`
--

DROP TABLE IF EXISTS `WALLET_TRANSACTION`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `WALLET_TRANSACTION` (
  `transaction_id` binary(16) NOT NULL,
  `wallet_id` binary(16) NOT NULL,
  `transaction_type` enum('DEPOSIT','WITHDRAWAL','ORDER_RESERVE','ORDER_RELEASE','TRADE_DEBIT','TRADE_CREDIT','REFUND','ADJUSTMENT') NOT NULL,
  `amount` decimal(18,2) NOT NULL,
  `status` enum('PENDING','COMPLETED','FAILED','REVERSED') NOT NULL,
  `reference_id` binary(16) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `processed_at` datetime DEFAULT NULL,
  PRIMARY KEY (`transaction_id`),
  KEY `wallet_id` (`wallet_id`),
  CONSTRAINT `WALLET_TRANSACTION_ibfk_1` FOREIGN KEY (`wallet_id`) REFERENCES `WALLET` (`wallet_id`),
  CONSTRAINT `WALLET_TRANSACTION_chk_1` CHECK ((`amount` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `WATCHLIST`
--

DROP TABLE IF EXISTS `WATCHLIST`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `WATCHLIST` (
  `watchlist_id` binary(16) NOT NULL,
  `account_id` binary(16) NOT NULL,
  `name` varchar(100) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`watchlist_id`),
  KEY `account_id` (`account_id`),
  CONSTRAINT `WATCHLIST_ibfk_1` FOREIGN KEY (`account_id`) REFERENCES `ACCOUNT` (`account_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `WATCHLIST_ITEM`
--

DROP TABLE IF EXISTS `WATCHLIST_ITEM`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `WATCHLIST_ITEM` (
  `watchlist_item_id` binary(16) NOT NULL,
  `watchlist_id` binary(16) NOT NULL,
  `instrument_id` binary(16) NOT NULL,
  `added_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`watchlist_item_id`),
  UNIQUE KEY `watchlist_id` (`watchlist_id`,`instrument_id`),
  KEY `instrument_id` (`instrument_id`),
  CONSTRAINT `WATCHLIST_ITEM_ibfk_1` FOREIGN KEY (`watchlist_id`) REFERENCES `WATCHLIST` (`watchlist_id`),
  CONSTRAINT `WATCHLIST_ITEM_ibfk_2` FOREIGN KEY (`instrument_id`) REFERENCES `INSTRUMENT` (`instrument_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `orders`
--

DROP TABLE IF EXISTS `orders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `orders` (
  `order_id` binary(16) NOT NULL,
  `account_id` binary(16) NOT NULL,
  `portfolio_id` binary(16) NOT NULL,
  `instrument_id` binary(16) NOT NULL,
  `order_type` enum('MARKET','LIMIT','STOP_LOSS') NOT NULL,
  `transaction_type` enum('BUY','SELL') NOT NULL,
  `quantity` int NOT NULL,
  `price` decimal(10,2) DEFAULT NULL,
  `status` varchar(20) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `trigger_price` decimal(10,2) DEFAULT NULL,
  `fill_price` decimal(10,2) DEFAULT NULL,
  `filled_at` datetime DEFAULT NULL,
  `reject_reason` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`order_id`),
  KEY `instrument_id` (`instrument_id`),
  KEY `portfolio_id` (`portfolio_id`),
  KEY `idx_orders_account_status` (`account_id`,`status`),
  CONSTRAINT `orders_ibfk_1` FOREIGN KEY (`account_id`) REFERENCES `ACCOUNT` (`account_id`),
  CONSTRAINT `orders_ibfk_2` FOREIGN KEY (`instrument_id`) REFERENCES `INSTRUMENT` (`instrument_id`),
  CONSTRAINT `orders_ibfk_3` FOREIGN KEY (`portfolio_id`) REFERENCES `PORTFOLIO` (`portfolio_id`),
  CONSTRAINT `orders_chk_1` CHECK ((`quantity` > 0)),
  CONSTRAINT `orders_chk_2` CHECK ((`price` >= 0)),
  CONSTRAINT `orders_chk_3` CHECK ((`status` in (_utf8mb4'OPEN',_utf8mb4'FILLED',_utf8mb4'CANCELLED',_utf8mb4'REJECTED',_utf8mb4'EXPIRED'))),
  CONSTRAINT `orders_chk_4` CHECK ((`order_type` in (_utf8mb4'MARKET',_utf8mb4'LIMIT',_utf8mb4'STOP_LOSS'))),
  CONSTRAINT `orders_chk_5` CHECK ((`transaction_type` in (_utf8mb4'BUY',_utf8mb4'SELL')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
SET @@SESSION.SQL_LOG_BIN = @MYSQLDUMP_TEMP_LOG_BIN;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-24 21:59:27
