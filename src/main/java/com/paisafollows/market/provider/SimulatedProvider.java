package com.paisafollows.market.provider;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.paisafollows.common.Tick;
import com.paisafollows.market.Instrument;
import com.paisafollows.market.InstrumentDao;
import com.paisafollows.market.PriceCache;
import com.paisafollows.market.PriceDao;

/**
 * Fake but realistic prices: each second every stock moves randomly by about 0.1%.
 * Active only when market.provider=simulated.
 */
@Component
public class SimulatedProvider {

    private static final Logger log = LoggerFactory.getLogger(SimulatedProvider.class);
    private static final BigDecimal TICK_SIZE = new BigDecimal("0.05");   // NSE prices move in steps of 5 paise
    private static final BigDecimal DEFAULT_PRICE = new BigDecimal("1000.00");

    private final InstrumentDao instrumentDao;
    private final PriceDao priceDao;
    private final PriceCache priceCache;
    private final YahooQuoteClient yahoo;
    private final boolean enabled;

    private final Map<UUID, BigDecimal> current = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private volatile List<Instrument> stocks = List.of();

    public SimulatedProvider(InstrumentDao instrumentDao, PriceDao priceDao, PriceCache priceCache,
                             YahooQuoteClient yahoo, @Value("${market.provider}") String provider) {
        this.instrumentDao = instrumentDao;
        this.priceDao = priceDao;
        this.priceCache = priceCache;
        this.yahoo = yahoo;
        this.enabled = "simulated".equalsIgnoreCase(provider);
    }

    /** Runs once, when the app has fully started. */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!enabled) {
            return;
        }
        List<Instrument> list = instrumentDao.findActiveStocks();
        Map<UUID, BigDecimal[]> saved = priceDao.findSavedPrices();
        int fromDb = 0, fromYahoo = 0, fromDefault = 0;

        for (Instrument stock : list) {
            BigDecimal[] row = saved.get(stock.id());
            BigDecimal price;
            BigDecimal prevClose;
            if (row != null && row[0] != null) {
                price = row[0];
                prevClose = row[1] != null ? row[1] : row[0];
                fromDb++;
            } else {
                price = yahoo.lastPrice(stock.symbol()).orElse(null);
                if (price != null) {
                    fromYahoo++;
                } else {
                    price = DEFAULT_PRICE;
                    fromDefault++;
                }
                prevClose = price;
            }
            current.put(stock.id(), price);
            priceCache.setPreviousClose(stock.id(), prevClose);
            priceCache.publish(new Tick(stock.id(), stock.symbol(), price, Instant.now()));
        }
        stocks = list;
        log.info("Simulator started with {} stocks ({} prices from database, {} from Yahoo, {} default)",
                list.size(), fromDb, fromYahoo, fromDefault);
    }

    /** Every second: move every price a little and publish it. */
    @Scheduled(fixedRate = 1000)
    public void tick() {
        for (Instrument stock : stocks) {
            BigDecimal old = current.get(stock.id());
            double move = random.nextGaussian() * 0.001;   // usually within about ±0.1%
            BigDecimal next = roundToTick(old.multiply(BigDecimal.valueOf(1 + move)));
            if (next.compareTo(TICK_SIZE) < 0) {
                next = TICK_SIZE;
            }
            current.put(stock.id(), next);
            priceCache.publish(new Tick(stock.id(), stock.symbol(), next, Instant.now()));
        }
    }

    private static BigDecimal roundToTick(BigDecimal value) {
        return value.divide(TICK_SIZE, 0, RoundingMode.HALF_UP).multiply(TICK_SIZE).setScale(2, RoundingMode.HALF_UP);
    }
}