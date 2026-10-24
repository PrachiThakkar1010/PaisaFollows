package com.paisafollows.trading;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.paisafollows.trading.dao.HoldingDao;

/** Adds and removes shares in HOLDING. Must run inside a transaction that already locked the wallet. */
@Component
public class PositionUpdater {

    private final HoldingDao holdingDao;

    public PositionUpdater(HoldingDao holdingDao) {
        this.holdingDao = holdingDao;
    }

    /** New holding, or a new weighted average price for an existing one. */
    public void addShares(UUID portfolioId, UUID instrumentId, int quantity, BigDecimal price) {
        Optional<Holding> existing = holdingDao.lock(portfolioId, instrumentId);
        if (existing.isPresent()) {
            Holding h = existing.get();
            int newQuantity = h.quantity() + quantity;
            BigDecimal newAverage = h.averageBuyPrice().multiply(BigDecimal.valueOf(h.quantity()))
                    .add(price.multiply(BigDecimal.valueOf(quantity)))
                    .divide(BigDecimal.valueOf(newQuantity), 2, RoundingMode.HALF_UP);
            holdingDao.update(h.holdingId(), newQuantity, newAverage);
        } else {
            holdingDao.insert(portfolioId, instrumentId, quantity, price);
        }
    }

    /** Removes sold shares (deletes the row when all are sold). Returns the realised profit/loss. */
    public BigDecimal removeShares(Holding h, int quantity, BigDecimal sellPrice) {
        BigDecimal realisedPnl = sellPrice.subtract(h.averageBuyPrice())
                .multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
        if (quantity == h.quantity()) {
            holdingDao.delete(h.holdingId());
        } else {
            holdingDao.update(h.holdingId(), h.quantity() - quantity, h.averageBuyPrice());
        }
        return realisedPnl;
    }
}