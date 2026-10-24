package com.paisafollows.market;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.paisafollows.common.Tick;
import com.paisafollows.common.TickListener;

/** Sends every new price to all browsers subscribed to /topic/ticks. */
@Component
public class TickBroadcaster implements TickListener {

    private final SimpMessagingTemplate messaging;
    private final PriceCache prices;

    public TickBroadcaster(SimpMessagingTemplate messaging, PriceCache prices) {
        this.messaging = messaging;
        this.prices = prices;
    }

    @Override
    public void onTick(Tick tick) {
        Quote quote = Quote.of(tick, prices.previousClose(tick.instrumentId(), tick.price()), prices.isMarketOpen());
        messaging.convertAndSend("/topic/ticks", quote);
    }
}