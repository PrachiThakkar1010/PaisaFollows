package com.paisafollows.trading;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.paisafollows.auth.AppUser;
import com.paisafollows.common.ApiException;
import com.paisafollows.common.ErrorCode;
import com.paisafollows.common.UuidUtil;
import com.paisafollows.trading.dao.OrderBookDao;
import com.paisafollows.trading.dao.OrderDao;
import com.paisafollows.trading.dao.WalletDao;
import com.paisafollows.trading.dao.WalletTxnDao;

/** Places LIMIT and STOP_LOSS orders: saved as OPEN, added to ORDER_BOOK, money reserved for buys. */
@Service
public class LimitOrderPlacer {

    private final WalletDao walletDao;
    private final WalletTxnDao walletTxnDao;
    private final OrderDao orderDao;
    private final OrderBookDao orderBookDao;
    private final MarketOrderExecutor marketExecutor;

    public LimitOrderPlacer(WalletDao walletDao, WalletTxnDao walletTxnDao, OrderDao orderDao,
                            OrderBookDao orderBookDao, MarketOrderExecutor marketExecutor) {
        this.walletDao = walletDao;
        this.walletTxnDao = walletTxnDao;
        this.orderDao = orderDao;
        this.orderBookDao = orderBookDao;
        this.marketExecutor = marketExecutor;
    }

    @Transactional
    public OrderResponse place(AppUser user, InstrumentRef stock, OrderType type, Side side, int quantity,
                               BigDecimal limitPrice, BigDecimal triggerPrice) {
        // 1. Lock the wallet FIRST (same lock order as every other trade)
        Wallet wallet = walletDao.lock(user.walletId());

        // 2. A sell must have enough free shares (checked before this order exists)
        if (side == Side.SELL) {
            marketExecutor.lockSellableHolding(user, stock, quantity);
        }

        // 3. Save the order as OPEN and put it in the order book
        UUID orderId = UuidUtil.newId();
        orderDao.insert(new Order(orderId, user.accountId(), user.portfolioId(), stock.id(),
                type, side, quantity, limitPrice, triggerPrice, OrderStatus.OPEN, null, null, null, null));
        BigDecimal bookPrice = type == OrderType.STOP_LOSS ? triggerPrice : limitPrice;
        orderBookDao.add(orderId, stock.id(), side, quantity, bookPrice);

        // 4. A buy reserves its maximum cost now, so the money cannot be spent elsewhere
        String message;
        if (side == Side.BUY) {
            BigDecimal reserve = limitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
            if (!walletDao.reserve(wallet.walletId(), reserve)) {
                throw new ApiException(ErrorCode.INSUFFICIENT_FUNDS,
                        "Need ₹" + reserve + " to reserve but only ₹" + wallet.available() + " is available");
            }
            walletTxnDao.record(wallet.walletId(), "ORDER_RESERVE", reserve, orderId,
                    "Reserved for limit buy of " + quantity + " " + stock.symbol());
            message = "Waiting to buy " + quantity + " " + stock.symbol() + " at ₹" + limitPrice
                    + " or lower; ₹" + reserve + " reserved";
        } else if (type == OrderType.LIMIT) {
            message = "Waiting to sell " + quantity + " " + stock.symbol() + " at ₹" + limitPrice + " or higher";
        } else {
            message = "Stop-loss set: sells " + quantity + " " + stock.symbol() + " if the price falls to ₹" + triggerPrice;
        }

        return new OrderResponse(orderId, stock.symbol(), side, type, quantity,
                OrderStatus.OPEN, null, null, null, message);
    }
}