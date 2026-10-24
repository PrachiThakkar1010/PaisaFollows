package com.paisafollows.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Reply of GET /api/statement */
public record Statement(LocalDate from, LocalDate to, BigDecimal totalIn, BigDecimal totalOut, List<StatementEntry> entries) {
}