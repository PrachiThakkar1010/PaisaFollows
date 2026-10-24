package com.paisafollows.watchlist;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request and reply shapes for watchlists and "My stocks", kept together in one file. */
public final class WatchlistViews {

    private WatchlistViews() {
    }

    public record CreateWatchlistRequest(@NotBlank @Size(max = 100) String name) {
    }

    public record AddItemRequest(@NotBlank String symbol) {
    }

    public record WatchlistSummary(UUID watchlistId, String name, long items, LocalDateTime createdAt) {
    }

    public record WatchlistItem(String symbol, String name, BigDecimal ltp, BigDecimal changePct,
                                boolean owned, LocalDateTime addedAt) {
    }

    public record DeleteResult(String message, long itemsRemoved) {
    }

    public record Suggestion(String symbol, long holders) {
    }

    public record MyStock(String symbol, boolean watched, Integer heldQuantity, BigDecimal ltp) {
    }

    public record Activity(String activity, String symbol, Integer quantity, BigDecimal price, LocalDateTime happenedAt) {
    }

    public record Idea(String symbol, BigDecimal ltp, BigDecimal changePct) {
    }
}