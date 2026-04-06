package com.delfino.expensetracker.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

@Component
public class SupportedCurrencyService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SupportedCurrencyService.class);
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Environment environment;

    @Value("${currency.api.url:https://api.frankfurter.app}")
    private String apiUrl;

    private volatile Map<String, String> currencyMap = Collections.emptyMap();

    public SupportedCurrencyService(ObjectMapper objectMapper, Environment environment) {
        this.objectMapper = objectMapper;
        this.environment = environment;
        // Follow redirects and use a sensible timeout
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public void run(ApplicationArguments args) {
        // Load fallback currencies from application.yml first (if present)
        Map<String, String> fallback = Binder.get(environment)
                .bind("currencies", Bindable.mapOf(String.class, String.class))
                .orElse(Collections.emptyMap());
        if (!fallback.isEmpty()) {
            // Use fallback as initial map to ensure availability even if remote call fails
            currencyMap = Map.copyOf(fallback);
            log.info("Loaded {} fallback currencies from configuration", currencyMap.size());
        }
        // Then attempt to fetch live list
        fetchCurrencies();
    }

    public synchronized void fetchCurrencies() {
        try {
            String url = apiUrl + "/currencies";
            log.info("Fetching supported currencies from {}", url);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    // Some servers behave differently based on User-Agent; use a common browser UA
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            log.info("Currency API response: status={}, bodyLen={}", resp.statusCode(), resp.body() == null ? 0 : resp.body().length());

            if (resp.statusCode() == 200 && resp.body() != null && !resp.body().isBlank()) {
                Map<String, String> map = objectMapper.readValue(resp.body(), new TypeReference<>() {});
                if (map != null && !map.isEmpty()) {
                    currencyMap = Map.copyOf(map);
                    log.info("Loaded {} supported currencies from remote API", currencyMap.size());
                    return;
                }
            } else {
                log.warn("Currency API returned non-200 status {} for {}", resp.statusCode(), url);
            }
        } catch (Exception e) {
            log.error("Error fetching supported currencies: {}", e.getMessage(), e);
        }
        // If we get here, remote fetch failed or empty — currencyMap remains as fallback or empty
        if (currencyMap.isEmpty()) {
            log.warn("No supported currencies available (remote fetch failed and no fallback configured)");
        } else {
            log.info("Using fallback currencies (count={})", currencyMap.size());
        }
    }

    public boolean isSupported(String code) {
        if (code == null) return false;
        return currencyMap.containsKey(code.toUpperCase());
    }

    public Set<String> getCodes() {
        return currencyMap.keySet();
    }

    public Map<String, String> getMap() {
        return currencyMap;
    }
}
