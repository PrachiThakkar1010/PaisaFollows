package com.paisafollows.trading;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.paisafollows.common.ApiException;
import com.paisafollows.common.ErrorCode;
import com.paisafollows.trading.dao.OrderBookDao;
import com.paisafollows.trading.dao.OrderDao;
import com.paisafollows.trading.dao.WalletDao;
import com.paisafollows.trading.dao.WalletTxnDao;

/** Closes an OPEN order as CANCELLED (by its owner) or EXPIRED (end of day), and frees its reserve. */
@Service
public class OrderCanceller {

    private final OrderDao orderDao;
    private final OrderBookDao orderBookDao;
    private final WalletDao walletDao;
    private final WalletTxnDao walletTxnDao;
    private final ApplicationEventPublisher events;

    public OrderCanceller(OrderDao orderDao, OrderBookDao orderBookDao, WalletDao walletDao,
                          WalletTxnDao walletTxnDao, ApplicationEventPublisher events) {
        this.orderDao = orderDao;
        this.orderBookDao = orderBookDao;
        this.walletDao = walletDao;
        this.walletTxnDao = walletTxnDao;
        this.events = events;
    }

    /** @param ownerAccountId the logged-in user's account, or null for the system (expiry) */
    @Transactional
    public OrderResponse close(UUID orderId, UUID ownerAccountId, OrderStatus newStatus) {
        Order found = orderDao.findById(orderId)
                .filter(o -> ownerAccountId == null || o.accountId().equals(ownerAccountId))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Order not found"));

        // Lock order: wallet -> order
        Wallet wallet = walletDao.lockByAccount(found.accountId());
        Order o = orderDao.lock(orderId).orElseThrow();
        if (o.status() != OrderStatus.OPEN) {
            throw new ApiException(ErrorCode.INVALID_ORDER, "This order is already " + o.status());
        }

        orderDao.markClosed(orderId, newStatus);
        orderBookDao.remove(orderId);

        String message = "Order " + newStatus.name().toLowerCase();
        if (o.side() == Side.BUY && o.orderType() == OrderType.LIMIT) {
            BigDecimal reserved = o.price().multiply(BigDecimal.valueOf(o.quantity())).setScale(2, RoundingMode.HALF_UP);
            if (!walletDao.release(wallet.walletId(), reserved)) {
                throw new IllegalStateException("Reserved money missing for order " + orderId);
            }
            walletTxnDao.record(wallet.walletId(), "ORDER_RELEASE", reserved, orderId,
                    (newStatus == OrderStatus.EXPIRED ? "Expired" : "Cancelled") + " limit buy");
            message += "; ₹" + reserved + " returned to your balance";
        }

        events.publishEvent(new OrderEvent(orderId, o.side(), o.orderType(), o.quantity(),
                newStatus, null, message));

        return new OrderResponse(orderId, null, o.side(), o.orderType(), o.quantity(), newStatus,
                null, null, null, "Order " + newStatus.name().toLowerCase());
    }
}