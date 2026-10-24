package com.paisafollows.trading;

import java.math.BigDecimal;
import java.util.UUID;

public record Holding(UUID holdingId, UUID portfolioId, UUID instrumentId, int quantity, BigDecimal averageBuyPrice) {
}