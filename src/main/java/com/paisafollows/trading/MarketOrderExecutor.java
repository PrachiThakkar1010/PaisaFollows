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
import com.paisafollows.trading.dao.HoldingDao;
import com.paisafollows.trading.dao.OrderDao;
import com.paisafollows.trading.dao.TradeDao;
import com.paisafollows.trading.dao.WalletDao;
import com.paisafollows.trading.dao.WalletTxnDao;

/** Executes market orders. */
@Service
public class MarketOrderExecutor {

    /** 0.05% slippage against the user, like a real market order */
    private static final BigDecimal SLIPPAGE = new BigDecimal("0.0005");

    private final WalletDao walletDao;
    private final WalletTxnDao walletTxnDao;
    private final OrderDao orderDao;
    private final HoldingDao holdingDao;
    private final TradeDao tradeDao;
    private final PositionUpdater positions;

    public MarketOrderExecutor(WalletDao walletDao, WalletTxnDao walletTxnDao, OrderDao orderDao,
                               HoldingDao holdingDao, TradeDao tradeDao, PositionUpdater positions) {
        this.walletDao = walletDao;
        this.walletTxnDao = walletTxnDao;
        this.orderDao = orderDao;
        this.holdingDao = holdingDao;
        this.tradeDao = tradeDao;
        this.positions = positions;
    }

    /** One market order = one transaction (START ... COMMIT, or ROLLBACK on any exception). */
    @Transactional
    public OrderResponse execute(AppUser user, InstrumentRef stock, Side side, int quantity, BigDecimal marketPrice) {
        return executeWithinTransaction(user, stock, side, quantity, marketPrice);
    }

    /** Same work, but joins the caller's transaction (the basket order uses this with SAVEPOINTs). */
    public OrderResponse executeWithinTransaction(AppUser user, InstrumentRef stock, Side side,
                                                  int quantity, BigDecimal marketPrice) {
        // 1. Lock the wallet FIRST (fixed lock order: wallet -> holding)
        Wallet wallet = walletDao.lock(user.walletId());

        // 2. For a sell, check free shares BEFORE this order exists
        Holding holding = side == Side.SELL ? lockSellableHolding(user, stock, quantity) : null;

        // 3. The order starts its life as OPEN
        UUID orderId = UuidUtil.newId();
        orderDao.insert(new Order(orderId, user.accountId(), user.portfolioId(), stock.id(),
                OrderType.MARKET, side, quantity, null, null, OrderStatus.OPEN, null, null, null, null));

        return side == Side.BUY
                ? buy(user, wallet, stock, orderId, quantity, marketPrice)
                : sell(user, stock, holding, orderId, quantity, marketPrice);
    }

    /** Locks the holding and checks enough shares are free (owned minus those promised to OTHER open sell orders). */
    public Holding lockSellableHolding(AppUser user, InstrumentRef stock, int quantity) {
        Holding h = holdingDao.lock(user.portfolioId(), stock.id())
                .orElseThrow(() -> new ApiException(ErrorCode.INSUFFICIENT_QTY,
                        "You do not own any " + stock.symbol()));

        int free = h.quantity() - orderDao.openSellQuantity(user.portfolioId(), stock.id());
        if (free <= 0) {
            throw new ApiException(ErrorCode.INSUFFICIENT_QTY,
                    "All your " + stock.symbol() + " shares are already in open sell orders");
        }
        if (quantity > free) {
            throw new ApiException(ErrorCode.INSUFFICIENT_QTY,
                    "You can sell at most " + free + " " + stock.symbol());
        }
        return h;
    }

    private OrderResponse buy(AppUser user, Wallet wallet, InstrumentRef stock, UUID orderId,
                              int quantity, BigDecimal marketPrice) {
        BigDecimal fillPrice = marketPrice.multiply(BigDecimal.ONE.add(SLIPPAGE)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal cost = fillPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);

        if (!walletDao.debitAvailable(wallet.walletId(), cost)) {
            throw new ApiException(ErrorCode.INSUFFICIENT_FUNDS,
                    "Need ₹" + cost + " but only ₹" + wallet.available() + " is available");
        }

        positions.addShares(user.portfolioId(), stock.id(), quantity, fillPrice);
        tradeDao.recordBuy(orderId, stock.id(), quantity, fillPrice);
        markFilled(orderId, fillPrice);
        walletTxnDao.record(wallet.walletId(), "TRADE_DEBIT", cost, orderId,
                "Bought " + quantity + " " + stock.symbol() + " @ ₹" + fillPrice);

        return new OrderResponse(orderId, stock.symbol(), Side.BUY, OrderType.MARKET, quantity,
                OrderStatus.FILLED, fillPrice, cost, null, "Bought " + quantity + " " + stock.symbol());
    }

    private OrderResponse sell(AppUser user, InstrumentRef stock, Holding h, UUID orderId,
                               int quantity, BigDecimal marketPrice) {
        BigDecimal fillPrice = marketPrice.multiply(BigDecimal.ONE.subtract(SLIPPAGE)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal proceeds = fillPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal realisedPnl = positions.removeShares(h, quantity, fillPrice);

        tradeDao.recordSell(orderId, stock.id(), quantity, fillPrice, realisedPnl);
        markFilled(orderId, fillPrice);
        walletDao.credit(user.walletId(), proceeds);
        walletTxnDao.record(user.walletId(), "TRADE_CREDIT", proceeds, orderId,
                "Sold " + quantity + " " + stock.symbol() + " @ ₹" + fillPrice);

        return new OrderResponse(orderId, stock.symbol(), Side.SELL, OrderType.MARKET, quantity,
                OrderStatus.FILLED, fillPrice, proceeds, realisedPnl, "Sold " + quantity + " " + stock.symbol());
    }

    private void markFilled(UUID orderId, BigDecimal fillPrice) {
        if (!orderDao.markFilled(orderId, fillPrice)) {
            throw new IllegalStateException("Order " + orderId + " was no longer OPEN");
        }
    }
}