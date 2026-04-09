package com.delfino.expensetracker.businesslogic;

import com.delfino.expensetracker.model.Store;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Pure utility for matching a {@link Store} against a country filter string.
 * Country name resolution is injected as a {@link Function} so this class
 * remains free of Spring beans and can be tested in isolation.
 */
public final class StoreCountryMatcher {

    private StoreCountryMatcher() {}

    /**
     * Returns {@code true} if the store's country matches the given filter.
     *
     * <p>Matching is tried in order:
     * <ol>
     *   <li>Country code contains {@code filterLower} (substring, case-insensitive)</li>
     *   <li>Country code exactly equals {@code resolvedCode} (if non-blank)</li>
     *   <li>Human-readable country name (from {@code countryNameFunc}) contains {@code filterLower}</li>
     * </ol>
     *
     * @param store           the store to test
     * @param filterLower     lowercase filter string (keyword or dedicated country filter)
     * @param resolvedCode    optional country code pre-resolved from the filter string
     * @param countryNameFunc function mapping a country code to its display name
     */
    public static boolean matches(Store store,
                                  String filterLower,
                                  String resolvedCode,
                                  UnaryOperator<String> countryNameFunc) {
        if (!StringUtils.hasText(store.getCountry())) return false;
        String sc = store.getCountry().toLowerCase();
        if (sc.contains(filterLower)) return true;
        if (StringUtils.hasText(resolvedCode) && sc.equalsIgnoreCase(resolvedCode)) return true;
        String name = countryNameFunc.apply(store.getCountry());
        return StringUtils.hasText(name) && name.toLowerCase().contains(filterLower);
    }

    public static boolean countryMatches(String storeCountryCode, String otherCountryCode) {
        if (!StringUtils.hasText(storeCountryCode) || !StringUtils.hasText(otherCountryCode)) return false;
        return Objects.equals(storeCountryCode.toLowerCase(), otherCountryCode.toLowerCase());
    }
}

