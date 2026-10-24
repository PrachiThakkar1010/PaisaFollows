package com.paisafollows.market;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Reply shapes of the market screens, kept together in one file. */
public final class MarketViews {

    private MarketViews() {
    }

    public record SearchResult(String symbol, String name) {
    }

    public record StockRow(UUID instrumentId, String symbol, String name, String sector,
                           BigDecimal ltp, BigDecimal previousClose, BigDecimal changeAmount, BigDecimal changePct) {
    }

    public record StockPage(List<StockRow> items, int page, int size, long totalItems, int totalPages) {
    }

    public record StockDetail(String symbol, String name, String companyName, String sector,
                              BigDecimal faceValue, String exchange, BigDecimal ltp, BigDecimal previousClose,
                              BigDecimal changePct, long holders, boolean aboveSectorAverage, boolean sectorLeader) {
    }

    public record SimilarStock(String symbol, String name, BigDecimal ltp) {
    }

    public record AffordableStock(String symbol, String name, BigDecimal ltp, long maxQuantity) {
    }

    public record SectorSummary(String sector, int stocks, BigDecimal avgChangePct, int gainers, int losers,
                                int totalHolders, BigDecimal totalInvested, LocalDateTime refreshedAt) {
    }

    public record SectorPerformance(String sector, long stocks, BigDecimal avgChangePct, BigDecimal bestChangePct) {
    }

    public record MostHeld(String symbol, long holders, long sharesHeld) {
    }

    public record MarketOverview(long stocks, long gainers, long losers, long unchanged,
                                 BigDecimal avgChangePct, BigDecimal avgPrice,
                                 List<String> aboveAveragePrice, List<MostHeld> mostHeld) {
    }
}