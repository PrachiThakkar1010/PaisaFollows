package com.paisafollows.market;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.paisafollows.common.PriceService;
import com.paisafollows.common.Tick;
import com.paisafollows.common.TickListener;

/**
 * Holds the latest price of every stock in memory and tells every TickListener about each new price.
 * This is the real implementation of the PriceService contract from Module 2.
 */
@Service
public class PriceCache implements PriceService {

    private static final Logger log = LoggerFactory.getLogger(PriceCache.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalTime OPEN = LocalTime.of(9, 15);
    private static final LocalTime CLOSE = LocalTime.of(15, 30);

    private final Map<UUID, Tick> latest = new ConcurrentHashMap<>();
    private final Map<String, UUID> idsBySymbol = new ConcurrentHashMap<>();
    private final Map<UUID, BigDecimal> previousCloses = new ConcurrentHashMap<>();

    private final ObjectProvider<TickListener> listeners;
    private final boolean forceOpen;

    public PriceCache(ObjectProvider<TickListener> listeners,
                      @Value("${market.force-open:false}") boolean forceOpen) {
        this.listeners = listeners;
        this.forceOpen = forceOpen;
    }

    /** Called by a price provider for every new price. */
    public void publish(Tick tick) {
        latest.put(tick.instrumentId(), tick);
        idsBySymbol.put(tick.symbol(), tick.instrumentId());
        listeners.orderedStream().forEach(listener -> {
            try {
                listener.onTick(tick);
            } catch (RuntimeException e) {
                // One faulty listener must not stop prices reaching the others
                log.error("TickListener {} failed for {}", listener.getClass().getSimpleName(), tick.symbol(), e);
            }
        });
    }

    public void setPreviousClose(UUID instrumentId, BigDecimal close) {
        previousCloses.put(instrumentId, close);
    }

    // ----- PriceService contract (used by trading) -----

    @Override
    public Optional<Tick> last(UUID instrumentId) {
        return Optional.ofNullable(latest.get(instrumentId));
    }

    @Override
    public boolean isMarketOpen() {
        if (forceOpen) {
            return true;
        }
        ZonedDateTime now = ZonedDateTime.now(IST);
        DayOfWeek day = now.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        LocalTime time = now.toLocalTime();
        return !time.isBefore(OPEN) && time.isBefore(CLOSE);
    }

    // ----- Extra lookups (used by quotes and the database writer) -----

    public Optional<Tick> lastBySymbol(String symbol) {
        UUID id = idsBySymbol.get(symbol.toUpperCase());
        return id == null ? Optional.empty() : last(id);
    }

    public BigDecimal previousClose(UUID instrumentId, BigDecimal fallback) {
        return previousCloses.getOrDefault(instrumentId, fallback);
    }

    public Map<UUID, BigDecimal> previousCloses() {
        return previousCloses;
    }

    public Collection<Tick> all() {
        return latest.values();
    }
}