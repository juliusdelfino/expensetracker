package com.delfino.expensetracker.service;

import com.delfino.expensetracker.model.Store;
import com.delfino.expensetracker.repository.StoreRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service for geocoding store addresses using Nominatim OpenStreetMap API.
 * Respects the 1 request/second rate limit to avoid being blocked.
 */
@Service
public class GeocodingService {

    private static final Logger log = LoggerFactory.getLogger(GeocodingService.class);
    private static final String NOMINATIM_URL = "https://nominatim.openstreetmap.org/search";

    private final StoreRepository storeRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    /**
     * Track last request time to enforce 1 req/sec rate limit.
     */
    private long lastRequestTimeMs = 0;

    public GeocodingService(StoreRepository storeRepository, ObjectMapper objectMapper) {
        this.storeRepository = storeRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Async geocode a single store after OCR processing.
     * First tries full address, then simplified address if no results.
     */
    @Async
    public void geocodeStoreAsync(Store store) {
        geocodeStore(store);
    }

    /**
     * Synchronous geocode for a store. Returns true if coordinates were found and saved.
     */
    public boolean geocodeStore(Store store) {
        if (store == null) return false;
        if (store.getLatitude() != null && store.getLongitude() != null) return true;

        // Build full address query: address, city, country, postal code
        String fullQuery = buildFullQuery(store);
        if (fullQuery == null || fullQuery.isBlank()) {
            log.debug("No address info available for store {} ({})", store.getId(), store.getName());
            return false;
        }

        log.info("Geocoding store {} ({}) with full query: {}", store.getId(), store.getName(), fullQuery);
        NominatimResult result = searchNominatim(fullQuery);

        if (result == null) {
            // Simplify: use just street name+number, city, country, postal code
            String simplifiedQuery = buildSimplifiedQuery(store);
            if (simplifiedQuery != null && !simplifiedQuery.equals(fullQuery)) {
                log.info("Full address failed, retrying with simplified query: {}", simplifiedQuery);
                result = searchNominatim(simplifiedQuery);
            }
        }

        if (result != null) {
            store.setLatitude(result.lat);
            store.setLongitude(result.lon);
            if (result.placeId != null && store.getSourceId() == null) {
                store.setSourceId("nominatim-" + result.placeId);
            }
            storeRepository.save(store);
            log.info("Geocoded store {} ({}) -> lat={}, lon={}, sourceId={}", store.getId(), store.getName(), result.lat, result.lon, store.getSourceId());
            return true;
        }

        log.info("Could not geocode store {} ({})", store.getId(), store.getName());
        return false;
    }

    /**
     * Retroactively geocode all stores that have no lat/long.
     * Called at startup.
     */
    public void geocodeAllStoresWithoutCoordinates() {
        List<Store> stores = storeRepository.findAll().stream()
                .filter(s -> s.getLatitude() == null || s.getLongitude() == null)
                .filter(s -> s.getAddress() != null || s.getCity() != null || s.getCountry() != null)
                .collect(Collectors.toList());

        if (stores.isEmpty()) {
            log.info("No stores without coordinates found — skipping retroactive geocoding");
            return;
        }

        log.info("Retroactively geocoding {} store(s) without coordinates", stores.size());
        int success = 0;
        for (Store store : stores) {
            if (geocodeStore(store)) {
                success++;
            }
        }
        log.info("Retroactive geocoding complete: {}/{} stores geocoded", success, stores.size());
    }

    /**
     * Build full query from all address fields: address, city, country, postalCode.
     */
    private String buildFullQuery(Store store) {
        return Stream.of(store.getAddress(), store.getCity(), store.getCountry(), store.getPostalCode())
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining(" "));
    }

    /**
     * Build simplified query: extract street name & number from address, keep city, country, postalCode.
     */
    private String buildSimplifiedQuery(Store store) {
        String simplified = simplifyAddress(store.getAddress());
        return Stream.of(simplified, store.getCity(), store.getCountry(), store.getPostalCode())
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining(" "));
    }

    /**
     * Simplify an address to just the street name and number.
     * E.g., "123 Main Street, Suite 200" -> "123 Main Street"
     */
    private String simplifyAddress(String address) {
        if (address == null || address.isBlank()) return null;
        // Take everything before the first comma (typically suite/unit info)
        String simplified = address.split(",")[0].trim();
        return simplified.isBlank() ? null : simplified;
    }

    /**
     * Result from a Nominatim API call.
     */
    record NominatimResult(double lat, double lon, String placeId) {}

    /**
     * Call Nominatim API. Enforces 1 request/second rate limit.
     * Returns NominatimResult or null if not found.
     */
    private synchronized NominatimResult searchNominatim(String query) {
        try {
            // Enforce rate limit: at least 1 second between requests
            long now = System.currentTimeMillis();
            long elapsed = now - lastRequestTimeMs;
            if (elapsed < 1100) {
                Thread.sleep(1100 - elapsed);
            }

            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = NOMINATIM_URL + "?q=" + encoded + "&format=json&addressdetails=1&limit=1";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "ExpenseTracker/1.0")
                    .GET()
                    .build();

            lastRequestTimeMs = System.currentTimeMillis();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Nominatim returned status {} for query: {}", response.statusCode(), query);
                return null;
            }

            JsonNode results = objectMapper.readTree(response.body());
            if (results.isArray() && !results.isEmpty()) {
                JsonNode first = results.get(0);
                double lat = first.get("lat").asDouble();
                double lon = first.get("lon").asDouble();
                String placeId = first.has("place_id") ? String.valueOf(first.get("place_id").asLong()) : null;
                return new NominatimResult(lat, lon, placeId);
            }

            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Geocoding interrupted for query: {}", query);
            return null;
        } catch (Exception e) {
            log.error("Geocoding failed for query: {}", query, e);
            return null;
        }
    }
}

