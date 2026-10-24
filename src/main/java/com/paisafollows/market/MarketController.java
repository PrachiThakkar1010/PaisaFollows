package com.paisafollows.market;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paisafollows.auth.CurrentUser;
import com.paisafollows.common.ApiException;
import com.paisafollows.common.ErrorCode;
import com.paisafollows.market.MarketViews.AffordableStock;
import com.paisafollows.market.MarketViews.MarketOverview;
import com.paisafollows.market.MarketViews.SearchResult;
import com.paisafollows.market.MarketViews.SectorPerformance;
import com.paisafollows.market.MarketViews.SectorSummary;
import com.paisafollows.market.MarketViews.SimilarStock;
import com.paisafollows.market.MarketViews.StockDetail;
import com.paisafollows.market.MarketViews.StockPage;
import com.paisafollows.market.MarketViews.StockRow;

@RestController
@RequestMapping("/api")
public class MarketController {

    /** The ONLY columns the screener may sort by (user word -> SQL column) */
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "change", "change_pct",
            "price", "ltp",
            "symbol", "symbol",
            "name", "name");

    private final MarketQueryDao dao;
    private final CurrentUser currentUser;

    public MarketController(MarketQueryDao dao, CurrentUser currentUser) {
        this.dao = dao;
        this.currentUser = currentUser;
    }

    /** GET /api/stocks/search?q=tat */
    @GetMapping("/stocks/search")
    public List<SearchResult> search(@RequestParam String q) {
        return q.isBlank() ? List.of() : dao.search(q);
    }

    /** GET /api/stocks/sectors */
    @GetMapping("/stocks/sectors")
    public List<String> sectors() {
        return dao.sectors();
    }

    /** GET /api/stocks?sectors=Healthcare,Power&minPrice=500&maxPrice=3000&sort=change&dir=desc&page=0&size=10 */
    @GetMapping("/stocks")
    public StockPage screener(@RequestParam(required = false) List<String> sectors,
                              @RequestParam(defaultValue = "0") BigDecimal minPrice,
                              @RequestParam(defaultValue = "100000000") BigDecimal maxPrice,
                              @RequestParam(defaultValue = "change") String sort,
                              @RequestParam(defaultValue = "desc") String dir,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "10") int size) {
        String column = SORT_COLUMNS.get(sort.toLowerCase());
        if (column == null) {
            throw new ApiException(ErrorCode.INVALID_INPUT, "sort must be one of " + SORT_COLUMNS.keySet());
        }
        List<String> chosen = sectors == null ? List.of() : sectors;
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 50);

        long total = dao.screenerCount(chosen, minPrice, maxPrice);
        List<StockRow> rows = dao.screener(chosen, minPrice, maxPrice, column,
                !"asc".equalsIgnoreCase(dir), safePage, safeSize);
        return new StockPage(rows, safePage, safeSize, total, (int) Math.ceil(total / (double) safeSize));
    }

    /** GET /api/stocks/movers?type=gainers&limit=5   (or type=losers) */
    @GetMapping("/stocks/movers")
    public List<StockRow> movers(@RequestParam(defaultValue = "gainers") String type,
                                 @RequestParam(defaultValue = "5") int limit) {
        return dao.movers(!"losers".equalsIgnoreCase(type), Math.min(Math.max(limit, 1), 50));
    }

    /** GET /api/stocks/affordable?limit=20 : how many shares my cash buys of each stock */
    @GetMapping("/stocks/affordable")
    public List<AffordableStock> affordable(@RequestParam(defaultValue = "20") int limit) {
        return dao.affordable(currentUser.get().walletId(), Math.min(Math.max(limit, 1), 50));
    }

    /** GET /api/stocks/TCS */
    @GetMapping("/stocks/{symbol}")
    public StockDetail detail(@PathVariable String symbol) {
        return dao.detail(symbol)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Unknown symbol " + symbol));
    }

    /** GET /api/stocks/TCS/similar */
    @GetMapping("/stocks/{symbol}/similar")
    public List<SimilarStock> similar(@PathVariable String symbol) {
        return dao.similar(symbol);
    }

    /** GET /api/market/sectors : heatmap, from the materialized view */
    @GetMapping("/market/sectors")
    public List<SectorSummary> sectorSummary() {
        return dao.sectorSummary();
    }

    /** GET /api/market/sector-performance?minStocks=3 : live, GROUP BY + HAVING */
    @GetMapping("/market/sector-performance")
    public List<SectorPerformance> sectorPerformance(@RequestParam(defaultValue = "3") int minStocks) {
        return dao.sectorPerformance(Math.max(minStocks, 1));
    }

    /** GET /api/market/overview */
    @GetMapping("/market/overview")
    public MarketOverview overview() {
        return dao.overview();
    }
}