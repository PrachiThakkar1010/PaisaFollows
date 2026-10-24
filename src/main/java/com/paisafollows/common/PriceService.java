package com.paisafollows.common;

import java.util.Optional;
import java.util.UUID;

/** Implemented by market data. Used by trading. */
public interface PriceService {

    /** Latest known price, or empty if none has arrived yet. */
    Optional<Tick> last(UUID instrumentId);

    /** True while NSE is open: 9:15 to 15:30 IST, Monday to Friday. */
    boolean isMarketOpen();
}