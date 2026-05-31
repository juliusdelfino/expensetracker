package com.delfino.expensetracker.service;

import com.delfino.expensetracker.businesslogic.StoreCountryMatcher;
import com.delfino.expensetracker.model.Store;
import com.delfino.expensetracker.repository.StoreRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
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

    @Value("${geocoding.api.url:https://nominatim.openstreetmap.org}")
    private String geocodingApiUrl;

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
     * Tries up to 5 progressively simplified query combinations.
     */
    public boolean geocodeStore(Store store) {
        if (store == null) return false;
        if (store.getLatitude() != null && store.getLongitude() != null) return true;

        String street       = simplifyAddress(store.getAddress());
        String streetFull   = store.getAddress();
        String city         = store.getCity();
        String country      = store.getCountry();
        String postal       = store.getPostalCode();
        String streetName   = extractStreetName(street);

        // Build candidate queries in order of specificity
        List<String> candidates = buildCandidateQueries(streetFull, street, streetName, city, country, postal);

        if (candidates.isEmpty()) {
            log.debug("No address info available for store {} ({})", store.getId(), store.getName());
            return false;
        }

        NominatimResult result = null;
        for (String query : candidates) {
            log.info("Geocoding store {} ({}) — trying: {}", store.getId(), store.getName(), query);
            result = searchNominatim(query);
            if (result != null) {
                log.info("  → match found");
                break;
            }
            log.info("  → no result");
        }

        if (result != null) {
            store.setLatitude(result.lat);
            store.setLongitude(result.lon);
            if (result.place_id != null && store.getSourceId() == null) {
                store.setSourceId("nominatim-" + result.place_id);
            }
            if (result.address != null && StoreCountryMatcher.countryMatches(store.getCountry(), result.address.country_code)
                    && result.address.city != null) {
                store.setCity(result.address.city);
            }
            storeRepository.save(store);
            log.info("Geocoded store {} ({}) -> lat={}, lon={}, sourceId={}", store.getId(), store.getName(), result.lat, result.lon, store.getSourceId());
            return true;
        }

        log.info("Could not geocode store {} ({})", store.getId(), store.getName());
        return false;
    }

    /**
     * Build a deduplicated list of candidate queries in order:
     * 1. Full address + city + country + postal code
     * 2. Street name & number (comma-trimmed) + city + country + postal code
     * 3. Street name & number + city
     * 4. Street name only (no number) + city
     */
    private List<String> buildCandidateQueries(String streetFull, String street, String streetName,
                                                String city, String country, String postal) {
        List<String> candidates = new ArrayList<>();
        // Add candidates in order of specificity, skip nulls and duplicates
        addCandidate(candidates, join(streetFull, city, country, postal));   // 1. full
        addCandidate(candidates, join(street, city, country, postal));       // 2. simplified street + city + country + postal
        addCandidate(candidates, join(street, city));                        // 3. simplified street + city
        addCandidate(candidates, join(streetName, city));                    // 4. street name (no number) + city
        addCandidate(candidates, join(city, country, postal));
        return candidates;
    }

    /** Add a query to candidates if non-null and not a duplicate. */
    private void addCandidate(List<String> candidates, String query) {
        if (StringUtils.hasText(query) && !candidates.contains(query)) {
            candidates.add(query);
        }
    }

    /** Join non-null, non-blank parts with a comma+space. Returns null if nothing to join. */
    private String join(String... parts) {
        String joined = Stream.of(parts)
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .collect(Collectors.joining(", "));
        return joined.isBlank() ? null : joined;
    }

    /**
     * Retroactively geocode all stores that have no lat/long.
     * Called at startup.
     */
    public void geocodeAllStoresWithoutCoordinates() {
        List<Store> stores = storeRepository.findAll().stream()
                .filter(s -> s.getLatitude() == null || s.getLongitude() == null)
                .filter(s -> s.getAddress() != null || s.getCity() != null || s.getCountry() != null)
                .toList();

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
     * Simplify an address to just the first component before a comma or hashtag.
     * E.g., "123 Main Street, Suite 200" -> "123 Main Street"
     * E.g., "20 Pasir Panjang #02-115 mapletree business city" -> "20 Pasir Panjang"
     */
    private String simplifyAddress(String address) {
        if (address == null || address.isBlank()) return null;
        // Split on either comma or hashtag, take first part
        String simplified = address.split("[,#/]")[0].trim();
        return simplified.isBlank() ? null : simplified;
    }

    /**
     * Extract the street name portion by stripping a leading house number.
     * E.g., "123 Main Street" -> "Main Street", "Main Street" -> "Main Street"
     */
    private String extractStreetName(String street) {
        if (street == null || street.isBlank()) return null;
        // Strip a leading numeric house number (optionally followed by a letter, e.g. "12A")
        String stripped = street.replaceFirst("^\\d+[A-Za-z]?\\s+", "").trim();
        return stripped.isBlank() ? street : stripped;
    }

    /**
     * Result from a Nominatim API call.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record NominatimResult(double lat, double lon, String place_id, NominatimAddress address) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record NominatimAddress(String city, String country, String country_code) {}

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
            String url = geocodingApiUrl + "/search?q=" + encoded + "&format=json&addressdetails=1&limit=1";

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

            JavaType type =
                    objectMapper.getTypeFactory().constructCollectionLikeType(List.class, NominatimResult.class);
            List<NominatimResult> rows = objectMapper.readValue(response.body(), type);
            return rows.isEmpty() ? null : rows.get(0);
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

