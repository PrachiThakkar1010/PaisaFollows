package com.paisafollows.portfolio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paisafollows.auth.AppUser;
import com.paisafollows.auth.CurrentUser;
import com.paisafollows.common.ApiException;
import com.paisafollows.common.ErrorCode;

@RestController
@RequestMapping("/api")
public class PortfolioController {

    private final PortfolioDao dao;
    private final CurrentUser currentUser;

    public PortfolioController(PortfolioDao dao, CurrentUser currentUser) {
        this.dao = dao;
        this.currentUser = currentUser;
    }

    /** GET /api/portfolio : cash, holdings with live value, and totals */
    @GetMapping("/portfolio")
    public PortfolioSummary portfolio() {
        AppUser user = currentUser.get();

        Map<String, Object> wallet = dao.walletBalance(user.walletId());
        BigDecimal available = (BigDecimal) wallet.get("available_balance");
        BigDecimal reserved = (BigDecimal) wallet.get("reserved_balance");

        Map<String, Object> totals = dao.holdingTotals(user.portfolioId());
        BigDecimal holdingsValue = dao.portfolioValue(user.portfolioId());

        return new PortfolioSummary(
                available,
                reserved,
                (BigDecimal) totals.get("invested"),
                holdingsValue,
                (BigDecimal) totals.get("unrealised_pnl"),
                dao.realisedPnl(user.portfolioId()),
                (BigDecimal) totals.get("day_change"),
                available.add(reserved).add(holdingsValue),
                dao.findHoldings(user.portfolioId()));
    }

    /** GET /api/statement?from=2026-10-01&to=2026-10-31  (default: last 30 days) */
    @GetMapping("/statement")
    public Statement statement(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(30);
        if (start.isAfter(end)) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "'from' must not be after 'to'");
        }

        List<StatementEntry> entries = dao.statement(currentUser.get().walletId(), start, end);
        BigDecimal totalIn = sum(entries, "IN");
        BigDecimal totalOut = sum(entries, "OUT");
        return new Statement(start, end, totalIn, totalOut, entries);
    }

    /** GET /api/wallet/history?limit=20 : before/after of every balance change (from the trigger) */
    @GetMapping("/wallet/history")
    public List<BalanceChange> walletHistory(@RequestParam(defaultValue = "20") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        return dao.balanceHistory(currentUser.get().walletId(), safeLimit);
    }

    private static BigDecimal sum(List<StatementEntry> entries, String flow) {
        return entries.stream()
                .filter(e -> e.flow().equals(flow))
                .map(StatementEntry::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}