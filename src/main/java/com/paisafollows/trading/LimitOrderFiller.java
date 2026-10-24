package com.paisafollows.trading;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.paisafollows.trading.dao.HoldingDao;
import com.paisafollows.trading.dao.OrderBookDao;
import com.paisafollows.trading.dao.OrderDao;
import com.paisafollows.trading.dao.TradeDao;
import com.paisafollows.trading.dao.WalletDao;
import com.paisafollows.trading.dao.WalletTxnDao;

/** Fills one waiting LIMIT / STOP_LOSS order at the current tick, as one transaction. */
@Service
public class LimitOrderFiller {

    private static final Logger log = LoggerFactory.getLogger(LimitOrderFiller.class);

    private final OrderDao orderDao;
    private final OrderBookDao orderBookDao;
    private final WalletDao walletDao;
    private final WalletTxnDao walletTxnDao;
    private final HoldingDao holdingDao;
    private final TradeDao tradeDao;
    private final PositionUpdater positions;
    private final ApplicationEventPublisher events;

    public LimitOrderFiller(OrderDao orderDao, OrderBookDao orderBookDao, WalletDao walletDao,
                            WalletTxnDao walletTxnDao, HoldingDao holdingDao, TradeDao tradeDao,
                            PositionUpdater positions, ApplicationEventPublisher events) {
        this.orderDao = orderDao;
        this.orderBookDao = orderBookDao;
        this.walletDao = walletDao;
        this.walletTxnDao = walletTxnDao;
        this.holdingDao = holdingDao;
        this.tradeDao = tradeDao;
        this.positions = positions;
        this.events = events;
    }

    @Transactional
    public void fill(UUID orderId, BigDecimal tickPrice) {
        Optional<Order> found = orderDao.findById(orderId);
        if (found.isEmpty() || found.get().status() != OrderStatus.OPEN) {
            orderBookDao.remove(orderId);           // already filled/cancelled elsewhere: just tidy up
            return;
        }

        // Lock order: wallet -> order -> holding
        Wallet wallet = walletDao.lockByAccount(found.get().accountId());
        Order o = orderDao.lock(orderId).orElseThrow();
        if (o.status() != OrderStatus.OPEN) {       // a cancel won the race while we waited for the lock
            return;
        }

        if (o.side() == Side.BUY) {
            fillBuy(o, wallet, tickPrice);
        } else {
            fillSell(o, wallet, tickPrice);
        }
        orderBookDao.remove(orderId);
    }

    private void fillBuy(Order o, Wallet wallet, BigDecimal tickPrice) {
        BigDecimal fillPrice = tickPrice.min(o.price()).setScale(2, RoundingMode.HALF_UP);   // never above the limit
        BigDecimal qty = BigDecimal.valueOf(o.quantity());
        BigDecimal reserved = o.price().multiply(qty).setScale(2, RoundingMode.HALF_UP);
        BigDecimal cost = fillPrice.multiply(qty).setScale(2, RoundingMode.HALF_UP);
        BigDecimal leftover = reserved.subtract(cost);

        // The whole reserve leaves the reserved part; whatever was not needed goes back to available
        if (!walletDao.spendReserved(wallet.walletId(), reserved)) {
            throw new IllegalStateException("Reserved money missing for order " + o.orderId());
        }
        if (leftover.signum() > 0) {
            walletDao.credit(wallet.walletId(), leftover);
        }

        positions.addShares(o.portfolioId(), o.instrumentId(), o.quantity(), fillPrice);
        tradeDao.recordBuy(o.orderId(), o.instrumentId(), o.quantity(), fillPrice);
        orderDao.markFilled(o.orderId(), fillPrice);
        walletTxnDao.record(wallet.walletId(), "TRADE_DEBIT", cost, o.orderId(),
                "Limit buy filled: " + o.quantity() + " @ ₹" + fillPrice);
        walletTxnDao.record(wallet.walletId(), "ORDER_RELEASE", leftover, o.orderId(),
                "Unused reserve returned");
        log.info("Filled {} BUY {} x{} @ {}", o.orderType(), o.orderId(), o.quantity(), fillPrice);

        events.publishEvent(new OrderEvent(o.orderId(), Side.BUY, o.orderType(), o.quantity(),
                OrderStatus.FILLED, fillPrice,
                "Limit buy filled: " + o.quantity() + " @ ₹" + fillPrice + " (₹" + cost + ")"));
    }

    private void fillSell(Order o, Wallet wallet, BigDecimal tickPrice) {
        Optional<Holding> holding = holdingDao.lock(o.portfolioId(), o.instrumentId());
        if (holding.isEmpty() || holding.get().quantity() < o.quantity()) {
            // Shares are gone (should not happen: they were promised). Close the order safely.
            orderDao.markClosed(o.orderId(), OrderStatus.CANCELLED);
            log.warn("Cancelled sell {}: not enough shares left", o.orderId());
            events.publishEvent(new OrderEvent(o.orderId(), Side.SELL, o.orderType(), o.quantity(),
                    OrderStatus.CANCELLED, null, "Sell cancelled: not enough shares left"));
            return;
        }

        BigDecimal fillPrice = (o.orderType() == OrderType.LIMIT ? tickPrice.max(o.price()) : tickPrice)
                .setScale(2, RoundingMode.HALF_UP);   // a limit sell never fills below its limit
        BigDecimal proceeds = fillPrice.multiply(BigDecimal.valueOf(o.quantity())).setScale(2, RoundingMode.HALF_UP);
        BigDecimal realisedPnl = positions.removeShares(holding.get(), o.quantity(), fillPrice);

        tradeDao.recordSell(o.orderId(), o.instrumentId(), o.quantity(), fillPrice, realisedPnl);
        orderDao.markFilled(o.orderId(), fillPrice);
        walletDao.credit(wallet.walletId(), proceeds);
        walletTxnDao.record(wallet.walletId(), "TRADE_CREDIT", proceeds, o.orderId(),
                o.orderType() + " sell filled: " + o.quantity() + " @ ₹" + fillPrice);
        log.info("Filled {} SELL {} x{} @ {}", o.orderType(), o.orderId(), o.quantity(), fillPrice);

        String what = o.orderType() == OrderType.STOP_LOSS ? "Stop-loss triggered" : "Limit sell filled";
        events.publishEvent(new OrderEvent(o.orderId(), Side.SELL, o.orderType(), o.quantity(),
                OrderStatus.FILLED, fillPrice,
                what + ": " + o.quantity() + " @ ₹" + fillPrice + " (P&L ₹" + realisedPnl + ")"));
    }
}