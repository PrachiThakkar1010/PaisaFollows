package com.paisafollows.market;

import java.util.Comparator;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.paisafollows.common.ApiException;
import com.paisafollows.common.ErrorCode;

@RestController
@RequestMapping("/api/quotes")
public class QuoteController {

    private final PriceCache prices;

    public QuoteController(PriceCache prices) {
        this.prices = prices;
    }

    /** GET /api/quotes -> all 50 stocks, A to Z */
    @GetMapping
    public List<Quote> all() {
        return prices.all().stream()
                .map(t -> Quote.of(t, prices.previousClose(t.instrumentId(), t.price()), prices.isMarketOpen()))
                .sorted(Comparator.comparing(Quote::symbol))
                .toList();
    }

    /** GET /api/quotes/RELIANCE -> one stock */
    @GetMapping("/{symbol}")
    public Quote one(@PathVariable String symbol) {
        return prices.lastBySymbol(symbol)
                .map(t -> Quote.of(t, prices.previousClose(t.instrumentId(), t.price()), prices.isMarketOpen()))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No price for " + symbol));
    }
}