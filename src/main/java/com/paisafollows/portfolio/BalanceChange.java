package com.paisafollows.portfolio;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One row of WALLET_AUDIT, written by the trg_wallet_audit trigger. */
public record BalanceChange(
        BigDecimal oldAvailable,
        BigDecimal newAvailable,
        BigDecimal oldReserved,
        BigDecimal newReserved,
        LocalDateTime changedAt
) {
}