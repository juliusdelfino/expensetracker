package com.delfino.expensetracker.util;

import java.util.List;
import java.util.Locale;

public final class LocaleSupport {

    public static final Locale DEFAULT_LOCALE = Locale.ENGLISH;
    public static final List<Locale> SUPPORTED_LOCALES = List.of(Locale.ENGLISH, Locale.GERMAN);

    private LocaleSupport() {
    }

    public static Locale normalizeToSupportedLocale(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_LOCALE;
        }
        Locale parsed = Locale.forLanguageTag(raw.trim());
        if (parsed.getLanguage().isBlank()) {
            return DEFAULT_LOCALE;
        }
        for (Locale supported : SUPPORTED_LOCALES) {
            if (supported.getLanguage().equalsIgnoreCase(parsed.getLanguage())) {
                return supported;
            }
        }
        return DEFAULT_LOCALE;
    }

    public static String normalizeLanguageTag(String raw) {
        return normalizeToSupportedLocale(raw).toLanguageTag();
    }
}

