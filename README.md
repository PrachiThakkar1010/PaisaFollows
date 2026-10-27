# PaisaFollows

**A live stock paper-trading platform for the Indian market.** Trade the Nifty 50 with prices that move every second, using ₹1,00,000 of practice money. It behaves like a real broker (market, limit and stop-loss orders, a live portfolio, watchlists and price alerts), but no real money is ever involved.

Built as a DBMS course project: every SQL concept in the syllabus (DDL, DML, DCL, TCL, joins, set operators, subqueries, views, procedures, functions, triggers) is used by a real feature of the app, not kept in a separate script.

---

## Features

| Area | What you can do |
| --- | --- |
| **Accounts** | Sign up with PAN and date of birth; under-18 users get equity only, adults also get commodity and F&O segments. Every account starts with ₹1,00,000. |
| **Live market** | 50 Nifty stocks with prices that tick every second, a scrolling ticker, top gainers/losers, market breadth and a sector heatmap |
| **Charts** | Candlestick charts (1m, 5m, 15m, 1h, 1D) built from price history, with the newest candle moving live |
| **Trading** | Market, limit and stop-loss orders; cancel open orders; a **basket order** that buys several stocks at once and rolls back only the ones that fail |
| **Notifications** | A pop-up the moment a waiting order fills, is cancelled or expires (15:30 IST) |
| **Portfolio** | Holdings valued at live prices, profit/loss, sector allocation, an account statement and a balance history |
| **Watchlists** | Several watchlists, "watched vs owned" comparisons, an activity timeline and stock ideas |
| **Price alerts** | "Tell me when RELIANCE goes above ₹1,500", checked by the database itself on every price update |
| **Leaderboard** | Investors ranked by total value and return |
| **Admin** | Rename a stock symbol, delist or re-list a stock, archive or reset price history |
| **Light / dark mode** | Follows your computer's setting; switch any time from the header |

---

## Tech stack

