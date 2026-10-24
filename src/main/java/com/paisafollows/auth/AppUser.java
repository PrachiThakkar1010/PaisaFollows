package com.paisafollows.auth;

import java.io.Serializable;
import java.util.UUID;

/**
 * The logged-in user, kept in the session after login.
 * Holds only ids that never change, so every later request knows whose
 * account, wallet and portfolio to use without asking the database again.
 */
public record AppUser(
        UUID userId,
        UUID accountId,
        UUID walletId,
        UUID portfolioId,
        String name,
        String email
) implements Serializable {
}