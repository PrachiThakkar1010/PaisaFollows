package com.paisafollows.trading;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.paisafollows.trading.dao.OrderBookDao;

/** Day orders: every open order expires when NSE closes at 15:30 IST. */
@Component
public class OrderExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(OrderExpiryJob.class);

    private final OrderBookDao orderBookDao;
    private final OrderCanceller canceller;
    private final MatchingEngine matchingEngine;

    public OrderExpiryJob(OrderBookDao orderBookDao, OrderCanceller canceller, MatchingEngine matchingEngine) {
        this.orderBookDao = orderBookDao;
        this.canceller = canceller;
        this.matchingEngine = matchingEngine;
    }

    @Scheduled(cron = "0 30 15 * * MON-FRI", zone = "Asia/Kolkata")
    public void expireDayOrders() {
        int expired = 0;
        for (BookEntry entry : orderBookDao.findAll()) {
            try {
                canceller.close(entry.orderId(), null, OrderStatus.EXPIRED);
                matchingEngine.remove(entry.orderId());
                expired++;
            } catch (RuntimeException e) {
                log.warn("Could not expire order {}: {}", entry.orderId(), e.getMessage());
            }
        }
        log.info("End of day: {} open orders expired", expired);
    }
}