| Layer | Technology |
| --- | --- |
| Backend | Java 21, Spring Boot 4 (Web MVC, JDBC, Security, WebSocket, Validation) |
| Database access | Plain SQL with Spring `JdbcTemplate` (no ORM), so every query is visible |
| Database | MySQL 8.4, hosted on [Aiven](https://aiven.io) |
| Live updates | WebSocket with STOMP (`/topic/ticks`, `/user/queue/orders`) |
| Frontend | Plain HTML, CSS and JavaScript (ES modules), served by Spring Boot; no build step |
| Libraries (CDN) | [TradingView Lightweight Charts](https://github.com/tradingview/lightweight-charts) 5.2.1, [@stomp/stompjs](https://github.com/stomp-js/stompjs) 7.3.0 |
| Prices | A simulator moves each stock about 0.1% per tick, starting from the last saved price or the real NSE close (Yahoo Finance) |

---

## How it works

```
Browser (HTML/CSS/JS)
   │  REST  /api/...        (session cookie login)
   │  WebSocket  /ws        (live prices, order notifications)
   ▼
Spring Boot (port 8080)
   ├── SimulatedProvider   new price for every stock, every second
   ├── PriceCache          latest prices in memory → WebSocket + MatchingEngine
   ├── MatchingEngine      fills waiting limit / stop-loss orders when the price reaches them
   ├── PriceWriter         saves prices every 5 s, price history every 10 s
   └── DAOs (JdbcTemplate) all SQL, one named constant per query
   ▼
MySQL on Aiven (stock_trading_db)
   tables · views · stored procedures · functions · triggers
```

Every trade runs inside one database transaction with row locks (`SELECT … FOR UPDATE`), so money can never be spent twice. Money is never stored twice either: wallet balance always equals deposits − debits + credits in `WALLET_TRANSACTION`.

---

## Project structure

```
PaisaFollows/
├── pom.xml
├── request.http                         92 API tests (VS Code REST Client)
└── src/main/
    ├── java/com/paisafollows/
    │   ├── auth/        sign-up, login, session user
    │   ├── market/      prices, quotes, screener, candles, sector summary
    │   │   └── provider/   price simulator, Yahoo start prices
    │   ├── trading/     orders, basket, matching engine, notifications
    │   │   └── dao/        wallet, order, holding, trade SQL
    │   ├── portfolio/   holdings, statement, balance history
    │   ├── watchlist/   watchlists, "my stocks"
    │   ├── alert/       price alerts
    │   ├── admin/       admin tools, leaderboard
    │   ├── config/      security, WebSocket, scheduling
    │   └── common/      shared types and error handling
    └── resources/
        ├── application.properties
        ├── db/          all SQL scripts (see "Database setup")
        └── static/      the website
            ├── *.html          one file per page
            ├── css/style.css   all styles, light and dark theme
            └── js/             api.js, live.js, layout.js, theme.js, chart.js, pages/*.js
```

---

## Getting started

### Prerequisites

- **Java 21** (JDK)
- **VS Code** with the *Extension Pack for Java* (or IntelliJ IDEA)
- **MySQL 8.x**: an Aiven MySQL service (free plan) or a local MySQL server
- **MySQL Workbench** to run the SQL scripts
- Optional: the *REST Client* VS Code extension, to run `request.http`

### 1. Database setup

Create a database called `stock_trading_db`, then run the scripts in `src/main/resources/db/` **in this order**, in MySQL Workbench (open the file, then *Execute All* ⚡):

| # | Script | What it creates |
| --- | --- | --- |
| 1 | `schema.sql` | All tables with their keys and constraints |
| 2 | `seed.sql` | The 50 Nifty 50 stocks |
| 3 | `demo_data.sql` | `sp_seed_demo_data` and 100 demo investors (`demo1`…`demo100@paisafollows.test`) |
| 4 | `order_guard_trigger.sql` | Trigger: filled or cancelled orders can't be edited |
| 5 | `portfolio_objects.sql` | `v_portfolio_holdings`, `fn_portfolio_value`, `sp_account_statement`, wallet audit trigger |
| 6 | `market_objects.sql` | `v_market_movers`, materialized view `MV_SECTOR_SUMMARY` and its refresh procedure |
| 7 | `watchlist_cascade.sql` | Deleting a watchlist deletes its items (`ON DELETE CASCADE`) |
| 8 | `price_alerts.sql` | `PRICE_ALERT` table and the trigger that fires alerts |
| 9 | `database_users.sql` | Optional: the limited `pf_app` and read-only `pf_report` logins. **Change the passwords inside first.** |

`showcase.sql` is a reference of standalone examples for the viva; the app doesn't need it.

> On Aiven, `log_bin_trust_function_creators` is already on. On a local MySQL with binary logging, run `SET GLOBAL log_bin_trust_function_creators = 1;` before scripts 5 and 6, or functions and triggers will fail with error 1419.

### 2. Configure the connection

The app reads five environment variables. **Never commit real values.** `.env` and `.vscode/` are in `.gitignore`.

In VS Code, put them in `.vscode/launch.json`:

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "PaisafollowsApplication",
      "request": "launch",
      "mainClass": "com.paisafollows.PaisafollowsApplication",
      "projectName": "paisafollows",
      "env": {
        "DB_HOST": "your-service.aivencloud.com",
        "DB_PORT": "12345",
        "DB_NAME": "stock_trading_db",
        "DB_USER": "avnadmin",
        "DB_PASSWORD": "your-password"
      }
    }
  ]
}
```

If you ran `database_users.sql`, use `DB_USER=pf_app` and its password instead of the admin login.

### 3. Run

Start **PaisafollowsApplication** from *Run and Debug* in VS Code, or from a terminal with the same environment variables set:

```bash
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run
```

Open **http://localhost:8080** and log in with a demo account:

| Email | Password | Role |
| --- | --- | --- |
| `demo1@paisafollows.test` | `password123` | Admin (sees the Admin page) |
| `demo2@paisafollows.test` … `demo100@paisafollows.test` | `password123` | Normal investor |

The browser needs internet access the first time, to load the chart and WebSocket libraries from jsDelivr.

---

## Configuration

All settings are in `src/main/resources/application.properties`.

| Property | Default | Meaning |
| --- | --- | --- |
| `app.starting-balance` | `100000.00` | Practice money for every new account (₹) |
| `app.admin-emails` | `demo1@paisafollows.test` | Comma-separated emails that get the ADMIN role at login |
| `market.provider` | `simulated` | Price source |
| `market.force-open` | `true` | Treat the market as open at any time (set `false` to respect 09:15–15:30 IST, Mon–Fri) |
| `market.history-interval-ms` | `10000` | How often a price snapshot is saved for charts |
| `logging.level...JdbcTransactionManager` | `DEBUG` | Prints every COMMIT / ROLLBACK in the console |

---

## API overview

All endpoints return JSON. Errors always look like `{"error": "INSUFFICIENT_FUNDS", "message": "…"}`. Everything under `/api/**` needs a logged-in session, except register, login and health.

| Group | Endpoints |
| --- | --- |
| Auth | `POST /api/auth/register`, `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/me` |
| Market | `GET /api/quotes`, `/api/stocks`, `/api/stocks/search`, `/api/stocks/{symbol}`, `/api/stocks/movers`, `/api/market/sectors`, `/api/market/overview`, `/api/candles/{symbol}` |
| Trading | `POST /api/orders`, `DELETE /api/orders/{id}`, `POST /api/orders/basket`, `GET /api/orders` |
| Portfolio | `GET /api/portfolio`, `/api/statement`, `/api/wallet/history`, `/api/leaderboard` |
| Watchlists | `/api/watchlists/**`, `/api/my-stocks/**` |
| Alerts | `GET/POST /api/alerts`, `PUT/DELETE /api/alerts/{id}` |
| Admin | `/api/admin/**` (ADMIN role only) |
| WebSocket | `ws://localhost:8080/ws` → subscribe to `/topic/ticks` and `/user/queue/orders` |

`request.http` contains 92 ready-made requests covering all of these; open it in VS Code with the REST Client extension and click *Send Request*.

---

## Where each DBMS concept is used

| Concept | Where in the app |
| --- | --- |
| **DDL**: CREATE, ALTER, RENAME, DROP, TRUNCATE | `price_alerts.sql`, `watchlist_cascade.sql`; Admin → price-history archive (`AdminDao`) |
| **DML** with and without cascade | Trading DAOs; renaming a symbol (UPDATE cascade); deleting a watchlist (DELETE cascade); delisting (DELETE refused) |
| **DCL**: GRANT, REVOKE | `database_users.sql`: app login `pf_app` cannot edit the money ledger |
| **TCL**: COMMIT, ROLLBACK, SAVEPOINT | Every trade (`@Transactional`); basket orders (`SAVEPOINT` per stock); refresh procedure |
| **Clauses and aggregates**: WHERE, IN, LIKE, DISTINCT, ORDER BY, LIMIT/OFFSET, GROUP BY/HAVING | Search, screener, order history, sector ranking, candles |
| **Joins**: inner, left, right, full outer (emulated), cross, self, natural | Login, watchlists, market overview, "my stocks", "what can I afford?", similar stocks, stock page |
| **Set operators**: UNION, UNION ALL, INTERSECT, EXCEPT, ANY, ALL | `MyStocksDao`, stock page sector leader |
| **Subqueries**: scalar, correlated, EXISTS, derived tables, CTE | Stock page, watchlist items, leaderboard, candles |
| **Views and materialized view** | `v_portfolio_holdings`, `v_market_movers`, `MV_SECTOR_SUMMARY` |
| **Procedures, functions, triggers** | `sp_seed_demo_data`, `sp_account_statement`, `sp_refresh_mv_sector_summary`, `fn_portfolio_value`, 3 triggers |

---

## Sharing the app temporarily

To let others open your running app from their own devices (for a demo), use a Cloudflare quick tunnel:

```bash
cloudflared tunnel --url http://localhost:8080
```

It prints a public `https://….trycloudflare.com` link. The link works only while both the app and this command are running, and it changes every time.

---

## Troubleshooting

| Problem | Fix |
| --- | --- |
| `Communications link failure` / `Read timed out` at start-up | The database isn't answering. Check the Aiven service is **Running**, that Workbench can connect, or try another network (some Wi-Fi blocks unusual ports). The app keeps retrying every 15 s. |
| `NumberFormatException: "${DB_PORT}"` | The environment variables aren't set. Start the app from the *PaisafollowsApplication* launch configuration. |
| Page looks old after a change | Press **Ctrl+F5** to reload without the browser cache. |
| Chart says "No history yet" | Price history is empty (fresh database or after an admin reset). Wait 2–3 minutes. |
| Grey dot next to your name | The WebSocket isn't connected; check that the app is running and refresh. |
| Error 1419 when running SQL scripts | Turn on `log_bin_trust_function_creators` (see Database setup). |
| `'powershell' is not recognized` when running `mvnw.cmd` | Add `C:\Windows\System32\WindowsPowerShell\v1.0\` to your PATH, or run from VS Code. |

---

*Paper trading only. Prices are simulated around real NSE closing prices and are not investment advice.*