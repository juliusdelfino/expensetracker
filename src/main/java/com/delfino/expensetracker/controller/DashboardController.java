package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.service.CountryService;
import com.delfino.expensetracker.dto.auth.UserToken;
import com.delfino.expensetracker.dto.dashboard.DashboardResponse;
import com.delfino.expensetracker.dto.dashboard.DiscoveryCard;
import com.delfino.expensetracker.dto.dashboard.DiscoveryCardExpense;
import com.delfino.expensetracker.dto.dashboard.GeoCountry;
import com.delfino.expensetracker.dto.dashboard.GeoPoint;
import com.delfino.expensetracker.dto.dashboard.TopExpense;
import com.delfino.expensetracker.dto.dashboard.TopItem;
import com.delfino.expensetracker.dto.dashboard.TopShop;
import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.ExpenseItem;
import com.delfino.expensetracker.model.ExpenseStatus;
import com.delfino.expensetracker.model.Store;
import com.delfino.expensetracker.model.User;
import com.delfino.expensetracker.repository.ExpenseItemRepository;
import com.delfino.expensetracker.repository.ExpenseRepository;
import com.delfino.expensetracker.repository.StoreRepository;
import com.delfino.expensetracker.service.ExpenseService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final CountryService countryService;

    public DashboardController(ExpenseRepository expenseRepository, StoreRepository storeRepository,
                               ExpenseItemRepository expenseItemRepository, ExpenseService expenseService,
                               CountryService countryService) {
        this.expenseRepository = expenseRepository;
        this.storeRepository = storeRepository;
        this.expenseItemRepository = expenseItemRepository;
        this.expenseService = expenseService;
        this.countryService = countryService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DashboardResponse> dashboard(@RequestParam(required = false) String startDate,
                                                       @RequestParam(required = false) String endDate,
                                                       @RequestParam(required = false) String category,
                                                       UserToken userToken) {
        long userId = userToken.getUserId();

        List<Expense> allExpenses = expenseRepository.findByUserIdAndDeletedFalse(userId);
        List<Expense> expenses = new ArrayList<>(allExpenses);

        Map<Long, Store> storeMap = new LinkedHashMap<>();
        for (Store s : storeRepository.findByUserId(userId)) {
            storeMap.put(s.getId(), s);
        }

        // Apply filters
        expenses = ExpenseController.filterByDateRange(expenses, startDate, endDate);
        if (category != null && !category.isBlank()) {
            expenses = expenses.stream()
                    .filter(e -> category.equalsIgnoreCase(e.getCategory()))
                    .toList();
        }

        TimeTotals timeTotals = computeTimeTotals(expenses);
        User user = expenseService.deriveAndSetBaseLocation(userId);

        Set<String> allCategories = allExpenses.stream()
                .map(Expense::getCategory).filter(Objects::nonNull)
                .collect(Collectors.toCollection(TreeSet::new));

        String minDate = allExpenses.stream().map(Expense::getTransactionDatetime).filter(Objects::nonNull)
                .map(dt -> dt.toLocalDate().toString()).min(String::compareTo).orElse(null);
        String maxDate = allExpenses.stream().map(Expense::getTransactionDatetime).filter(Objects::nonNull)
                .map(dt -> dt.toLocalDate().toString()).max(String::compareTo).orElse(null);

        PerPeriodStats stats = computePerPeriodStats(allExpenses);

        DashboardResponse response = DashboardResponse.builder()
                .monthlyTotals(timeTotals.monthly())
                .weeklyTotals(timeTotals.weekly())
                .annualTotals(timeTotals.annual())
                .categoryTotals(computeCategoryTotals(expenses))
                .timeline(computeTimeline(expenses))
                .geoData(buildGeoData(expenses, storeMap))
                .geoByCountry(buildGeoByCountry(expenses, storeMap))
                .topShops(buildTopShops(expenses, storeMap))
                .topItems(buildTopItems(expenses, storeMap))
                .discoveryCards(buildDiscoveryCards(allExpenses, user, storeMap))
                .categories(allCategories)
                .totalExpenses(expenses.size())
                .minDate(minDate)
                .maxDate(maxDate)
                .topExpenses(buildTopExpenses(expenses, storeMap))
                .perMonthTxCount(stats.perMonthTxCount())
                .perMonthTopCategory(stats.perMonthTopCategory())
                .perYearTxCount(stats.perYearTxCount())
                .perYearTopCategory(stats.perYearTopCategory())
                .build();

        return ResponseEntity.ok(response);
    }

    // --- Private helper records for internal decomposition ---

    private record TimeTotals(
            Map<String, BigDecimal> monthly,
            Map<String, BigDecimal> weekly,
            Map<String, BigDecimal> annual) {}

    private record PerPeriodStats(
            Map<String, Integer> perMonthTxCount,
            Map<String, String> perMonthTopCategory,
            Map<String, Integer> perYearTxCount,
            Map<String, String> perYearTopCategory) {}

    // --- Extracted helper methods ---

    private TimeTotals computeTimeTotals(List<Expense> expenses) {
        Map<String, BigDecimal> monthlyTotals = new TreeMap<>();
        Map<String, BigDecimal> weeklyTotals = new TreeMap<>();
        Map<String, BigDecimal> annualTotals = new TreeMap<>();
        for (Expense e : expenses) {
            if (e.getTransactionDatetime() == null) continue;
            BigDecimal amt = e.getBaseAmountOrAmount();
            LocalDate d = e.getTransactionDatetime().toLocalDate();
            monthlyTotals.merge(YearMonth.from(d).toString(), amt, BigDecimal::add);
            LocalDate weekStart = d.minusDays(d.getDayOfWeek().getValue() - 1);
            weeklyTotals.merge(weekStart.toString(), amt, BigDecimal::add);
            annualTotals.merge(String.valueOf(d.getYear()), amt, BigDecimal::add);
        }
        return new TimeTotals(monthlyTotals, weeklyTotals, annualTotals);
    }

    private Map<String, BigDecimal> computeCategoryTotals(List<Expense> expenses) {
        Map<String, BigDecimal> categoryTotals = new TreeMap<>();
        for (Expense e : expenses) {
            String cat = e.getCategory() != null ? e.getCategory() : "Uncategorized";
            categoryTotals.merge(cat, e.getBaseAmountOrAmount(), BigDecimal::add);
        }
        return categoryTotals;
    }

    private Map<String, BigDecimal> computeTimeline(List<Expense> expenses) {
        Map<String, BigDecimal> timeline = new TreeMap<>();
        for (Expense e : expenses) {
            if (e.getTransactionDatetime() != null) {
                timeline.merge(e.getTransactionDatetime().toLocalDate().toString(),
                        e.getBaseAmountOrAmount(), BigDecimal::add);
            }
        }
        return timeline;
    }

    private List<GeoPoint> buildGeoData(List<Expense> expenses, Map<Long, Store> storeMap) {
        List<GeoPoint> geoData = new ArrayList<>();
        for (Expense e : expenses) {
            var store = e.getStoreId() != null ? storeMap.get(e.getStoreId()) : null;
            if (store == null) continue;
            Double lat = store.getLatitude();
            Double lng = store.getLongitude();
            if ((lat == null || lng == null) && store.getCountry() != null) {
                double[] centroid = countryService.getLatLng(store.getCountry());
                if (centroid != null) { lat = centroid[0]; lng = centroid[1]; }
            }
            if (lat != null && lng != null) {
                geoData.add(new GeoPoint(
                        lat, lng,
                        store.getName(),
                        e.getAmountInBase() != null ? e.getAmountInBase() : e.getAmount(),
                        e.getCurrency(),
                        e.getTransactionDatetime(),
                        store.getCountry(),
                        countryService.getName(store.getCountry())
                ));
            }
        }
        return geoData;
    }

    private List<GeoCountry> buildGeoByCountry(List<Expense> expenses, Map<Long, Store> storeMap) {
        Map<String, GeoCountry.Builder> countryBuilderMap = new LinkedHashMap<>();
        for (Expense e : expenses) {
            var store = e.getStoreId() != null ? storeMap.get(e.getStoreId()) : null;
            if (store == null) continue;
            String country = store.getCountry();
            if (country == null || country.isBlank()) continue;

            GeoCountry.Builder builder = countryBuilderMap.computeIfAbsent(country, k -> {
                Double sLat = store.getLatitude(), sLng = store.getLongitude();
                if (sLat == null || sLng == null) {
                    double[] centroid = countryService.getLatLng(country);
                    if (centroid != null) { sLat = centroid[0]; sLng = centroid[1]; }
                }
                return GeoCountry.builder()
                        .country(country)
                        .countryName(countryService.getName(country))
                        .lat(sLat != null ? sLat : 0.0)
                        .lng(sLng != null ? sLng : 0.0);
            });

            builder.addAmount(e.getBaseAmountOrAmount());
            if (e.getTransactionDatetime() != null) {
                builder.updateDates(e.getTransactionDatetime().toLocalDate().toString());
            }
        }
        return countryBuilderMap.values().stream().map(GeoCountry.Builder::build).toList();
    }

    private List<TopShop> buildTopShops(List<Expense> expenses, Map<Long, Store> storeMap) {
        Map<String, Integer> shopVisitCounts = new LinkedHashMap<>();
        Map<String, List<Expense>> shopExpenses = new LinkedHashMap<>();
        for (Expense e : expenses) {
            var store = e.getStoreId() != null ? storeMap.get(e.getStoreId()) : null;
            if (store != null && store.getName() != null && !store.getName().isBlank()) {
                shopVisitCounts.merge(store.getName(), 1, Integer::sum);
                shopExpenses.computeIfAbsent(store.getName(), k -> new ArrayList<>()).add(e);
            }
        }
        return shopVisitCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(entry -> {
                    List<TopShop.TopShopTransaction> recent = shopExpenses.getOrDefault(entry.getKey(), List.of()).stream()
                            .filter(e -> e.getTransactionDatetime() != null)
                            .sorted((a, b) -> b.getTransactionDatetime().compareTo(a.getTransactionDatetime()))
                            .limit(3)
                            .map(e -> new TopShop.TopShopTransaction(
                                    e.getId(), e.getUrlId(),
                                    e.getCategory() != null ? e.getCategory() : "Uncategorized",
                                    e.getTransactionDatetime().toLocalDate().toString(),
                                    e.getAmount(), e.getCurrency()))
                            .toList();
                    return new TopShop(entry.getKey(), entry.getValue(), recent);
                })
                .toList();
    }

    private List<TopItem> buildTopItems(List<Expense> expenses, Map<Long, Store> storeMap) {
        Map<String, BigDecimal> itemCounts = new LinkedHashMap<>();
        // Track recent transactions per item name
        Map<String, List<TopItem.TopItemTransaction>> itemTransactions = new LinkedHashMap<>();

        for (Expense e : expenses) {
            String storeName = e.getStoreId() != null && storeMap.containsKey(e.getStoreId())
                    ? storeMap.get(e.getStoreId()).getName() : null;
            for (ExpenseItem item : expenseItemRepository.findByExpenseIdAndDeletedFalse(e.getId())) {
                if (item.getItemName() != null && !item.getItemName().isBlank()) {
                    BigDecimal qty = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ONE;
                    itemCounts.merge(item.getItemName(), qty, BigDecimal::add);
                    itemTransactions.computeIfAbsent(item.getItemName(), k -> new ArrayList<>())
                            .add(new TopItem.TopItemTransaction(
                                    e.getId(), e.getUrlId(),
                                    e.getTransactionDatetime() != null ? e.getTransactionDatetime().toLocalDate().toString() : null,
                                    item.getUnitPrice(), e.getCurrency(), storeName));
                }
            }
        }
        return itemCounts.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(5)
                .map(entry -> {
                    List<TopItem.TopItemTransaction> recent = itemTransactions.getOrDefault(entry.getKey(), List.of()).stream()
                            .filter(t -> t.date() != null)
                            .sorted((a, b) -> b.date().compareTo(a.date()))
                            .limit(3)
                            .toList();
                    return new TopItem(entry.getKey(), entry.getValue(), recent);
                })
                .toList();
    }

    private List<TopExpense> buildTopExpenses(List<Expense> expenses, Map<Long, Store> storeMap) {
        return expenses.stream()
                .filter(e -> ExpenseStatus.COMPLETED.equals(e.getStatus()))
                .sorted((a, b) -> b.getBaseAmountOrAmount().compareTo(a.getBaseAmountOrAmount()))
                .limit(5)
                .map(e -> {
                    String storeName = e.getStoreId() != null && storeMap.containsKey(e.getStoreId())
                            ? storeMap.get(e.getStoreId()).getName() : null;
                    String cat = e.getCategory() != null ? e.getCategory() : "Uncategorized";
                    String displayName = storeName != null && !storeName.isBlank()
                            ? cat + " \u2014 " + storeName : cat;
                    return new TopExpense(
                            e.getId(),
                            displayName,
                            e.getAmount(),
                            e.getCurrency(),
                            e.getBaseAmountOrAmount(),
                            e.getTransactionDatetime(),
                            e.getCategory()
                    );
                }).toList();
    }

    private PerPeriodStats computePerPeriodStats(List<Expense> allExpenses) {
        Map<String, Integer> perMonthTxCount = new TreeMap<>();
        Map<String, Map<String, BigDecimal>> perMonthCatTotals = new TreeMap<>();
        Map<String, Integer> perYearTxCount = new TreeMap<>();
        Map<String, Map<String, BigDecimal>> perYearCatTotals = new TreeMap<>();
        for (Expense e : allExpenses) {
            if (e.getTransactionDatetime() == null) continue;
            String month = YearMonth.from(e.getTransactionDatetime()).toString();
            String year = String.valueOf(e.getTransactionDatetime().getYear());
            String cat = e.getCategory() != null ? e.getCategory() : "Uncategorized";
            BigDecimal amt = e.getBaseAmountOrAmount();
            perMonthTxCount.merge(month, 1, Integer::sum);
            perMonthCatTotals.computeIfAbsent(month, k -> new TreeMap<>()).merge(cat, amt, BigDecimal::add);
            perYearTxCount.merge(year, 1, Integer::sum);
            perYearCatTotals.computeIfAbsent(year, k -> new TreeMap<>()).merge(cat, amt, BigDecimal::add);
        }
        Map<String, String> perMonthTopCategory = new TreeMap<>();
        perMonthCatTotals.forEach((m, cats) -> perMonthTopCategory.put(m,
                cats.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("-")));
        Map<String, String> perYearTopCategory = new TreeMap<>();
        perYearCatTotals.forEach((y, cats) -> perYearTopCategory.put(y,
                cats.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("-")));
        return new PerPeriodStats(perMonthTxCount, perMonthTopCategory, perYearTxCount, perYearTopCategory);
    }

    private List<DiscoveryCard> buildDiscoveryCards(List<Expense> allExpenses, User user, Map<Long, Store> storeMap) {
        String baseCity = user != null ? user.getBaseCity() : null;
        String baseCountry = user != null ? user.getBaseCountry() : null;

        Map<String, List<Expense>> groups = new LinkedHashMap<>();
        Map<String, String> keyToCity = new LinkedHashMap<>();
        Map<String, String> keyToCountry = new LinkedHashMap<>();

        for (Expense e : allExpenses) {
            if (e.getTransactionDatetime() == null) continue;
            var store = e.getStoreId() != null ? storeMap.get(e.getStoreId()) : null;
            if (store == null) continue;
            String country = store.getCountry();
            String city = store.getCity();
            if (country == null || country.isBlank()) continue;
            if (city != null && city.equalsIgnoreCase(baseCity) && country.equalsIgnoreCase(baseCountry)) continue;
            String ym = YearMonth.from(e.getTransactionDatetime()).toString();
            String cityKey = (city != null && !city.isBlank()) ? city.toLowerCase() : "_";
            String key = country + "|" + cityKey + "|" + ym;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
            keyToCity.putIfAbsent(key, (city != null && !city.isBlank()) ? city : null);
            keyToCountry.putIfAbsent(key, country);
        }

        List<String> keys = new ArrayList<>(groups.keySet());
        Collections.shuffle(keys);
        List<DiscoveryCard> cards = new ArrayList<>();
        int limit = Math.min(20, keys.size());
        for (int i = 0; i < limit; i++) {
            cards.add(buildSingleDiscoveryCard(keys.get(i), groups, keyToCity, storeMap));
        }
        return cards;
    }

    private DiscoveryCard buildSingleDiscoveryCard(String key, Map<String, List<Expense>> groups,
                                                    Map<String, String> keyToCity, Map<Long, Store> storeMap) {
        String[] parts = key.split("\\|");
        String country = parts[0];
        String ym = parts[2];
        String city = keyToCity.get(key);
        List<Expense> grp = groups.get(key);

        BigDecimal total = BigDecimal.ZERO;
        Set<String> shops = new LinkedHashSet<>();
        Set<String> distinctDates = new LinkedHashSet<>();
        Map<String, BigDecimal> currencyTotals = new LinkedHashMap<>();

        for (Expense exp : grp) {
            total = total.add(exp.getBaseAmountOrAmount());
            var s = exp.getStoreId() != null ? storeMap.get(exp.getStoreId()) : null;
            if (s != null && s.getName() != null) shops.add(s.getName());
            if (exp.getTransactionDatetime() != null) distinctDates.add(exp.getTransactionDatetime().toLocalDate().toString());
            if (exp.getCurrency() != null && exp.getAmount() != null) {
                currencyTotals.merge(exp.getCurrency(), exp.getAmount(), BigDecimal::add);
            }
        }

        String originalCurrency = null;
        BigDecimal originalTotal = null;
        if (!currencyTotals.isEmpty()) {
            var dominant = currencyTotals.entrySet().stream().max(Map.Entry.comparingByValue()).orElse(null);
            if (dominant != null) { originalCurrency = dominant.getKey(); originalTotal = dominant.getValue(); }
        }

        YearMonth yearMonth = YearMonth.parse(ym);
        String monthName = yearMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        int year = yearMonth.getYear();
        String locationLabel = (city != null) ? city + ", " + countryService.getName(country) : countryService.getName(country);

        List<DiscoveryCardExpense> topExpensesInCard = grp.stream()
                .sorted((a, b) -> b.getBaseAmountOrAmount().compareTo(a.getBaseAmountOrAmount()))
                .limit(3)
                .map(exp -> {
                    String sName = exp.getStoreId() != null && storeMap.containsKey(exp.getStoreId())
                            ? storeMap.get(exp.getStoreId()).getName() : null;
                    String cat = exp.getCategory() != null ? exp.getCategory() : "Uncategorized";
                    String displayName = sName != null && !sName.isBlank() ? cat + " \u2014 " + sName : cat;
                    return new DiscoveryCardExpense(
                            exp.getId(),
                            displayName,
                            exp.getAmount(),
                            exp.getCurrency(),
                            exp.getUrlId(),
                            exp.getBaseAmountOrAmount()
                    );
                }).toList();

        return DiscoveryCard.builder()
                .type("discovery")
                .country(country)
                .countryName(countryService.getName(country))
                .city(city)
                .locationLabel(locationLabel)
                .month(monthName)
                .year(year)
                .yearMonth(ym)
                .total(total)
                .count(grp.size())
                .daysStayed(distinctDates.size())
                .originalCurrency(originalCurrency)
                .originalTotal(originalTotal)
                .shops(new ArrayList<>(shops).subList(0, Math.min(3, shops.size())))
                .title("Expenses in " + locationLabel + " \u2014 " + monthName + " " + year)
                .topExpenses(topExpensesInCard)
                .build();
    }
}

