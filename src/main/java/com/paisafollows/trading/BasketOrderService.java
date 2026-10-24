package com.paisafollows.trading;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.paisafollows.auth.AppUser;
import com.paisafollows.auth.CurrentUser;
import com.paisafollows.common.ApiException;

/**
 * Buys/sells several stocks in ONE transaction. Each leg gets its own SAVEPOINT:
 * a failed leg rolls back to its savepoint only; the other legs are kept and
 * everything is committed together at the end.
 */
@Service
public class BasketOrderService {

    private static final Logger log = LoggerFactory.getLogger(BasketOrderService.class);

    private final JdbcTemplate jdbc;
    private final CurrentUser currentUser;
    private final OrderService orderService;
    private final MarketOrderExecutor marketExecutor;

    public BasketOrderService(JdbcTemplate jdbc, CurrentUser currentUser,
                              OrderService orderService, MarketOrderExecutor marketExecutor) {
        this.jdbc = jdbc;
        this.currentUser = currentUser;
        this.orderService = orderService;
        this.marketExecutor = marketExecutor;
    }

    @Transactional   // START TRANSACTION ... COMMIT around the whole basket
    public BasketResult place(BasketRequest request) {
        AppUser user = currentUser.get();
        List<BasketLegResult> results = new ArrayList<>();
        int number = 0;

        for (BasketLeg leg : request.legs()) {
            number++;
            String savepoint = "leg_" + number;

            InstrumentRef stock;
            try {
                stock = orderService.findInstrument(leg.symbol());
            } catch (ApiException e) {
                results.add(new BasketLegResult(number, leg.symbol(), leg.side(), leg.quantity(),
                        "SKIPPED", null, null, e.getMessage()));
                continue;
            }

            jdbc.execute("SAVEPOINT " + savepoint);
            log.info("SAVEPOINT {} ({} {} {})", savepoint, leg.side(), leg.quantity(), stock.symbol());
            try {
                orderService.checkTradable(user, stock);
                BigDecimal price = orderService.checkMarketPrice(stock);
                OrderResponse filled = marketExecutor.executeWithinTransaction(
                        user, stock, leg.side(), leg.quantity(), price);

                jdbc.execute("RELEASE SAVEPOINT " + savepoint);
                log.info("RELEASE SAVEPOINT {} (leg kept)", savepoint);
                results.add(new BasketLegResult(number, stock.symbol(), leg.side(), leg.quantity(),
                        "FILLED", savepoint, filled, null));

            } catch (ApiException e) {
                jdbc.execute("ROLLBACK TO SAVEPOINT " + savepoint);
                log.info("ROLLBACK TO SAVEPOINT {} ({})", savepoint, e.getMessage());
                orderService.recordRejection(user, stock,
                        new PlaceOrderRequest(stock.symbol(), leg.side(), OrderType.MARKET, leg.quantity(), null, null),
                        "Basket leg " + number + ": " + e.getMessage());
                results.add(new BasketLegResult(number, stock.symbol(), leg.side(), leg.quantity(),
                        "ROLLED_BACK", savepoint, null, e.getMessage()));
            }
        }

        long filled = results.stream().filter(r -> r.outcome().equals("FILLED")).count();
        long rolledBack = results.stream().filter(r -> r.outcome().equals("ROLLED_BACK")).count();
        return new BasketResult(results, filled, rolledBack);
    }
}