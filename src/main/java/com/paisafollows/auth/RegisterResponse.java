package com.paisafollows.auth;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Returned after a successful sign-up. Never contains the password. */
public record RegisterResponse(
        UUID userId,
        UUID accountId,
        UUID walletId,
        UUID portfolioId,
        UUID watchlistId,
        BigDecimal startingBalance,
        String investorType,
        List<String> segments
) {
}