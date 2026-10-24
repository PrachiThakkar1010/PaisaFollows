package com.paisafollows.auth;

import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;

/**
 * The logged-in user, kept in the session after login.
 * Holds only ids that never change, so every later request knows whose
 * account, wallet and portfolio to use without asking the database again.
 *
 * Also a java.security.Principal whose name is the email: WebSocket "send to user"
 * (/user/queue/orders) uses that name to find this user's open browser connections.
 */
public record AppUser(
        UUID userId,
        UUID accountId,
        UUID walletId,
        UUID portfolioId,
        String name,
        String email
) implements Serializable, Principal {

    /** Principal name = email (unique). Not the same as name(), which is the person's display name. */
    @Override
    public String getName() {
        return email;
    }
}