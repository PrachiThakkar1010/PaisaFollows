package com.paisafollows.alert;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/** Request and reply shapes for price alerts. */
public final class AlertViews {

    private AlertViews() {
    }

    public record CreateAlertRequest(
            @NotBlank String symbol,
            @NotBlank @Pattern(regexp = "ABOVE|BELOW", message = "must be ABOVE or BELOW") String direction,
            @NotNull @Positive BigDecimal targetPrice) {
    }

    public record UpdateAlertRequest(@NotNull @Positive BigDecimal targetPrice) {
    }

    public record AlertView(
            UUID alertId,
            String symbol,
            String direction,
            BigDecimal targetPrice,
            BigDecimal currentPrice,
            String status,
            BigDecimal triggeredPrice,
            LocalDateTime triggeredAt,
            LocalDateTime createdAt) {
    }
}