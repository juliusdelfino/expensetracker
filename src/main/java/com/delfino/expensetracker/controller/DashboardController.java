package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.config.CountryConfig;
import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.ExpenseItem;
import com.delfino.expensetracker.model.ExpenseStatus;
import com.delfino.expensetracker.model.Store;
import com.delfino.expensetracker.model.User;
import com.delfino.expensetracker.repository.ExpenseItemRepository;
import com.delfino.expensetracker.repository.ExpenseRepository;
import com.delfino.expensetracker.repository.StoreRepository;
import com.delfino.expensetracker.service.ExpenseService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final ExpenseRepository expenseRepository;
    private final StoreRepository storeRepository;
    private final ExpenseItemRepository expenseItemRepository;
    private final ExpenseService expenseService;

    public DashboardController(ExpenseRepository expenseRepository, StoreRepository storeRepository,
                               ExpenseItemRepository expenseItemRepository, ExpenseService expenseService) {
        this.expenseRepository = expenseRepository;
        this.storeRepository = storeRepository;
        this.expenseItemRepository = expenseItemRepository;
        this.expenseService = expenseService;
    }

    @GetMapping
    public ResponseEntity<?> dashboard(@RequestParam(required = false) String startDate,
                                       @RequestParam(required = false) String endDate,
                                       @RequestParam(required = false) String category,
                                       HttpSession session) {
        UUID userId = getUserId(session);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

        List<Expense> allExpenses = expenseRepository.findByUserIdAndDeletedFalse(userId);
        List<Expense> expenses = new ArrayList<>(allExpenses);

        // Preload all stores for this user into a map for efficient lookup
        Map<UUID, Store> storeMap = new LinkedHashMap<>();
        for (Store s : storeRepository.findByUserId(userId)) {
            storeMap.put(s.getId(), s);
        }

        // Apply filters
        if (startDate != null && !startDate.isBlank()) {
            LocalDate start = LocalDate.parse(startDate);
            expenses = expenses.stream()
                    .filter(e -> e.getTransactionDatetime() != null
                            && !e.getTransactionDatetime().toLocalDate().isBefore(start))
                    .toList();
        }
        if (endDate != null && !endDate.isBlank()) {
            LocalDate end = LocalDate.parse(endDate);
            expenses = expenses.stream()
                    .filter(e -> e.getTransactionDatetime() != null
                            && !e.getTransactionDatetime().toLocalDate().isAfter(end))
                    .toList();
        }
        if (category != null && !category.isBlank()) {
            expenses = expenses.stream()
                    .filter(e -> category.equalsIgnoreCase(e.getCategory()))
                    .toList();
        }

        // Monthly totals (in base currency)
        Map<String, BigDecimal> monthlyTotals = new TreeMap<>();
        for (Expense e : expenses) {
            if (e.getTransactionDatetime() != null) {
                String month = YearMonth.from(e.getTransactionDatetime()).toString();
                BigDecimal amt = getBaseAmount(e);
                monthlyTotals.merge(month, amt, BigDecimal::add);
            }
        }

        // Weekly totals
        Map<String, BigDecimal> weeklyTotals = new TreeMap<>();
        for (Expense e : expenses) {
            if (e.getTransactionDatetime() != null) {
                LocalDate d = e.getTransactionDatetime().toLocalDate();
                // ISO week: Monday-based, get the Monday of that week
                LocalDate weekStart = d.minusDays(d.getDayOfWeek().getValue() - 1);
                String weekLabel = weekStart.toString();
                BigDecimal amt = getBaseAmount(e);
                weeklyTotals.merge(weekLabel, amt, BigDecimal::add);
            }
        }

        // Annual totals
        Map<String, BigDecimal> annualTotals = new TreeMap<>();
        for (Expense e : expenses) {
            if (e.getTransactionDatetime() != null) {
                String year = String.valueOf(e.getTransactionDatetime().getYear());
                BigDecimal amt = getBaseAmount(e);
                annualTotals.merge(year, amt, BigDecimal::add);
            }
        }

        // Category breakdown
        Map<String, BigDecimal> categoryTotals = new TreeMap<>();
        for (Expense e : expenses) {
            String cat = e.getCategory() != null ? e.getCategory() : "Uncategorized";
            BigDecimal amt = getBaseAmount(e);
            categoryTotals.merge(cat, amt, BigDecimal::add);
        }

        // Timeline (daily)
        Map<String, BigDecimal> timeline = new TreeMap<>();
        for (Expense e : expenses) {
            if (e.getTransactionDatetime() != null) {
                String day = e.getTransactionDatetime().toLocalDate().toString();
                BigDecimal amt = getBaseAmount(e);
                timeline.merge(day, amt, BigDecimal::add);
            }
        }

        // Geo data (individual markers) - use store lat/lng or fall back to country centroid
        List<Map<String, Object>> geoData = new ArrayList<>();
        for (Expense e : expenses) {
            var store = e.getStoreId() != null ? storeMap.get(e.getStoreId()) : null;
            if (store != null) {
                Double lat = store.getLatitude();
                Double lng = store.getLongitude();
                // Fall back to country centroid if no lat/lng
                if ((lat == null || lng == null) && store.getCountry() != null) {
                    double[] centroid = CountryConfig.getLatLng(store.getCountry());
                    if (centroid != null) { lat = centroid[0]; lng = centroid[1]; }
                }
                if (lat != null && lng != null) {
                    Map<String, Object> point = new LinkedHashMap<>();
                    point.put("lat", lat);
                    point.put("lng", lng);
                    point.put("name", store.getName());
                    point.put("amount", e.getAmountInBase() != null ? e.getAmountInBase() : e.getAmount());
                    point.put("currency", e.getCurrency());
                    point.put("date", e.getTransactionDatetime());
                    point.put("country", store.getCountry());
                    point.put("countryName", CountryConfig.getName(store.getCountry()));
                    geoData.add(point);
                }
            }
        }

        // Geo data aggregated by country - use country centroid coordinates
        Map<String, Map<String, Object>> countryDataMap = new LinkedHashMap<>();
        for (Expense e : expenses) {
            var store = e.getStoreId() != null ? storeMap.get(e.getStoreId()) : null;
            if (store != null) {
                String country = store.getCountry();
                if (country != null && !country.isBlank()) {
                    countryDataMap.computeIfAbsent(country, k -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("country", country);
                        m.put("countryName", CountryConfig.getName(country));
                        Double sLat = store.getLatitude();
                        Double sLng = store.getLongitude();
                        if (sLat == null || sLng == null) {
                            double[] centroid = CountryConfig.getLatLng(country);
                            if (centroid != null) { sLat = centroid[0]; sLng = centroid[1]; }
                        }
                        m.put("lat", sLat != null ? sLat : 0.0);
                        m.put("lng", sLng != null ? sLng : 0.0);
                        m.put("total", BigDecimal.ZERO);
                        m.put("count", 0);
                        m.put("minDate", e.getTransactionDatetime() != null ? e.getTransactionDatetime().toLocalDate().toString() : null);
                        m.put("maxDate", e.getTransactionDatetime() != null ? e.getTransactionDatetime().toLocalDate().toString() : null);
                        return m;
                    });
                    Map<String, Object> cd = countryDataMap.get(country);
                    BigDecimal amt = getBaseAmount(e);
                    cd.put("total", ((BigDecimal) cd.get("total")).add(amt));
                    cd.put("count", (int) cd.get("count") + 1);
                    if (e.getTransactionDatetime() != null) {
                        String dateStr = e.getTransactionDatetime().toLocalDate().toString();
                        if (cd.get("minDate") == null || dateStr.compareTo((String) cd.get("minDate")) < 0)
                            cd.put("minDate", dateStr);
                        if (cd.get("maxDate") == null || dateStr.compareTo((String) cd.get("maxDate")) > 0)
                            cd.put("maxDate", dateStr);
                    }
                }
            }
        }
        List<Map<String, Object>> geoByCountry = new ArrayList<>(countryDataMap.values());

        // Most visited shops (from filtered expenses)
        Map<String, Integer> shopVisitCounts = new LinkedHashMap<>();
        for (Expense e : expenses) {
            var store = e.getStoreId() != null ? storeMap.get(e.getStoreId()) : null;
            if (store != null && store.getName() != null && !store.getName().isBlank()) {
                shopVisitCounts.merge(store.getName(), 1, Integer::sum);
            }
        }
        List<Map<String, Object>> topShops = shopVisitCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(entry -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", entry.getKey());
                    m.put("visits", entry.getValue());
                    return m;
                })
                .toList();

        // Most bought items (from filtered expenses)
        Map<String, BigDecimal> itemCounts = new LinkedHashMap<>();
        for (Expense e : expenses) {
            List<ExpenseItem> items = expenseItemRepository.findByExpenseIdAndDeletedFalse(e.getId());
            for (ExpenseItem item : items) {
                if (item.getItemName() != null && !item.getItemName().isBlank()) {
                    BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ONE;
                    itemCounts.merge(item.getItemName(), qty, BigDecimal::add);
                }
            }
        }
        List<Map<String, Object>> topItems = itemCounts.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(10)
                .map(entry -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", entry.getKey());
                    m.put("count", entry.getValue());
                    return m;
                })
                .toList();

        // Discovery cards: random country+month combos from all expenses (unfiltered)
        // Only show places outside the user's base city and country
        User user = expenseService.deriveAndSetBaseLocation(userId);
        List<Map<String, Object>> discoveryCards = buildDiscoveryCards(allExpenses, user, storeMap);

        // All categories for filter dropdown
        Set<String> allCategories = allExpenses.stream()
                .map(Expense::getCategory)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(TreeSet::new));

        // Min/max dates for filter defaults
        String minDate = allExpenses.stream()
                .map(Expense::getTransactionDatetime)
                .filter(Objects::nonNull)
                .map(dt -> dt.toLocalDate().toString())
                .min(String::compareTo)
                .orElse(null);
        String maxDate = allExpenses.stream()
                .map(Expense::getTransactionDatetime)
                .filter(Objects::nonNull)
                .map(dt -> dt.toLocalDate().toString())
                .max(String::compareTo)
                .orElse(null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("monthlyTotals", monthlyTotals);
        result.put("weeklyTotals", weeklyTotals);
        result.put("annualTotals", annualTotals);
        result.put("categoryTotals", categoryTotals);
        result.put("timeline", timeline);
        result.put("geoData", geoData);
        result.put("geoByCountry", geoByCountry);
        result.put("categories", allCategories);
        result.put("totalExpenses", expenses.size());
        result.put("minDate", minDate);
        result.put("maxDate", maxDate);
        // Top expenses by amount in base currency (from filtered expenses)
        List<Map<String, Object>> topExpenses = expenses.stream()
                .filter(e -> ExpenseStatus.COMPLETED.equals(e.getStatus()))
                .sorted((a, b) -> {
                    BigDecimal ba = getBaseAmount(b);
                    BigDecimal aa = getBaseAmount(a);
                    return ba.compareTo(aa);
                })
                .limit(5)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", e.getId());
                    String storeName = e.getStoreId() != null && storeMap.containsKey(e.getStoreId())
                            ? storeMap.get(e.getStoreId()).getName() : null;
                    String cat = e.getCategory() != null ? e.getCategory() : "Uncategorized";
                    String displayName = storeName != null && !storeName.isBlank()
                            ? cat + " — " + storeName : cat;
                    m.put("displayName", displayName);
                    m.put("amount", e.getAmount());
                    m.put("currency", e.getCurrency());
                    m.put("amountInBase", getBaseAmount(e));
                    m.put("transactionDatetime", e.getTransactionDatetime());
                    m.put("category", e.getCategory());
                    return m;
                })
                .toList();

        result.put("topShops", topShops);
        result.put("topItems", topItems);
        result.put("topExpenses", topExpenses);
        result.put("discoveryCards", discoveryCards);

        // Per-month and per-year stats for hero card (computed from ALL expenses, not filtered)
        Map<String, Integer> perMonthTxCount = new TreeMap<>();
        Map<String, Map<String, BigDecimal>> perMonthCatTotals = new TreeMap<>();
        Map<String, Integer> perYearTxCount = new TreeMap<>();
        Map<String, Map<String, BigDecimal>> perYearCatTotals = new TreeMap<>();
        for (Expense e : allExpenses) {
            if (e.getTransactionDatetime() != null) {
                String month = YearMonth.from(e.getTransactionDatetime()).toString();
                String year = String.valueOf(e.getTransactionDatetime().getYear());
                String cat = e.getCategory() != null ? e.getCategory() : "Uncategorized";
                BigDecimal amt = getBaseAmount(e);

                perMonthTxCount.merge(month, 1, Integer::sum);
                perMonthCatTotals.computeIfAbsent(month, k -> new TreeMap<>()).merge(cat, amt, BigDecimal::add);

                perYearTxCount.merge(year, 1, Integer::sum);
                perYearCatTotals.computeIfAbsent(year, k -> new TreeMap<>()).merge(cat, amt, BigDecimal::add);
            }
        }
        // Convert cat totals to top category strings
        Map<String, String> perMonthTopCategory = new TreeMap<>();
        perMonthCatTotals.forEach((month, cats) ->
                perMonthTopCategory.put(month, cats.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey).orElse("-")));
        Map<String, String> perYearTopCategory = new TreeMap<>();
        perYearCatTotals.forEach((year, cats) ->
                perYearTopCategory.put(year, cats.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey).orElse("-")));

        result.put("perMonthTxCount", perMonthTxCount);
        result.put("perMonthTopCategory", perMonthTopCategory);
        result.put("perYearTxCount", perYearTxCount);
        result.put("perYearTopCategory", perYearTopCategory);

        return ResponseEntity.ok(result);
    }

    /**
     * Build random "discovery" cards from all-time expenses: e.g. "Expenses in Spain on July 2025"
     * Only includes places outside the user's base city and country.
     */
    private List<Map<String, Object>> buildDiscoveryCards(List<Expense> allExpenses, User user, Map<UUID, Store> storeMap) {
        String baseCity = user != null ? user.getBaseCity() : null;
        String baseCountry = user != null ? user.getBaseCountry() : null;

        // Group by city+country+yearMonth
        Map<String, List<Expense>> groups = new LinkedHashMap<>();
        Map<String, String> keyToCity = new LinkedHashMap<>();
        Map<String, String> keyToCountry = new LinkedHashMap<>();

        for (Expense e : allExpenses) {
            if (e.getTransactionDatetime() == null) continue;
            var store = e.getStoreId() != null ? storeMap.get(e.getStoreId()) : null;
            if (store != null) {
                String country = store.getCountry();
                String city = store.getCity();
                if (country != null && !country.isBlank()) {
                    if (city != null && city.equalsIgnoreCase(baseCity)
                            && country.equalsIgnoreCase(baseCountry)) {
                        continue; // Skip — this is the user's home location
                    }
                    String ym = YearMonth.from(e.getTransactionDatetime()).toString();
                    String cityKey = (city != null && !city.isBlank()) ? city.toLowerCase() : "_";
                    String key = country + "|" + cityKey + "|" + ym;
                    groups.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
                    keyToCity.putIfAbsent(key, (city != null && !city.isBlank()) ? city : null);
                    keyToCountry.putIfAbsent(key, country);
                }
            }
        }

        List<String> keys = new ArrayList<>(groups.keySet());
        Collections.shuffle(keys);
        List<Map<String, Object>> cards = new ArrayList<>();
        int limit = Math.min(20, keys.size());
        for (int i = 0; i < limit; i++) {
            String key = keys.get(i);
            String[] parts = key.split("\\|");
            String country = parts[0];
            String ym = parts[2];
            String city = keyToCity.get(key);
            List<Expense> grp = groups.get(key);

            BigDecimal total = BigDecimal.ZERO;
            Set<String> shops = new LinkedHashSet<>();
            Set<String> distinctDates = new LinkedHashSet<>();

            // Track original currency spending: currency -> total
            Map<String, BigDecimal> currencyTotals = new LinkedHashMap<>();

            for (Expense exp : grp) {
                total = total.add(getBaseAmount(exp));
                var s = exp.getStoreId() != null ? storeMap.get(exp.getStoreId()) : null;
                if (s != null && s.getName() != null) shops.add(s.getName());
                if (exp.getTransactionDatetime() != null) {
                    distinctDates.add(exp.getTransactionDatetime().toLocalDate().toString());
                }
                // Accumulate original currency amounts
                if (exp.getCurrency() != null && exp.getAmount() != null) {
                    currencyTotals.merge(exp.getCurrency(), exp.getAmount(), BigDecimal::add);
                }
            }

            int daysStayed = distinctDates.size();

            // Pick the dominant original currency (most spent in native currency)
            String originalCurrency = null;
            BigDecimal originalTotal = null;
            if (!currencyTotals.isEmpty()) {
                // Use the currency with the highest total
                Map.Entry<String, BigDecimal> dominant = currencyTotals.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .orElse(null);
                if (dominant != null) {
                    originalCurrency = dominant.getKey();
                    originalTotal = dominant.getValue();
                }
            }

            YearMonth yearMonth = YearMonth.parse(ym);
            String monthName = yearMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
            int year = yearMonth.getYear();

            String locationLabel = (city != null)
                    ? city + ", " + CountryConfig.getName(country)
                    : CountryConfig.getName(country);

            // Top 3 most expensive expenses within this group
            List<Map<String, Object>> topExpensesInCard = grp.stream()
                    .sorted((a, b) -> getBaseAmount(b).compareTo(getBaseAmount(a)))
                    .limit(3)
                    .map(exp -> {
                        Map<String, Object> em = new LinkedHashMap<>();
                        em.put("id", exp.getId());
                        String sName = exp.getStoreId() != null && storeMap.containsKey(exp.getStoreId())
                                ? storeMap.get(exp.getStoreId()).getName() : null;
                        String cat = exp.getCategory() != null ? exp.getCategory() : "Uncategorized";
                        em.put("displayName", sName != null && !sName.isBlank() ? cat + " — " + sName : cat);
                        em.put("amount", exp.getAmount());
                        em.put("currency", exp.getCurrency());
                        em.put("amountInBase", getBaseAmount(exp));
                        return em;
                    })
                    .toList();

            Map<String, Object> card = new LinkedHashMap<>();
            card.put("type", "discovery");
            card.put("country", country);
            card.put("countryName", CountryConfig.getName(country));
            card.put("city", city);
            card.put("locationLabel", locationLabel);
            card.put("month", monthName);
            card.put("year", year);
            card.put("yearMonth", ym);
            card.put("total", total);
            card.put("count", grp.size());
            card.put("daysStayed", daysStayed);
            card.put("originalCurrency", originalCurrency);
            card.put("originalTotal", originalTotal);
            card.put("shops", new ArrayList<>(shops).subList(0, Math.min(3, shops.size())));
            card.put("title", "Expenses in " + locationLabel + " — " + monthName + " " + year);
            card.put("topExpenses", topExpensesInCard);
            cards.add(card);
        }
        return cards;
    }

    private BigDecimal getBaseAmount(Expense e) {
        return e.getAmountInBase() != null ? e.getAmountInBase()
                : (e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO);
    }

    private UUID getUserId(HttpSession session) {
        String id = (String) session.getAttribute("userId");
        return id != null ? UUID.fromString(id) : null;
    }
}

