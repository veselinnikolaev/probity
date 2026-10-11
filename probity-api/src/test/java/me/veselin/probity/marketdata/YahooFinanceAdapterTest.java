package me.veselin.probity.marketdata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.veselin.probity.marketdata.dto.PriceBarDto;
import me.veselin.probity.marketdata.exception.MarketDataException;
import me.veselin.probity.marketdata.finance.YahooFinanceAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YahooFinanceAdapterTest {

    @Mock
    private RestTemplate restTemplate;

    private YahooFinanceAdapter adapter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final LocalDate FROM = LocalDate.of(2024, 1, 1);
    private static final LocalDate TO   = LocalDate.of(2024, 1, 31);
    private static final String TICKER  = "AAPL";

    @BeforeEach
    void setUp() {
        adapter = new YahooFinanceAdapter(restTemplate);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private void mockResponse(String json) throws JsonProcessingException {
        JsonNode node = objectMapper.readTree(json);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(JsonNode.class)))
                .thenReturn(new ResponseEntity<>(node, HttpStatus.OK));
    }

    private void mockNullResponse() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(), eq(JsonNode.class)))
                .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));
    }

    // ── Tests ────────────────────────────────────────────────────────────────

    @Test
    void fetchDailyBars_returnsMappedBars_andSortsAscending() throws JsonProcessingException {
        // Timestamps: 2024-01-10 = 1704844800, 2024-01-20 = 1705708800
        mockResponse("""
                {
                  "chart": {
                    "result": [{
                      "timestamp": [1704893400, 1705757400],
                      "indicators": {
                        "quote": [{
                          "open":   [180.00, 190.00],
                          "high":   [182.00, 195.00],
                          "low":    [178.50, 188.00],
                          "close":  [181.00, 192.00],
                          "volume": [1500000, 2000000]
                        }],
                        "adjclose": [{
                          "adjclose": [181.00, 192.00]
                        }]
                      }
                    }],
                    "error": null
                  }
                }
                """);

        List<PriceBarDto> bars = adapter.fetchDailyBars(TICKER, FROM, TO);

        assertThat(bars).hasSize(2);

        PriceBarDto first = bars.getFirst();
        assertThat(first.barDate()).isEqualTo(LocalDate.of(2024, 1, 10));
        assertThat(first.ticker()).isEqualTo(TICKER);
        assertThat(first.close()).isEqualByComparingTo("181.00");
        assertThat(first.volume()).isEqualTo(1_500_000L);

        PriceBarDto second = bars.get(1);
        assertThat(second.barDate()).isEqualTo(LocalDate.of(2024, 1, 20));
        assertThat(second.close()).isEqualByComparingTo("192.00");
    }

    @Test
    void fetchDailyBars_skipsNullBars() throws JsonProcessingException {
        // Yahoo sometimes returns null for certain fields on partial trading days
        mockResponse("""
                {
                  "chart": {
                    "result": [{
                      "timestamp": [1704893400, 1705757400],
                      "indicators": {
                        "quote": [{
                          "open":   [null, 190.00],
                          "high":   [null, 195.00],
                          "low":    [null, 188.00],
                          "close":  [null, 192.00],
                          "volume": [null, 2000000]
                        }],
                        "adjclose": [{
                          "adjclose": [null, 192.00]
                        }]
                      }
                    }],
                    "error": null
                  }
                }
                """);

        List<PriceBarDto> bars = adapter.fetchDailyBars(TICKER, FROM, TO);

        assertThat(bars).hasSize(1);
        assertThat(bars.getFirst().barDate()).isEqualTo(LocalDate.of(2024, 1, 20));
    }

    @Test
    void fetchDailyBars_throwsException_whenResponseIsNull() {
        mockNullResponse();

        assertThatThrownBy(() -> adapter.fetchDailyBars(TICKER, FROM, TO))
                .isInstanceOf(MarketDataException.class)
                .hasMessageContaining("Received null response");
    }

    @Test
    void fetchDailyBars_throwsException_whenApiReturnsError() throws JsonProcessingException {
        mockResponse("""
                {
                  "chart": {
                    "result": null,
                    "error": {
                      "code": "Not Found",
                      "description": "No fundamentals data found for any of the summaryTypes=financialData"
                    }
                  }
                }
                """);

        assertThatThrownBy(() -> adapter.fetchDailyBars(TICKER, FROM, TO))
                .isInstanceOf(MarketDataException.class)
                .hasMessageContaining("Yahoo Finance API error");
    }

    @Test
    void fetchDailyBars_throwsException_whenResultIsEmpty() throws JsonProcessingException {
        mockResponse("""
                {
                  "chart": {
                    "result": [],
                    "error": null
                  }
                }
                """);

        assertThatThrownBy(() -> adapter.fetchDailyBars(TICKER, FROM, TO))
                .isInstanceOf(MarketDataException.class)
                .hasMessageContaining("No data returned");
    }
}
