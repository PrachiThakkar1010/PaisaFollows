package com.paisafollows.market.provider;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Fetches the latest price of an NSE stock from Yahoo Finance (free, no key, unofficial). */
@Component
public class YahooQuoteClient {

    private static final Logger log = LoggerFactory.getLogger(YahooQuoteClient.class);
    private static final Pattern PRICE = Pattern.compile("\"regularMarketPrice\"\\s*:\\s*([0-9.]+)");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** e.g. lastPrice("RELIANCE") -> 1412.50. Empty if Yahoo is unreachable or blocks us. */
    public Optional<BigDecimal> lastPrice(String nseSymbol) {
        String yahooSymbol = URLEncoder.encode(nseSymbol + ".NS", StandardCharsets.UTF_8); // M&M -> M%26M.NS
        String url = "https://query1.finance.yahoo.com/v8/finance/chart/" + yahooSymbol + "?range=1d&interval=1d";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Yahoo returned {} for {}", response.statusCode(), nseSymbol);
                return Optional.empty();
            }
            Matcher m = PRICE.matcher(response.body());
            if (m.find()) {
                return Optional.of(new BigDecimal(m.group(1)).setScale(2, RoundingMode.HALF_UP));
            }
        } catch (IOException e) {
            log.warn("Could not reach Yahoo for {}: {}", nseSymbol, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return Optional.empty();
    }
}