package com.paisafollows.auth;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Returned by login and by GET /api/me. */
public record MeResponse(
        UUID userId,
        UUID accountId,
        UUID walletId,
        UUID portfolioId,
        String name,
        String email,
        BigDecimal availableBalance,
        BigDecimal reservedBalance,
        List<String> segments
) {
}