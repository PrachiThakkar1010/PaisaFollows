package com.paisafollows.market;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Copies prices from memory into the database on a timer. */
@Component
public class PriceWriter {

    private static final Logger log = LoggerFactory.getLogger(PriceWriter.class);

    private final PriceCache cache;
    private final PriceDao dao;

    public PriceWriter(PriceCache cache, PriceDao dao) {
        this.cache = cache;
        this.dao = dao;
    }

    /** Every 5 s: latest price of each stock -> CURRENT_MARKET_PRICE */
    @Scheduled(fixedRate = 5000, initialDelay = 10000)
    public void saveCurrentPrices() {
        if (!cache.all().isEmpty()) {
            dao.upsertCurrentPrices(cache.all(), cache.previousCloses());
        }
    }

    /** Every market.history-interval-ms (default 10 s): one snapshot per stock -> MARKET_PRICE_HISTORY */
    @Scheduled(fixedRateString = "${market.history-interval-ms:10000}", initialDelay = 15000)
    public void saveHistory() {
        if (!cache.all().isEmpty()) {
            dao.insertHistory(cache.all());
            log.debug("Saved price snapshot for {} stocks", cache.all().size());
        }
    }
}