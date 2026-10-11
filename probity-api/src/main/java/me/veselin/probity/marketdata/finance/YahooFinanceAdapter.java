package me.veselin.probity.marketdata.finance;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.veselin.probity.marketdata.dto.PriceBarDto;
import me.veselin.probity.marketdata.exception.MarketDataException;
import me.veselin.probity.portfolio.dto.AssetMetadataDto;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class YahooFinanceAdapter implements FinanceAdapter {

    private final RestTemplate restTemplate;

    private static final ZoneId MARKET_ZONE = ZoneId.of("America/New_York");

    private static final String BASE_URL =
            "https://query1.finance.yahoo.com/v8/finance/chart/%s?interval=1d&period1=%d&period2=%d";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    @Override
    public List<PriceBarDto> fetchDailyBars(String ticker, LocalDate from, LocalDate to) {
        long period1 = from.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        long period2 = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        String url   = String.format(BASE_URL, ticker, period1, period2);

        log.debug("Fetching bars ticker={} url={}", ticker, url);

        try {
            JsonNode root = fetchWithHeaders(url);
            validateResponse(root, ticker);
            return extractBars(ticker, root);
        } catch (MarketDataException e) {
            throw e;
        } catch (Exception e) {
            throw new MarketDataException("Failed to fetch data for ticker: " + ticker, e);
        }
    }

    @Override
    public Optional<AssetMetadataDto> fetchMetadata(String ticker) {
        // Use the same chart endpoint as fetchDailyBars for metadata.
        // The /v8/finance/chart endpoint works reliably and returns meta in chart.result[0].meta
        long period1 = 0L; // epoch start for full history
        long period2 = Instant.now().getEpochSecond();
        String url = String.format(BASE_URL, ticker, period1, period2);

        log.debug("Fetching metadata from chart endpoint ticker={} url={}", ticker, url);

        try {
            JsonNode root = fetchWithHeaders(url);
            validateResponse(root, ticker);

            JsonNode result = root.path("chart").path("result").get(0);
            JsonNode meta = result.path("meta");

            if (meta.isMissingNode() || meta.isNull()) {
                log.warn("No meta in chart response for ticker={}", ticker);
                return Optional.empty();
            }

            String symbol   = meta.path("symbol").asText(ticker.toUpperCase());
            String longName = meta.path("longName").asText(null);
            String shortName = meta.path("shortName").asText(null);

            // longName is preferred; shortName is fallback
            String name = (longName != null && !longName.isBlank()) ? longName : shortName;
            if (name == null || name.isBlank()) {
                name = symbol;
            }

            String type = meta.path("instrumentType").asText("EQUITY");

            // Chart meta does NOT contain sector — leave as null (not available from this endpoint)
            String sector = null;

            log.debug("Resolved metadata from chart ticker={} name='{}' type={} sector={}",
                    ticker, name, type, sector);

            return Optional.of(new AssetMetadataDto(
                    symbol, name, type, sector));

        } catch (Exception e) {
            log.warn("fetchMetadata failed for ticker={}: {}", ticker, e.getMessage());
            return Optional.empty();
        }
    }


    // ── Helpers ──────────────────────────────────────────────────────────────

    private JsonNode fetchWithHeaders(String url) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", USER_AGENT);

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class
        );

        log.trace("Yahoo response for url={}: {}", url, response.getBody());
        return response.getBody();
    }

    private void validateResponse(JsonNode root, String ticker) {
        if (root == null) {
            throw new MarketDataException("Received null response from Yahoo Finance for " + ticker);
        }

        JsonNode error = root.path("chart").path("error");
        if (!error.isNull() && !error.isMissingNode() && error.has("description")) {
            throw new MarketDataException(
                    "Yahoo Finance API error for " + ticker + ": "
                            + error.get("description").asText());
        }

        JsonNode result = root.path("chart").path("result");
        if (result.isMissingNode() || result.isNull()
                || !result.isArray() || result.isEmpty()) {
            throw new MarketDataException(
                    "No data returned from Yahoo Finance for " + ticker);
        }
    }

    private List<PriceBarDto> extractBars(String ticker, JsonNode root) {
        JsonNode result     = root.path("chart").path("result").get(0);
        JsonNode timestamps = result.path("timestamp");
        JsonNode quote      = result.path("indicators").path("quote").get(0);
        JsonNode adjCloseArr = result.path("indicators")
                .path("adjclose").get(0)
                .path("adjclose");

        if (timestamps.isMissingNode() || timestamps.isEmpty()) {
            log.debug("No timestamps in Yahoo response for ticker={} — market likely closed", ticker);
            return List.of();
        }

        // Validate array lengths match before iterating — Yahoo occasionally
        // returns ragged arrays on partial trading days.
        int len = timestamps.size();
        if (quote.isEmpty() || adjCloseArr.isEmpty()) {
            log.debug("Empty quote/adjclose arrays for ticker={}", ticker);
            return List.of();
        }

        List<PriceBarDto> bars = new ArrayList<>(len);

        for (int i = 0; i < len; i++) {
            // Guard against ragged arrays — skip any index that would be
            // out of bounds in quote or adjclose.
            if (i >= adjCloseArr.size()) {
                log.warn("adjclose array shorter than timestamps at i={} ticker={}", i, ticker);
                break;
            }

            JsonNode openNode     = quote.path("open").get(i);
            JsonNode highNode     = quote.path("high").get(i);
            JsonNode lowNode      = quote.path("low").get(i);
            JsonNode closeNode    = quote.path("close").get(i);
            JsonNode adjCloseNode = adjCloseArr.get(i);
            JsonNode volumeNode   = quote.path("volume").get(i);

            // Skip bars where any required field is null or missing.
            // Yahoo returns null entries for market holidays and early closes.
            if (isNullOrMissing(openNode)     || isNullOrMissing(highNode)
                    || isNullOrMissing(lowNode)  || isNullOrMissing(closeNode)
                    || isNullOrMissing(adjCloseNode)) {
                log.trace("Skipping null bar at i={} ticker={}", i, ticker);
                continue;
            }

            BigDecimal adjCloseVal;
            try {
                adjCloseVal = new BigDecimal(adjCloseNode.asText());
            } catch (NumberFormatException e) {
                log.warn("Unparseable adjClose '{}' at i={} ticker={} — skipping",
                        adjCloseNode.asText(), i, ticker);
                continue;
            }

            // Skip bars with zero or negative adjClose — these are data artifacts,
            // not real prices, and cause division-by-zero downstream.
            if (adjCloseVal.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Non-positive adjClose {} at i={} ticker={} — skipping",
                        adjCloseVal, i, ticker);
                continue;
            }

            // Use MARKET_ZONE for date derivation — consistent with the rest of
            // the codebase and avoids UTC midnight edge cases.
            LocalDate date = Instant.ofEpochSecond(timestamps.get(i).asLong())
                    .atZone(MARKET_ZONE)
                    .toLocalDate();

            bars.add(new PriceBarDto(
                    ticker,
                    date,
                    parseBigDecimal(openNode,  ticker, i, "open"),
                    parseBigDecimal(highNode,  ticker, i, "high"),
                    parseBigDecimal(lowNode,   ticker, i, "low"),
                    parseBigDecimal(closeNode, ticker, i, "close"),
                    adjCloseVal,
                    volumeNode != null && !volumeNode.isNull() ? volumeNode.asLong() : 0L
            ));
        }

        bars.sort(Comparator.comparing(PriceBarDto::barDate));
        log.debug("Extracted {} bars for ticker={}", bars.size(), ticker);
        return bars;
    }

    private boolean isNullOrMissing(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode();
    }

    private BigDecimal parseBigDecimal(JsonNode node, String ticker, int i, String field) {
        try {
            return new BigDecimal(node.asText());
        } catch (NumberFormatException e) {
            log.warn("Unparseable {} '{}' at i={} ticker={} — defaulting to zero",
                    field, node.asText(), i, ticker);
            return BigDecimal.ZERO;
        }
    }
}