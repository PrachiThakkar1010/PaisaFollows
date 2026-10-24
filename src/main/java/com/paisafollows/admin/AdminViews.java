package com.paisafollows.admin;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Request and reply shapes for admin and leaderboard screens. */
public final class AdminViews {

    private AdminViews() {
    }

    public record RenameRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9&-]{1,20}$",
                    message = "1-20 letters, digits, & or -") String newSymbol) {
    }

    public record RenameResult(String oldSymbol, String newSymbol, long alertsFollowed, String message) {
    }

    public record DelistResult(String symbol, boolean deleted, String status, String reason) {
    }

    public record ArchiveInfo(String table, long rows) {
    }

    public record ArchiveResult(String archiveTable, long rowsArchived, String message) {
    }

    public record ResetResult(long rowsRemoved, String message) {
    }

    public record LeaderboardEntry(int rank, String name, BigDecimal totalValue, BigDecimal returnPct) {
    }
}