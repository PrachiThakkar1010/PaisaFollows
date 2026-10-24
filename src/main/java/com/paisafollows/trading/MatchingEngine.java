package com.paisafollows.trading;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.paisafollows.common.PriceService;
import com.paisafollows.common.Tick;
import com.paisafollows.common.TickListener;
import com.paisafollows.trading.dao.OrderBookDao;

/**
 * Keeps waiting orders in memory, grouped by stock. On every price tick it checks
 * that stock's orders and fills the ones the price has reached.
 */
@Component
public class MatchingEngine implements TickListener {

    private static final Logger log = LoggerFactory.getLogger(MatchingEngine.class);

    private final Map<UUID, List<BookEntry>> waitingByStock = new ConcurrentHashMap<>();

    private final OrderBookDao orderBookDao;
    private final LimitOrderFiller filler;
    private final PriceService priceService;

    public MatchingEngine(OrderBookDao orderBookDao, LimitOrderFiller filler, PriceService priceService) {
        this.orderBookDao = orderBookDao;
        this.filler = filler;
        this.priceService = priceService;
    }

    /** After a restart, rebuild memory from ORDER_BOOK so no waiting order is lost. */
    @EventListener(ApplicationReadyEvent.class)
    public void loadFromDatabase() {
        List<BookEntry> all = orderBookDao.findAll();
        all.forEach(this::add);
        log.info("Matching engine loaded {} waiting orders", all.size());
    }

    public void add(BookEntry entry) {
        waitingByStock.computeIfAbsent(entry.instrumentId(), id -> new CopyOnWriteArrayList<>()).add(entry);
    }

    public void remove(UUID orderId) {
        waitingByStock.values().forEach(list -> list.removeIf(e -> e.orderId().equals(orderId)));
    }

    @Override
    public void onTick(Tick tick) {
        List<BookEntry> waiting = waitingByStock.get(tick.instrumentId());
        if (waiting == null || waiting.isEmpty() || !priceService.isMarketOpen()) {
            return;
        }
        for (BookEntry entry : waiting) {
            if (reached(entry, tick.price())) {
                try {
                    filler.fill(entry.orderId(), tick.price());
                } catch (RuntimeException e) {
                    log.error("Could not fill order {}", entry.orderId(), e);
                }
                waiting.remove(entry);   // filled, or failed (it reloads from ORDER_BOOK after a restart)
            }
        }
    }

    /** Has the price reached this order? */
    static boolean reached(BookEntry e, BigDecimal price) {
        if (e.type() == OrderType.STOP_LOSS) {
            return price.compareTo(e.price()) <= 0;                 // price fell to the trigger
        }
        return e.side() == Side.BUY
                ? price.compareTo(e.price()) <= 0                   // cheap enough to buy
                : price.compareTo(e.price()) >= 0;                  // high enough to sell
    }
}