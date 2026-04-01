package com.delfino.expensetracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Binds the country-mapping.yml file (loaded via spring.config.import).
 * Each entry: code -> { name, lat, lng }
 */
@Component
@ConfigurationProperties(prefix = "")
public class CountryProperties {

    private Map<String, Map<String, Object>> countries = new LinkedHashMap<>();

    public Map<String, Map<String, Object>> getCountries() { return countries; }
    public void setCountries(Map<String, Map<String, Object>> countries) { this.countries = countries; }
}

