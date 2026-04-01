package com.delfino.expensetracker.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Country code to name and centroid coordinates mapping.
 * Data is loaded from country-mapping.yml via CountryProperties.
 * Used for geo map display and searching expenses by country name.
 */
@Component
public class CountryConfig {

    public record CountryInfo(String name, double lat, double lng) {}

    private static final Map<String, CountryInfo> COUNTRIES = new LinkedHashMap<>();

    private final CountryProperties countryProperties;

    public CountryConfig(CountryProperties countryProperties) {
        this.countryProperties = countryProperties;
    }

    @PostConstruct
    public void init() {
        COUNTRIES.clear();
        countryProperties.getCountries().forEach((code, props) -> {
            String name = String.valueOf(props.getOrDefault("name", code));
            double lat = toDouble(props.getOrDefault("lat", 0.0));
            double lng = toDouble(props.getOrDefault("lng", 0.0));
            COUNTRIES.put(code.toUpperCase(), new CountryInfo(name, lat, lng));
        });
    }

    private static double toDouble(Object val) {
        if (val instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(val)); } catch (NumberFormatException e) { return 0.0; }
    }

    public static Map<String, CountryInfo> getAll() { return COUNTRIES; }

    public static CountryInfo get(String code) {
        if (code == null) return null;
        return COUNTRIES.get(code.toUpperCase());
    }

    public static String getName(String code) {
        CountryInfo info = get(code);
        return info != null ? info.name() : code;
    }

    public static double[] getLatLng(String code) {
        CountryInfo info = get(code);
        return info != null ? new double[]{info.lat(), info.lng()} : null;
    }

    /**
     * Find country code by name (case-insensitive partial match).
     */
    public static String findCodeByName(String name) {
        if (name == null || name.isBlank()) return null;
        String lower = name.toLowerCase();
        if (COUNTRIES.containsKey(name.toUpperCase())) return name.toUpperCase();
        for (var entry : COUNTRIES.entrySet()) {
            if (entry.getValue().name().toLowerCase().contains(lower)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
