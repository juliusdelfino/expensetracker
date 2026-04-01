package com.delfino.expensetracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.delfino.expensetracker.model.ExchangeRateCache;
import com.delfino.expensetracker.repository.ExchangeRateCacheRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
public class CurrencyService {

    private static final Logger log = LoggerFactory.getLogger(CurrencyService.class);
    private final ExchangeRateCacheRepository cacheRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${currency.api.url:https://api.frankfurter.app}")
    private String apiUrl;

    public CurrencyService(ExchangeRateCacheRepository cacheRepository, ObjectMapper objectMapper) {
        this.cacheRepository = cacheRepository;
        this.objectMapper = objectMapper;
    }

    public BigDecimal getRate(String from, String to, LocalDate date) {
        if (from.equalsIgnoreCase(to)) return BigDecimal.ONE;

        String key = from + "_" + to + "_" + date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        Optional<ExchangeRateCache> cached = cacheRepository.findByKey(key);
        if (cached.isPresent()) return cached.get().getRate();

        try {
            String url = apiUrl + "/" + date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    + "?from=" + from + "&to=" + to;
            log.info("Calling Currency API: GET {}", url);
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("Currency API response: status={}, bodyLength={}", response.statusCode(), response.body().length());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode rates = root.get("rates");
                if (rates != null && rates.has(to)) {
                    BigDecimal rate = rates.get(to).decimalValue();
                    log.info("Currency API returned rate {} -> {} = {} on {}", from, to, rate, date);
                    cacheRepository.save(new ExchangeRateCache(key, rate, LocalDateTime.now()));
                    return rate;
                }
                log.warn("Currency API response missing rate for {} in response body", to);
            } else {
                log.warn("Currency API returned non-200 status {} for {}", response.statusCode(), url);
            }
        } catch (Exception e) {
            log.error("Failed to fetch exchange rate for {} -> {} on {}: {}", from, to, date, e.getMessage(), e);
        }
        return null; // null means manual entry needed
    }
}

