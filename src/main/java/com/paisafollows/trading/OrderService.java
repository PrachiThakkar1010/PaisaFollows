package com.paisafollows.trading;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

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
 * Entry point for placing and cancelling orders. Not @Transactional itself:
 * each order runs in its own transaction in the executor/placer, and a rejected
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
    private final LimitOrderPlacer limitPlacer;
    private final OrderCanceller canceller;
    private final MatchingEngine matchingEngine;

    public OrderService(CurrentUser currentUser, TradingLookupDao lookupDao, OrderDao orderDao,
                        PriceService priceService, MarketOrderExecutor marketExecutor,
                        LimitOrderPlacer limitPlacer, OrderCanceller canceller, MatchingEngine matchingEngine) {
        this.currentUser = currentUser;
        this.lookupDao = lookupDao;
        this.orderDao = orderDao;
        this.priceService = priceService;
        this.marketExecutor = marketExecutor;
        this.limitPlacer = limitPlacer;
        this.canceller = canceller;
        this.matchingEngine = matchingEngine;
    }

    public OrderResponse place(PlaceOrderRequest req) {
        AppUser user = currentUser.get();
        InstrumentRef stock = findInstrument(req.symbol());

        try {
            checkTradable(user, stock);
            return switch (req.type()) {
                case MARKET -> marketExecutor.execute(user, stock, req.side(), req.quantity(), checkMarketPrice(stock));
                case LIMIT -> placeLimit(user, stock, req);
                case STOP_LOSS -> placeStopLoss(user, stock, req);
            };
        } catch (ApiException e) {
            recordRejection(user, stock, req, e.getMessage());
            throw e;
        }
    }

    public OrderResponse cancel(UUID orderId) {
        OrderResponse response = canceller.close(orderId, currentUser.get().accountId(), OrderStatus.CANCELLED);
        matchingEngine.remove(orderId);
        return response;
    }

    private OrderResponse placeLimit(AppUser user, InstrumentRef stock, PlaceOrderRequest req) {
        if (req.price() == null) {
            throw new ApiException(ErrorCode.INVALID_ORDER, "A LIMIT order needs a price");
        }
        BigDecimal limit = req.price().setScale(2, RoundingMode.HALF_UP);
        OrderResponse placed = limitPlacer.place(user, stock, OrderType.LIMIT, req.side(), req.quantity(), limit, null);
        // Added to the engine only after the placer's transaction has committed
        matchingEngine.add(new BookEntry(placed.orderId(), stock.id(), OrderType.LIMIT, req.side(), req.quantity(), limit));
        return placed;
    }

    private OrderResponse placeStopLoss(AppUser user, InstrumentRef stock, PlaceOrderRequest req) {
        if (req.side() != Side.SELL) {
            throw new ApiException(ErrorCode.INVALID_ORDER, "Stop-loss is only for selling shares you own");
        }
        if (req.triggerPrice() == null) {
            throw new ApiException(ErrorCode.INVALID_ORDER, "A STOP_LOSS order needs a triggerPrice");
        }
        BigDecimal trigger = req.triggerPrice().setScale(2, RoundingMode.HALF_UP);

        // A stop-loss protects against a FUTURE fall, so its trigger must be below today's price
        BigDecimal current = priceService.last(stock.id()).map(Tick::price).orElse(null);
        if (current != null && trigger.compareTo(current) >= 0) {
            throw new ApiException(ErrorCode.INVALID_ORDER, "Trigger ₹" + trigger
                    + " must be below the current price ₹" + current + " (use a MARKET sell to sell now)");
        }

        OrderResponse placed = limitPlacer.place(user, stock, OrderType.STOP_LOSS, Side.SELL, req.quantity(), null, trigger);
        matchingEngine.add(new BookEntry(placed.orderId(), stock.id(), OrderType.STOP_LOSS, Side.SELL, req.quantity(), trigger));
        return placed;
    }

    // ----- Checks, also used by the basket order -----

    public InstrumentRef findInstrument(String symbol) {
        return lookupDao.findInstrument(symbol)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Unknown symbol " + symbol));
    }

    public void checkTradable(AppUser user, InstrumentRef stock) {
        if (!stock.active()) {
            throw new ApiException(ErrorCode.INVALID_ORDER, stock.symbol() + " is not tradable");
        }
        if (!lookupDao.hasSegment(user.accountId(), stock.requiredSegment())) {
            throw new ApiException(ErrorCode.INVALID_ORDER,
                    "Your account is not enabled for the " + stock.requiredSegment() + " segment");
        }
    }

    /** Market orders need an open market and a recent price. */
    public BigDecimal checkMarketPrice(InstrumentRef stock) {
        if (!priceService.isMarketOpen()) {
            throw new ApiException(ErrorCode.MARKET_CLOSED, "NSE is closed (open 9:15-15:30 IST, Mon-Fri)");
        }
        Tick tick = priceService.last(stock.id())
                .filter(t -> Duration.between(t.timestamp(), Instant.now()).compareTo(MAX_PRICE_AGE) <= 0)
                .orElseThrow(() -> new ApiException(ErrorCode.NO_PRICE, "No recent price for " + stock.symbol()));
        return tick.price();
    }

    /** Saves a REJECTED order with its reason (auto-commit, or inside the caller's transaction). */
    public void recordRejection(AppUser user, InstrumentRef stock, PlaceOrderRequest req, String reason) {
        String shortReason = reason.length() > 255 ? reason.substring(0, 255) : reason;
        orderDao.insert(new Order(UuidUtil.newId(), user.accountId(), user.portfolioId(), stock.id(),
                req.type(), req.side(), req.quantity(), req.price(), req.triggerPrice(),
                OrderStatus.REJECTED, null, shortReason, null, null));
    }
}