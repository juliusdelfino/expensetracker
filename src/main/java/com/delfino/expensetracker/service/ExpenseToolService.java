package com.delfino.expensetracker.service;

import com.delfino.expensetracker.config.CountryConfig;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Provides @Tool-annotated methods that the LLM can call via Spring AI
 * to query the user's expense data. Each method is automatically exposed
 * through the MCP server and available to the ChatClient during tool-calling.
 */
@Service
public class ExpenseToolService {

    private static final Logger log = LoggerFactory.getLogger(ExpenseToolService.class);

    private final ExpenseRepository expenseRepository;
    private final ExpenseItemRepository expenseItemRepository;
    private final StoreRepository storeRepository;
    private final UserContext userContext;

    public ExpenseToolService(ExpenseRepository expenseRepository,
                              ExpenseItemRepository expenseItemRepository,
                              StoreRepository storeRepository,
                              UserContext userContext) {
        this.expenseRepository = expenseRepository;
        this.expenseItemRepository = expenseItemRepository;
        this.storeRepository = storeRepository;
        this.userContext = userContext;
    }

    // ─────────────────────────────────────────────────────────────
    // Tool 1: Look up an item's price, optionally filtered by store
    // Example question: "How much is soy sauce from Josco?"
    // ─────────────────────────────────────────────────────────────

