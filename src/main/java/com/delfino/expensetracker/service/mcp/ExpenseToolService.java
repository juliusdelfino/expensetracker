package com.delfino.expensetracker.service.mcp;

import com.delfino.expensetracker.businesslogic.ExpenseAggregation;
import com.delfino.expensetracker.businesslogic.ExpenseDateRange;
import com.delfino.expensetracker.businesslogic.StoreCountryMatcher;
import com.delfino.expensetracker.service.CountryService;
import com.delfino.expensetracker.config.UserContext;
import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.ExpenseItem;
import com.delfino.expensetracker.model.Store;
import com.delfino.expensetracker.repository.ExpenseItemRepository;
import com.delfino.expensetracker.repository.ExpenseRepository;
import com.delfino.expensetracker.repository.StoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Provides @Tool-annotated methods that the LLM can call via Spring AI
 * to query the user's expense data.
 */
@Service
public class ExpenseToolService {

    private static final Logger log = LoggerFactory.getLogger(ExpenseToolService.class);

    private final ExpenseRepository expenseRepository;
    private final ExpenseItemRepository expenseItemRepository;
    private final StoreRepository storeRepository;
    private final UserContext userContext;
    private final CountryService countryService;

    public ExpenseToolService(ExpenseRepository expenseRepository,
                              ExpenseItemRepository expenseItemRepository,
                              StoreRepository storeRepository,
                              UserContext userContext,
                              CountryService countryService) {
        this.expenseRepository = expenseRepository;
        this.expenseItemRepository = expenseItemRepository;
        this.storeRepository = storeRepository;
        this.userContext = userContext;
        this.countryService = countryService;
    }

    // ─────────────────────────────────────────────────────────────
    // Tool 1: Look up an item's price, optionally filtered by store
    // ─────────────────────────────────────────────────────────────

    @Tool(description = "Find the price of a specific item from receipts, optionally filtered by store name. " +
            "Returns the most recent price, the store it was bought from, and the date. " +
            "Use this when the user asks about the price or cost of a specific product/item.")
    public String findItemPrice(
            @ToolParam(description = "The name of the item to search for, e.g. 'soy sauce', 'milk', 'bread'") String itemName,
            @ToolParam(description = "Optional store name to filter by, e.g. 'Josco', 'Walmart'. Pass empty string if not specified.") String storeName) {

        log.info("Tool call: findItemPrice(itemName={}, storeName={}, userId={})", itemName, storeName, userContext.getUserId());

        List<Expense> expenses = expenseRepository.findByUserIdAndDeletedFalse(userContext.getUserId());
        String itemLower = itemName.toLowerCase();
        String storeLower = StringUtils.hasText(storeName) ? storeName.toLowerCase() : null;
        Map<Long, Store> storeMap = storeLower != null ? buildFullStoreMap(expenses) : new HashMap<>();

        List<Map<String, Object>> matches = new ArrayList<>();
        for (Expense expense : expenses) {
            if (storeLower != null && !storeNameMatches(storeMap.get(expense.getId()), storeLower)) continue;
            appendItemMatches(matches, expense, itemLower, storeMap);
        }

        if (matches.isEmpty()) {
            return "No items found matching '" + itemName + "'"
                    + (storeLower != null ? " from store '" + storeName + "'" : "") + ".";
        }
        matches.sort((a, b) -> String.valueOf(b.get("date")).compareTo(String.valueOf(a.get("date"))));
        return formatItemResults(itemName, matches);
    }

    // ─────────────────────────────────────────────────────────────
    // Tool 2: Sum expenses by keyword/tag and date range
    // ─────────────────────────────────────────────────────────────

