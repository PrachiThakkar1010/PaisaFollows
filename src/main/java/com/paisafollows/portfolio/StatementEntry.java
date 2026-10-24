package com.paisafollows.portfolio;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One row returned by sp_account_statement. */
public record StatementEntry(String type, String flow, BigDecimal amount, String description, LocalDateTime createdAt) {
}