package com.paisafollows.trading;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;

import com.paisafollows.auth.AppUser;
import com.paisafollows.auth.CurrentUser;
import com.paisafollows.common.ApiException;
import com.paisafollows.common.ErrorCode;
import com.paisafollows.common.PriceService;
import com.paisafollows.common.Tick;
import com.paisafollows.common.UuidUtil;
import com.paisafollows.trading.dao.OrderDao;
import com.paisafollows.trading.dao.TradingLookupDao;

/**
 * Entry point for placing orders. Not @Transactional itself:
 * the trade runs in MarketOrderExecutor's transaction, and a rejected
 * order is saved here AFTER that transaction has rolled back.
 */
@Service
public class OrderService {

    private static final Duration MAX_PRICE_AGE = Duration.ofSeconds(60);

    private final CurrentUser currentUser;
    private final TradingLookupDao lookupDao;
    private final OrderDao orderDao;
    private final PriceService priceService;
    private final MarketOrderExecutor marketExecutor;

    public OrderService(CurrentUser currentUser, TradingLookupDao lookupDao, OrderDao orderDao,
                        PriceService priceService, MarketOrderExecutor marketExecutor) {
        this.currentUser = currentUser;
        this.lookupDao = lookupDao;
        this.orderDao = orderDao;
        this.priceService = priceService;
        this.marketExecutor = marketExecutor;
    }

    public OrderResponse place(PlaceOrderRequest req) {
        AppUser user = currentUser.get();
        InstrumentRef stock = lookupDao.findInstrument(req.symbol())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Unknown symbol " + req.symbol()));

        try {
            if (!stock.active()) {
                throw new ApiException(ErrorCode.INVALID_ORDER, stock.symbol() + " is not tradable");
            }
            if (!lookupDao.hasSegment(user.accountId(), stock.requiredSegment())) {
                throw new ApiException(ErrorCode.INVALID_ORDER,
                        "Your account is not enabled for the " + stock.requiredSegment() + " segment");
            }
            if (req.type() != OrderType.MARKET) {
                throw new ApiException(ErrorCode.INVALID_ORDER, req.type() + " orders arrive in the next update");
            }
            if (!priceService.isMarketOpen()) {
                throw new ApiException(ErrorCode.MARKET_CLOSED, "NSE is closed (open 9:15-15:30 IST, Mon-Fri)");
            }
            Tick tick = priceService.last(stock.id())
                    .filter(t -> Duration.between(t.timestamp(), Instant.now()).compareTo(MAX_PRICE_AGE) <= 0)
                    .orElseThrow(() -> new ApiException(ErrorCode.NO_PRICE, "No recent price for " + stock.symbol()));

            return marketExecutor.execute(user, stock, req.side(), req.quantity(), tick.price());

        } catch (ApiException e) {
            recordRejection(user, stock, req, e.getMessage());
            throw e;
        }
    }

    /** Runs on its own (auto-commit), after the trade transaction has already rolled back. */
    private void recordRejection(AppUser user, InstrumentRef stock, PlaceOrderRequest req, String reason) {
        String shortReason = reason.length() > 255 ? reason.substring(0, 255) : reason;
        orderDao.insert(new Order(UuidUtil.newId(), user.accountId(), user.portfolioId(), stock.id(),
                req.type(), req.side(), req.quantity(), req.price(), req.triggerPrice(),
                OrderStatus.REJECTED, null, shortReason, null, null));
    }
}