    @Tool(description = "Find the price of a specific item from receipts, optionally filtered by store name. " +
            "Returns the most recent price, the store it was bought from, and the date. " +
            "Use this when the user asks about the price or cost of a specific product/item.")
    public String findItemPrice(
            @ToolParam(description = "The name of the item to search for, e.g. 'soy sauce', 'milk', 'bread'") String itemName,
            @ToolParam(description = "Optional store name to filter by, e.g. 'Josco', 'Walmart'. Pass empty string if not specified.") String storeName) {

        log.info("Tool call: findItemPrice(itemName={}, storeName={}, userId={})", itemName, storeName, userContext.getUserId());

        Long userId = userContext.getUserId();
        List<Expense> expenses = expenseRepository.findByUserIdAndDeletedFalse(userId);

        // Build a map of expenseId -> Store for filtering
        Map<Long, Store> storeMap = new HashMap<>();
        if (storeName != null && !storeName.isBlank()) {
            for (Expense e : expenses) {
                if (e.getStoreId() != null) {
                    storeRepository.findById(e.getStoreId()).ifPresent(s -> storeMap.put(e.getId(), s));
                }
            }
        }

        String itemLower = itemName.toLowerCase();
        String storeLower = (storeName != null && !storeName.isBlank()) ? storeName.toLowerCase() : null;

        // Search through all expense items matching the item name
        List<Map<String, Object>> matches = new ArrayList<>();

        for (Expense expense : expenses) {
            // If store filter is specified, check if the store matches
            if (storeLower != null) {
                Store store = storeMap.get(expense.getId());
                if (store == null || store.getName() == null ||
                        !store.getName().toLowerCase().contains(storeLower)) {
                    continue;
                }
            }

            List<ExpenseItem> items = expenseItemRepository.findByExpenseIdAndDeletedFalse(expense.getId());
            for (ExpenseItem item : items) {
                if (item.getItemName() != null && item.getItemName().toLowerCase().contains(itemLower)) {
                    Map<String, Object> match = new LinkedHashMap<>();
                    match.put("itemName", item.getItemName());
                    match.put("unitPrice", item.getUnitPrice());
                    match.put("totalPrice", item.getTotalPrice());
                    match.put("quantity", item.getQuantity());
                    match.put("currency", expense.getCurrency());
                    match.put("date", expense.getTransactionDatetime() != null
                            ? expense.getTransactionDatetime().format(DateTimeFormatter.ISO_LOCAL_DATE) : "unknown");

                    Store store = storeMap.getOrDefault(expense.getId(), null);
                    if (store == null && expense.getStoreId() != null) {
                        store = storeRepository.findById(expense.getStoreId()).orElse(null);
                    }
                    match.put("storeName", store != null ? store.getName() : "unknown");
                    matches.add(match);
                }
            }
        }

        if (matches.isEmpty()) {
            return "No items found matching '" + itemName + "'"
                    + (storeLower != null ? " from store '" + storeName + "'" : "") + ".";
        }

        // Sort by date descending (most recent first)
        matches.sort((a, b) -> String.valueOf(b.get("date")).compareTo(String.valueOf(a.get("date"))));

        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(matches.size()).append(" result(s) for '").append(itemName).append("':\n");
        for (Map<String, Object> m : matches) {
            sb.append("- ").append(m.get("itemName"))
                    .append(": ").append(m.get("unitPrice")).append(" ").append(m.get("currency"))
                    .append(" (qty: ").append(m.get("quantity")).append(")")
                    .append(" from ").append(m.get("storeName"))
                    .append(" on ").append(m.get("date"))
                    .append("\n");
        }
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────
    // Tool 2: Sum expenses by keyword/tag and date range
    // Example question: "How much is total expenses for the ski holiday last year?"
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

        Long userId = userContext.getUserId();
        List<Expense> expenses = expenseRepository.findByUserIdAndDeletedFalse(userId);

        LocalDate start = (startDate != null && !startDate.isBlank()) ? LocalDate.parse(startDate) : null;
        LocalDate end = (endDate != null && !endDate.isBlank()) ? LocalDate.parse(endDate) : null;
        String keyLower = (keyword != null && !keyword.isBlank()) ? keyword.toLowerCase() : null;

        // Resolve keyword as possible country code for matching
        String resolvedCountryCode = keyLower != null ? CountryConfig.findCodeByName(keyword) : null;

        // Build store lookup map for city/country keyword matching
        Map<Long, Store> storeMap = new HashMap<>();
        if (keyLower != null) {
            for (Expense e : expenses) {
                if (e.getStoreId() != null) {
                    storeRepository.findById(e.getStoreId()).ifPresent(s -> storeMap.put(e.getId(), s));
                }
            }
        }

        List<Expense> filtered = expenses.stream()
                .filter(e -> {
                    if (start != null && e.getTransactionDatetime() != null
                            && e.getTransactionDatetime().toLocalDate().isBefore(start)) return false;
                    if (end != null && e.getTransactionDatetime() != null
                            && e.getTransactionDatetime().toLocalDate().isAfter(end)) return false;
                    if (keyLower != null) {
                        boolean matchNotes = e.getNotes() != null && e.getNotes().toLowerCase().contains(keyLower);
                        boolean matchCategory = e.getCategory() != null && e.getCategory().toLowerCase().contains(keyLower);
                        boolean matchTags = e.getTags() != null && e.getTags().stream()
                                .anyMatch(t -> t.toLowerCase().contains(keyLower));
                        // Also search store city and country
                        boolean matchCity = false;
                        boolean matchCountry = false;
                        Store store = storeMap.get(e.getId());
                        if (store != null) {
                            matchCity = store.getCity() != null && store.getCity().toLowerCase().contains(keyLower);
                            if (store.getCountry() != null) {
                                String sc = store.getCountry().toLowerCase();
                                matchCountry = sc.contains(keyLower);
                                if (!matchCountry && resolvedCountryCode != null) {
                                    matchCountry = sc.equalsIgnoreCase(resolvedCountryCode);
                                }
                                if (!matchCountry) {
                                    String countryName = CountryConfig.getName(store.getCountry());
                                    matchCountry = countryName != null && countryName.toLowerCase().contains(keyLower);
                                }
                            }
                        }
                        if (!matchNotes && !matchCategory && !matchTags && !matchCity && !matchCountry) return false;
                    }
                    return true;
                })
                .toList();

        if (filtered.isEmpty()) {
            return "No expenses found" +
                    (keyLower != null ? " matching '" + keyword + "'" : "") +
                    (start != null ? " from " + startDate : "") +
                    (end != null ? " to " + endDate : "") + ".";
        }

        BigDecimal total = filtered.stream()
                .map(e -> e.getAmountInBase() != null ? e.getAmountInBase()
                        : (e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        // Group by category
        Map<String, BigDecimal> byCategory = new TreeMap<>();
        for (Expense e : filtered) {
            String cat = e.getCategory() != null && !e.getCategory().isBlank() ? e.getCategory() : "Uncategorized";
            BigDecimal amt = e.getAmountInBase() != null ? e.getAmountInBase()
                    : (e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO);
            byCategory.merge(cat, amt, BigDecimal::add);
        }

        // Determine the common currency (base currency)
        String currency = filtered.stream()
                .filter(e -> e.getAmountInBase() != null)
                .findFirst()
                .map(e -> "base currency")
                .orElse(filtered.get(0).getCurrency());

        StringBuilder sb = new StringBuilder();
        sb.append("Total: ").append(total.toPlainString()).append(" (").append(currency).append(")");
        sb.append(" across ").append(filtered.size()).append(" expense(s)");
        if (start != null || end != null) {
            sb.append(" from ").append(start != null ? startDate : "beginning")
                    .append(" to ").append(end != null ? endDate : "now");
        }
        sb.append(".\n");

        if (byCategory.size() > 1) {
            sb.append("Breakdown by category:\n");
            for (Map.Entry<String, BigDecimal> entry : byCategory.entrySet()) {
                sb.append("  - ").append(entry.getKey()).append(": ")
                        .append(entry.getValue().setScale(2, RoundingMode.HALF_UP).toPlainString()).append("\n");
            }
        }

        return sb.toString();
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

        Long userId = userContext.getUserId();
        List<Expense> expenses = expenseRepository.findByUserIdAndDeletedFalse(userId);

        LocalDate start = (startDate != null && !startDate.isBlank()) ? LocalDate.parse(startDate) : null;
        LocalDate end = (endDate != null && !endDate.isBlank()) ? LocalDate.parse(endDate) : null;
        String catLower = (category != null && !category.isBlank()) ? category.toLowerCase() : null;

        List<Expense> filtered = expenses.stream()
                .filter(e -> {
                    if (start != null && e.getTransactionDatetime() != null
                            && e.getTransactionDatetime().toLocalDate().isBefore(start)) return false;
                    if (end != null && e.getTransactionDatetime() != null
                            && e.getTransactionDatetime().toLocalDate().isAfter(end)) return false;
                    return catLower == null || (e.getCategory() != null &&
                            e.getCategory().toLowerCase().contains(catLower));
                })
                .sorted(Comparator.comparing(Expense::getTransactionDatetime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit > 0 ? limit : 10)
                .toList();

        if (filtered.isEmpty()) {
            return "No expenses found" +
                    (catLower != null ? " in category '" + category + "'" : "") +
                    (start != null ? " from " + startDate : "") +
                    (end != null ? " to " + endDate : "") + ".";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(filtered.size()).append(" expense(s):\n");
        for (Expense e : filtered) {
            sb.append("- ").append(e.getTransactionDatetime() != null
                            ? e.getTransactionDatetime().format(DateTimeFormatter.ISO_LOCAL_DATE) : "no date")
                    .append(" | ").append(e.getAmount()).append(" ").append(e.getCurrency())
                    .append(" | ").append(e.getCategory() != null ? e.getCategory() : "no category")
                    .append(" | ").append(e.getNotes() != null ? e.getNotes() : "")
                    .append("\n");
        }
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

        Long userId = userContext.getUserId();
        List<Expense> expenses = expenseRepository.findByUserIdAndDeletedFalse(userId);

        LocalDate start = (startDate != null && !startDate.isBlank()) ? LocalDate.parse(startDate) : null;
        LocalDate end = (endDate != null && !endDate.isBlank()) ? LocalDate.parse(endDate) : null;

        List<Expense> filtered = expenses.stream()
                .filter(e -> {
                    if (start != null && e.getTransactionDatetime() != null
                            && e.getTransactionDatetime().toLocalDate().isBefore(start)) return false;
                    return end == null || e.getTransactionDatetime() == null
                            || !e.getTransactionDatetime().toLocalDate().isAfter(end);
                })
                .toList();

        if (filtered.isEmpty()) {
            return "No expenses found" +
                    (start != null ? " from " + startDate : "") +
                    (end != null ? " to " + endDate : "") + ".";
        }

        BigDecimal total = filtered.stream()
                .map(e -> e.getAmountInBase() != null ? e.getAmountInBase()
                        : (e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        Map<String, BigDecimal> byCategory = new TreeMap<>();
        Map<String, Long> countByCategory = new TreeMap<>();
        for (Expense e : filtered) {
            String cat = e.getCategory() != null && !e.getCategory().isBlank() ? e.getCategory() : "Uncategorized";
            BigDecimal amt = e.getAmountInBase() != null ? e.getAmountInBase()
                    : (e.getAmount() != null ? e.getAmount() : BigDecimal.ZERO);
            byCategory.merge(cat, amt, BigDecimal::add);
            countByCategory.merge(cat, 1L, Long::sum);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Expense Summary");
        if (start != null || end != null) {
            sb.append(" (").append(start != null ? startDate : "all time")
                    .append(" to ").append(end != null ? endDate : "now").append(")");
        }
        sb.append(":\n");
        sb.append("Total: ").append(total.toPlainString()).append(" (base currency)\n");
        sb.append("Number of expenses: ").append(filtered.size()).append("\n");
        sb.append("Category breakdown:\n");
        for (Map.Entry<String, BigDecimal> entry : byCategory.entrySet()) {
            sb.append("  - ").append(entry.getKey())
                    .append(": ").append(entry.getValue().setScale(2, RoundingMode.HALF_UP).toPlainString())
                    .append(" (").append(countByCategory.get(entry.getKey())).append(" expense(s))\n");
        }

        return sb.toString();
    }
}