    @Tool(description = "Calculate the total expenses matching a keyword within a date range. " +
            "Searches in expense notes, category, tags, store city, and store country. " +
            "Use this when the user asks about total spending for a trip, event, category, time period, city, or country.")
    public String totalExpenses(
            @ToolParam(description = "Keyword to search for in notes, category, tags, store city, and store country. E.g. 'ski holiday', 'groceries', 'travel', 'Tokyo', 'Japan'. Pass empty string for all expenses.") String keyword,
            @ToolParam(description = "Start date in ISO format yyyy-MM-dd. Pass empty string if no start date filter.") String startDate,
            @ToolParam(description = "End date in ISO format yyyy-MM-dd. Pass empty string if no end date filter.") String endDate) {

        log.info("Tool call: totalExpenses(keyword={}, startDate={}, endDate={}, userId={})",
                keyword, startDate, endDate, userContext.getUserId());

        List<Expense> expenses = expenseRepository.findByUserIdAndDeletedFalse(userContext.getUserId());
        LocalDate start = ExpenseDateRange.parseOrNull(startDate);
        LocalDate end = ExpenseDateRange.parseOrNull(endDate);
        String keyLower = StringUtils.hasText(keyword) ? keyword.toLowerCase() : null;
        String resolvedCode = keyLower != null ? countryService.findCodeByName(keyword) : null;
        Map<Long, Store> storeMap = keyLower != null ? buildFullStoreMap(expenses) : Map.of();

        List<Expense> filtered = expenses.stream()
                .filter(e -> ExpenseDateRange.isWithin(e, start, end)
                        && matchesKeyword(e, keyLower, storeMap.get(e.getId()), resolvedCode))
                .toList();

        if (filtered.isEmpty()) {
            return "No expenses found"
                    + (keyLower != null ? " matching '" + keyword + "'" : "")
                    + (start != null ? " from " + startDate : "")
                    + (end != null ? " to " + endDate : "") + ".";
        }
        return buildTotalResponse(filtered, startDate, endDate, start, end);
    }

    // ─────────────────────────────────────────────────────────────
    // Tool 3: List recent expenses with optional category/date filters
    // ─────────────────────────────────────────────────────────────

