package com.paisafollows.trading;

import java.math.BigDecimal;
import java.util.UUID;

public record Wallet(UUID walletId, UUID accountId, BigDecimal available, BigDecimal reserved) {
}