    @Tool(description = "List recent expenses with optional filtering by category and date range. " +
            "Returns individual expense details. Use this when the user asks to see or list their expenses.")
    public String listExpenses(
            @ToolParam(description = "Category to filter by, e.g. 'Food', 'Transport'. Pass empty string for all categories.") String category,
            @ToolParam(description = "Start date in ISO format yyyy-MM-dd. Pass empty string if no start date filter.") String startDate,
            @ToolParam(description = "End date in ISO format yyyy-MM-dd. Pass empty string if no end date filter.") String endDate,
            @ToolParam(description = "Maximum number of results to return. Use 10 as default.") int limit) {

        log.info("Tool call: listExpenses(category={}, startDate={}, endDate={}, limit={}, userId={})",
                category, startDate, endDate, limit, userContext.getUserId());

        List<Expense> expenses = expenseRepository.findByUserIdAndDeletedFalse(userContext.getUserId());
        LocalDate start = ExpenseDateRange.parseOrNull(startDate);
        LocalDate end = ExpenseDateRange.parseOrNull(endDate);
        String catLower = StringUtils.hasText(category) ? category.toLowerCase() : null;

        List<Expense> filtered = expenses.stream()
                .filter(e -> ExpenseDateRange.isWithin(e, start, end)
                        && (catLower == null || containsIgnoreCase(e.getCategory(), catLower)))
                .sorted(Comparator.comparing(Expense::getTransactionDatetime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit > 0 ? limit : 10)
                .toList();

        if (filtered.isEmpty()) {
            return "No expenses found"
                    + (catLower != null ? " in category '" + category + "'" : "")
                    + (start != null ? " from " + startDate : "")
                    + (end != null ? " to " + endDate : "") + ".";
        }
        StringBuilder sb = new StringBuilder("Found ").append(filtered.size()).append(" expense(s):\n");
        filtered.forEach(e -> sb.append(formatExpenseLine(e)));
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────
    // Tool 4: Get expense summary / dashboard stats
    // ─────────────────────────────────────────────────────────────

    @Tool(description = "Get a summary of expenses for a date range, including total amount, number of expenses, " +
            "and breakdown by category. Use this for overview/dashboard-style questions.")
    public String getExpenseSummary(
            @ToolParam(description = "Start date in ISO format yyyy-MM-dd. Pass empty string for all time.") String startDate,
            @ToolParam(description = "End date in ISO format yyyy-MM-dd. Pass empty string for all time.") String endDate) {

        log.info("Tool call: getExpenseSummary(startDate={}, endDate={}, userId={})",
                startDate, endDate, userContext.getUserId());

        List<Expense> expenses = expenseRepository.findByUserIdAndDeletedFalse(userContext.getUserId());
        LocalDate start = ExpenseDateRange.parseOrNull(startDate);
        LocalDate end = ExpenseDateRange.parseOrNull(endDate);

        List<Expense> filtered = expenses.stream()
                .filter(e -> ExpenseDateRange.isWithin(e, start, end))
                .toList();

        if (filtered.isEmpty()) {
            return "No expenses found"
                    + (start != null ? " from " + startDate : "")
                    + (end != null ? " to " + endDate : "") + ".";
        }
        return buildSummaryResponse(filtered, startDate, endDate, start, end);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /** Loads every expense's store into a map keyed by expense ID. */
    private Map<Long, Store> buildFullStoreMap(List<Expense> expenses) {
        Map<Long, Store> map = new HashMap<>();
        for (Expense e : expenses) {
            if (e.getStoreId() != null) {
                storeRepository.findById(e.getStoreId()).ifPresent(s -> map.put(e.getId(), s));
            }
        }
        return map;
    }

    private static boolean storeNameMatches(Store store, String storeLower) {
        return store != null && containsIgnoreCase(store.getName(), storeLower);
    }

    /** Appends matching expense items to {@code matches}. */
    private void appendItemMatches(List<Map<String, Object>> matches, Expense expense,
                                   String itemLower, Map<Long, Store> storeMap) {
        Store store = resolveStore(expense, storeMap);
        expenseItemRepository.findByExpenseIdAndDeletedFalse(expense.getId()).stream()
                .filter(item -> containsIgnoreCase(item.getItemName(), itemLower))
                .forEach(item -> matches.add(buildItemMatchEntry(expense, item, store)));
    }

    /** Resolves the store for an expense, falling back to a DB lookup when the map is empty. */
    private Store resolveStore(Expense expense, Map<Long, Store> storeMap) {
        Store store = storeMap.get(expense.getId());
        if (store == null && expense.getStoreId() != null) {
            store = storeRepository.findById(expense.getStoreId()).orElse(null);
        }
        return store;
    }

    private static Map<String, Object> buildItemMatchEntry(Expense expense, ExpenseItem item, Store store) {
        Map<String, Object> match = new LinkedHashMap<>();
        match.put("itemName", item.getItemName());
        match.put("unitPrice", item.getUnitPrice());
        match.put("totalPrice", item.getTotalPrice());
        match.put("quantity", item.getQuantity());
        match.put("currency", expense.getCurrency());
        match.put("date", expense.getTransactionDatetime() != null
                ? expense.getTransactionDatetime().format(DateTimeFormatter.ISO_LOCAL_DATE) : "unknown");
        match.put("storeName", store != null ? store.getName() : "unknown");
        return match;
    }

    private static String formatItemResults(String itemName, List<Map<String, Object>> matches) {
        StringBuilder sb = new StringBuilder("Found ").append(matches.size())
                .append(" result(s) for '").append(itemName).append("':\n");
        for (Map<String, Object> m : matches) {
            sb.append("- ").append(m.get("itemName"))
                    .append(": ").append(m.get("unitPrice")).append(" ").append(m.get("currency"))
                    .append(" (qty: ").append(m.get("quantity")).append(")")
                    .append(" from ").append(m.get("storeName"))
                    .append(" on ").append(m.get("date")).append("\n");
        }
        return sb.toString();
    }

    /**
     * Returns {@code true} when the expense matches the keyword.
     * A {@code null} keyword means "match everything".
     */
    private boolean matchesKeyword(Expense e, String keyLower, Store store, String resolvedCode) {
        if (keyLower == null) return true;
        if (containsIgnoreCase(e.getNotes(), keyLower)) return true;
        if (containsIgnoreCase(e.getCategory(), keyLower)) return true;
        if (e.getTags() != null && e.getTags().stream().anyMatch(t -> containsIgnoreCase(t, keyLower))) return true;
        if (store == null) return false;
        if (containsIgnoreCase(store.getCity(), keyLower)) return true;
        return StoreCountryMatcher.matches(store, keyLower, resolvedCode, countryService::getName);
    }

    private static boolean containsIgnoreCase(String field, String keyword) {
        return StringUtils.hasText(field) && field.toLowerCase().contains(keyword);
    }

    private static String formatExpenseLine(Expense e) {
        String date = e.getTransactionDatetime() != null
                ? e.getTransactionDatetime().format(DateTimeFormatter.ISO_LOCAL_DATE) : "no date";
        String category = StringUtils.hasText(e.getCategory()) ? e.getCategory() : "no category";
        String notes = StringUtils.hasText(e.getNotes()) ? e.getNotes() : "";
        return "- " + e.getId() + " | " + date
                + " | " + e.getAmount() + " " + e.getCurrency()
                + " | " + category + " | " + notes + "\n";
    }

    private static String buildTotalResponse(List<Expense> filtered,
                                             String startDate, String endDate,
                                             LocalDate start, LocalDate end) {
        BigDecimal total = ExpenseAggregation.totalBaseAmount(filtered);
        Map<String, BigDecimal> byCategory = ExpenseAggregation.byCategory(filtered);
        boolean hasBaseAmounts = filtered.stream().anyMatch(e -> e.getAmountInBase() != null);
        String currency = hasBaseAmounts ? "base currency" : filtered.get(0).getCurrency();

        StringBuilder sb = new StringBuilder();
        sb.append("Total: ").append(total.toPlainString()).append(" (").append(currency).append(")")
                .append(" across ").append(filtered.size()).append(" expense(s)");
        if (start != null || end != null) {
            sb.append(" from ").append(start != null ? startDate : "beginning")
                    .append(" to ").append(end != null ? endDate : "now");
        }
        sb.append(".\n");
        if (byCategory.size() > 1) {
            sb.append("Breakdown by category:\n");
            byCategory.forEach((cat, amt) -> sb.append("  - ").append(cat).append(": ")
                    .append(amt.setScale(2, RoundingMode.HALF_UP).toPlainString()).append("\n"));
        }
        return sb.toString();
    }

    private static String buildSummaryResponse(List<Expense> filtered,
                                               String startDate, String endDate,
                                               LocalDate start, LocalDate end) {
        BigDecimal total = ExpenseAggregation.totalBaseAmount(filtered);
        Map<String, BigDecimal> byCategory = ExpenseAggregation.byCategory(filtered);
        Map<String, Long> countByCategory = new TreeMap<>();
        filtered.forEach(e -> countByCategory.merge(
                StringUtils.hasText(e.getCategory()) ? e.getCategory() : "Uncategorized", 1L, Long::sum));

        StringBuilder sb = new StringBuilder("Expense Summary");
        if (start != null || end != null) {
            sb.append(" (").append(start != null ? startDate : "all time")
                    .append(" to ").append(end != null ? endDate : "now").append(")");
        }
        sb.append(":\n")
                .append("Total: ").append(total.toPlainString()).append(" (base currency)\n")
                .append("Number of expenses: ").append(filtered.size()).append("\n")
                .append("Category breakdown:\n");
        byCategory.forEach((cat, amt) -> sb.append("  - ").append(cat).append(": ")
                .append(amt.setScale(2, RoundingMode.HALF_UP).toPlainString())
                .append(" (").append(countByCategory.get(cat)).append(" expense(s))\n"));
        return sb.toString();
    }
}

