package com.delfino.expensetracker.service.mcp;

import com.delfino.expensetracker.businesslogic.ExpenseDateRange;
import com.delfino.expensetracker.businesslogic.StoreCountryMatcher;
import com.delfino.expensetracker.dto.auth.UserContext;
import com.delfino.expensetracker.service.CountryService;
import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.ExpenseItem;
import com.delfino.expensetracker.model.Store;
import com.delfino.expensetracker.repository.ExpenseItemRepository;
import com.delfino.expensetracker.repository.ExpenseRepository;
import com.delfino.expensetracker.repository.StoreRepository;
import com.delfino.expensetracker.service.ExpenseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Provides @Tool-annotated methods for the LLM to perform CRUD operations
 * on the authenticated user's own Expenses, ExpenseItems, and Stores.
 */
@Service
public class ExpenseCrudToolService {

    private static final Logger log = LoggerFactory.getLogger(ExpenseCrudToolService.class);

    private final ExpenseRepository expenseRepository;
    private final ExpenseItemRepository expenseItemRepository;
    private final StoreRepository storeRepository;
    private final ExpenseService expenseService;
    private final UserContext userContext;
    private final CountryService countryService;

    public ExpenseCrudToolService(ExpenseRepository expenseRepository,
                                  ExpenseItemRepository expenseItemRepository,
                                  StoreRepository storeRepository,
                                  ExpenseService expenseService,
                                  UserContext userContext,
                                  CountryService countryService) {
        this.expenseRepository = expenseRepository;
        this.expenseItemRepository = expenseItemRepository;
        this.storeRepository = storeRepository;
        this.expenseService = expenseService;
        this.userContext = userContext;
        this.countryService = countryService;
    }

    // ─────────────────────────────────────────────────────────────
    // EXPENSE CRUD
    // ─────────────────────────────────────────────────────────────

    @Tool(description = "Update the notes, category, and/or tags of an existing expense. " +
            "Use this when the user wants to change the description, category, or tags of a specific expense.")
    public String updateExpense(
            @ToolParam(description = "The Long of the expense to update.") String expenseId,
            @ToolParam(description = "New category value. Pass empty string to skip.") String category,
            @ToolParam(description = "New notes value. Pass empty string to skip.") String notes,
            @ToolParam(description = "Comma-separated list of tags to set (e.g. 'food,travel'). Pass empty string to skip changing tags. Pass a single space ' ' to clear all tags.") String tags) {

        log.info("Tool call: updateExpense(expenseId={}, userId={})", expenseId, userContext.getUserId());
        long userId = userContext.getUserId();

        try {
            Expense expense = expenseRepository.findById(Long.valueOf(expenseId)).orElse(null);
            if (expense == null) return "Expense not found.";
            if (expense.getUserId() != userId) return "Not authorized to modify this expense.";

            Expense updates = new Expense();
            if (StringUtils.hasText(category)) updates.setCategory(category);
            if (StringUtils.hasText(notes)) updates.setNotes(notes);
            if (tags != null) {
                if (tags.isBlank()) {
                    // skip — empty string means don't change tags
                } else if (tags.trim().equals(" ") || tags.equals(" ")) {
                    updates.setTags(new ArrayList<>());
                } else {
                    updates.setTags(Arrays.asList(tags.split("\\s*,\\s*")));
                }
            }

            Expense updated = expenseService.updateExpense(expense.getUrlId(), updates, userId);
            String tagStr = updated.getTags() != null ? String.join(", ", updated.getTags()) : "";
            return "Expense updated. Category: " + updated.getCategory() + ", Notes: " + updated.getNotes() + ", Tags: [" + tagStr + "]";
        } catch (Exception e) {
            return "Failed to update expense: " + e.getMessage();
        }
    }

    @Tool(description = "Soft-delete an expense. The expense can be restored later. " +
            "Use this when the user wants to delete a specific expense.")
    public String deleteExpense(
            @ToolParam(description = "The Long of the expense to delete.") String expenseId) {

        log.info("Tool call: deleteExpense(expenseId={}, userId={})", expenseId, userContext.getUserId());
        Long userId = userContext.getUserId();

        try {
            Expense expense = expenseRepository.findById(Long.valueOf(expenseId)).orElse(null);
            expenseService.softDelete(expense.getUrlId(), userId);
            return "Expense deleted (soft-delete). It can be restored from the Expenses page.";
        } catch (Exception e) {
            return "Failed to delete expense: " + e.getMessage();
        }
    }

    @Tool(description = "Get full details of a specific expense including its items and store information. " +
            "Use this when the user asks about a specific expense by ID.")
    public String getExpenseDetail(
            @ToolParam(description = "The Long of the expense.") String expenseId) {

        log.info("Tool call: getExpenseDetail(expenseId={}, userId={})", expenseId, userContext.getUserId());
        long userId = userContext.getUserId();

        Expense expense = expenseRepository.findById(Long.valueOf(expenseId)).orElse(null);
        if (expense == null) return "Expense not found.";
        if (expense.getUserId() != userId) return "Not authorized to view this expense.";

        return buildFullExpenseDetail(expense, userId);
    }

    // ─────────────────────────────────────────────────────────────
    // EXPENSE ITEM CRUD
    // ─────────────────────────────────────────────────────────────

    @Tool(description = "Add an item to an existing expense. Use this when the user wants to add a line item.")
    public String addExpenseItem(
            @ToolParam(description = "The Long of the expense to add the item to.") String expenseId,
            @ToolParam(description = "Name of the item.") String itemName,
            @ToolParam(description = "Quantity of the item.") double quantity,
            @ToolParam(description = "Unit price of the item.") double unitPrice) {

        log.info("Tool call: addExpenseItem(expenseId={}, userId={})", expenseId, userContext.getUserId());
        Long userId = userContext.getUserId();

        Expense expense = expenseRepository.findById(Long.valueOf(expenseId)).orElse(null);
        if (expense == null) return "Expense not found.";
        if (expense.getUserId() != userId) return "Not authorized.";

        ExpenseItem item = new ExpenseItem();
        item.setItemName(itemName);
        item.setQuantity(BigDecimal.valueOf(quantity));
        item.setUnitPrice(BigDecimal.valueOf(unitPrice));
        ExpenseItem saved = expenseService.saveItem(expense.getUrlId(), item);

        return "Item added: " + saved.getItemName() + " (qty: " + saved.getQuantity()
                + ", unit: " + saved.getUnitPrice() + ", total: " + saved.getTotalPrice() + ")";
    }

    @Tool(description = "Delete an item from an expense. Use this when the user wants to remove a line item.")
    public String deleteExpenseItem(
            @ToolParam(description = "The Long of the expense.") String expenseId,
            @ToolParam(description = "The Long of the item to delete.") String itemId) {

        log.info("Tool call: deleteExpenseItem(expenseId={}, itemId={}, userId={})", expenseId, itemId, userContext.getUserId());
        Long userId = userContext.getUserId();
        Expense expense = expenseRepository.findById(Long.valueOf(expenseId))
                .orElseThrow(() -> new RuntimeException("Expense not found"));
        try {
            expenseService.softDeleteItem(expense.getUrlId(), Long.valueOf(itemId), userId);
            return "Item deleted.";
        } catch (Exception e) {
            return "Failed to delete item: " + e.getMessage();
        }
    }

    @Tool(description = "Update an existing item on an expense. Use this when the user wants to modify an item's name, quantity, or unit price. " +
            "For example: 'change cookie ice cream price to 3.50' or 'update item 5 quantity to 2'.")
    public String updateExpenseItem(
            @ToolParam(description = "The Long of the expense containing the item.") String expenseId,
            @ToolParam(description = "The Long of the item to update.") String itemId,
            @ToolParam(description = "New name for the item. Pass empty string to keep current name.") String itemName,
            @ToolParam(description = "New quantity. Pass 0 or negative to keep current quantity.") double quantity,
            @ToolParam(description = "New unit price. Pass negative to keep current unit price.") double unitPrice) {

        log.info("Tool call: updateExpenseItem(expenseId={}, itemId={}, userId={})", expenseId, itemId, userContext.getUserId());
        Long userId = userContext.getUserId();

        Expense expense = expenseRepository.findById(Long.valueOf(expenseId)).orElse(null);
        if (expense == null) return "Expense not found.";
        if (expense.getUserId() != userId) return "Not authorized.";

        ExpenseItem existing = expenseItemRepository.findById(Long.valueOf(itemId)).orElse(null);
        if (existing == null) return "Item not found.";
        if (existing.getExpenseId() != expense.getId()) return "Item does not belong to this expense.";

        if (StringUtils.hasText(itemName)) existing.setItemName(itemName);
        if (quantity > 0) existing.setQuantity(BigDecimal.valueOf(quantity));
        if (unitPrice >= 0) existing.setUnitPrice(BigDecimal.valueOf(unitPrice));

        ExpenseItem saved = expenseService.saveItem(expense.getUrlId(), existing);

        return "Item updated: " + saved.getItemName() + " (qty: " + saved.getQuantity()
                + ", unit: " + saved.getUnitPrice() + ", total: " + saved.getTotalPrice() + ")";
    }

    // ─────────────────────────────────────────────────────────────
    // STORE QUERY
    // ─────────────────────────────────────────────────────────────

    @Tool(description = "Find the most recently visited store branch matching a given store name. " +
            "Returns the store ID, name, address, city, and country of the most recent match. " +
            "Use this when the user mentions a store/place name while logging a new expense, " +
            "so the expense can be linked to the correct store branch.")
    public String findRecentStoreBranch(
            @ToolParam(description = "The store name to search for, e.g. 'Spar', 'Walmart', 'Starbucks'.") String storeName) {

        log.info("Tool call: findRecentStoreBranch(storeName={}, userId={})", storeName, userContext.getUserId());
        Long userId = userContext.getUserId();
        String nameLower = storeName.toLowerCase();

        List<Expense> expenses = expenseRepository.findByUserIdAndDeletedFalse(userId);
        // Sort by date descending to find most recent
        expenses.sort(Comparator.comparing(Expense::getTransactionDatetime,
                Comparator.nullsLast(Comparator.reverseOrder())));

        for (Expense e : expenses) {
            if (e.getStoreId() == null) continue;
            Store store = storeRepository.findById(e.getStoreId()).orElse(null);
            if (store != null && store.getName() != null && store.getName().toLowerCase().contains(nameLower)) {
                StringBuilder sb = new StringBuilder("Found store branch:\n");
                sb.append("- Store ID: ").append(store.getId()).append("\n");
                sb.append("- Name: ").append(store.getName()).append("\n");
                if (StringUtils.hasText(store.getAddress())) sb.append("- Address: ").append(store.getAddress()).append("\n");
                if (StringUtils.hasText(store.getCity())) sb.append("- City: ").append(store.getCity()).append("\n");
                if (StringUtils.hasText(store.getCountry())) sb.append("- Country: ").append(store.getCountry()).append("\n");
                sb.append("- Last visited: ").append(e.getTransactionDatetime() != null
                        ? e.getTransactionDatetime().format(DateTimeFormatter.ISO_LOCAL_DATE) : "unknown").append("\n");
                return sb.toString();
            }
        }
        return "No store found matching '" + storeName + "'.";
    }

    @Tool(description = "List stores the user has visited, optionally filtered by date range and/or country. " +
            "Returns store names with visit counts, sorted by most visited. " +
            "Use this when the user asks about most visited stores, stores in a specific country, or stores visited during a date range.")
    public String getStores(
            @ToolParam(description = "Start date in ISO format yyyy-MM-dd. Pass empty string for no start date filter.") String startDate,
            @ToolParam(description = "End date in ISO format yyyy-MM-dd. Pass empty string for no end date filter.") String endDate,
            @ToolParam(description = "Country name or code to filter by, e.g. 'Japan', 'JP'. Pass empty string for all countries.") String country) {

        log.info("Tool call: getStores(startDate={}, endDate={}, country={}, userId={})",
                startDate, endDate, country, userContext.getUserId());

        Long userId = userContext.getUserId();
        LocalDate start = ExpenseDateRange.parseOrNull(startDate);
        LocalDate end = ExpenseDateRange.parseOrNull(endDate);
        String countryLower = StringUtils.hasText(country) ? country.toLowerCase() : null;
        String resolvedCode = countryLower != null ? countryService.findCodeByName(country) : null;

        List<Expense> expenses = expenseRepository.findByUserIdAndDeletedFalse(userId);
        StoreVisitMaps visitMaps = new StoreVisitMaps();

        for (Expense e : expenses) {
            if (ExpenseDateRange.isWithin(e, start, end) && e.getStoreId() != null) {
                recordStoreVisit(e, userId, countryLower, resolvedCode, visitMaps);
            }
        }

        if (visitMaps.visitCounts().isEmpty()) {
            return "No stores found"
                    + (countryLower != null ? " in " + country : "")
                    + (start != null ? " from " + startDate : "")
                    + (end != null ? " to " + endDate : "") + ".";
        }
        return formatStoreList(visitMaps, country, countryLower, startDate, endDate, start, end);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private String buildFullExpenseDetail(Expense expense, long userId) {
        StringBuilder sb = new StringBuilder(buildExpenseDetailHeader(expense));
        appendItems(sb, expense.getId());
        appendStore(sb, expense.getStoreId(), userId);
        return sb.toString();
    }

    private static String buildExpenseDetailHeader(Expense expense) {
        StringBuilder sb = new StringBuilder("Expense Details:\n")
                .append("- ID: ").append(expense.getId()).append("\n")
                .append("- Date: ").append(expense.getTransactionDatetime() != null
                        ? expense.getTransactionDatetime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "unknown").append("\n")
                .append("- Amount: ").append(expense.getAmount()).append(" ").append(expense.getCurrency()).append("\n");
        if (expense.getAmountInBase() != null)
            sb.append("- Base Amount: ").append(expense.getAmountInBase()).append("\n");
        sb.append("- Category: ").append(expense.getCategory()).append("\n")
                .append("- Notes: ").append(expense.getNotes()).append("\n")
                .append("- Status: ").append(expense.getStatus()).append("\n");
        return sb.toString();
    }

    private void appendItems(StringBuilder sb, long expenseId) {
        List<ExpenseItem> items = expenseItemRepository.findByExpenseIdAndDeletedFalse(expenseId);
        if (items.isEmpty()) return;
        sb.append("\nItems:\n");
        items.forEach(item -> sb.append("  - [itemId: ").append(item.getId()).append("] ").append(item.getItemName())
                .append(" (qty: ").append(item.getQuantity())
                .append(", unit: ").append(item.getUnitPrice())
                .append(", total: ").append(item.getTotalPrice()).append(")\n"));
    }

    private void appendStore(StringBuilder sb, Long storeId, long userId) {
        if (storeId == null) return;
        storeRepository.findById(storeId)
                .filter(s -> s.getUserId() == userId)
                .ifPresent(s -> {
                    sb.append("\nStore: ").append(s.getName());
                    if (StringUtils.hasText(s.getCity())) sb.append(", ").append(s.getCity());
                    if (StringUtils.hasText(s.getCountry())) sb.append(", ").append(s.getCountry());
                    sb.append("\n");
                });
    }

    private void recordStoreVisit(Expense e, long userId, String countryLower, String resolvedCode,
                                  StoreVisitMaps visitMaps) {
        storeRepository.findById(e.getStoreId())
                .filter(s -> s.getUserId() == userId)
                .filter(s -> StringUtils.hasText(s.getName()))
                .filter(s -> matchesCountryFilter(s, countryLower, resolvedCode))
                .ifPresent(s -> accumulateVisit(s, e, visitMaps));
    }

    private boolean matchesCountryFilter(Store store, String countryLower, String resolvedCode) {
        return countryLower == null
                || StoreCountryMatcher.matches(store, countryLower, resolvedCode, countryService::getName);
    }

    private void accumulateVisit(Store store, Expense e, StoreVisitMaps visitMaps) {
        String name = store.getName();
        visitMaps.visitCounts().merge(name, 1, Integer::sum);
        if (StringUtils.hasText(store.getCity())) visitMaps.storeCities().putIfAbsent(name, store.getCity());
        if (StringUtils.hasText(store.getCountry()))
            visitMaps.storeCountries().putIfAbsent(name, countryService.getName(store.getCountry()));
        if (e.getTransactionDatetime() != null) {
            visitMaps.storeLastDates().merge(name,
                    e.getTransactionDatetime().format(DateTimeFormatter.ISO_LOCAL_DATE),
                    (a, b) -> a.compareTo(b) > 0 ? a : b);
        }
    }

    private static String formatStoreList(StoreVisitMaps visitMaps, String country, String countryLower,
                                          String startDate, String endDate,
                                          LocalDate start, LocalDate end) {
        List<Map.Entry<String, Integer>> sorted = visitMaps.visitCounts().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .toList();

        StringBuilder sb = new StringBuilder("Stores visited");
        if (StringUtils.hasText(countryLower)) sb.append(" in ").append(country);
        if (start != null || end != null) {
            sb.append(" (").append(start != null ? startDate : "beginning")
                    .append(" to ").append(end != null ? endDate : "now").append(")");
        }
        sb.append(":\n");
        sorted.forEach(entry -> appendStoreLine(sb, entry.getKey(), entry.getValue(), visitMaps));
        sb.append("\nTotal: ").append(sorted.size()).append(" store(s), ")
                .append(visitMaps.visitCounts().values().stream().mapToInt(Integer::intValue).sum())
                .append(" visit(s).");
        return sb.toString();
    }

    private static void appendStoreLine(StringBuilder sb, String name, int visits, StoreVisitMaps visitMaps) {
        sb.append("- ").append(name)
                .append(" (").append(visits).append(" visit").append(visits > 1 ? "s" : "").append(")");
        String loc = visitMaps.storeCities().get(name);
        String ctry = visitMaps.storeCountries().get(name);
        if (loc != null || ctry != null) {
            sb.append(" — ");
            if (loc != null) sb.append(loc);
            if (loc != null && ctry != null) sb.append(", ");
            if (ctry != null) sb.append(ctry);
        }
        String lastDate = visitMaps.storeLastDates().get(name);
        if (lastDate != null) sb.append(", last: ").append(lastDate);
        sb.append("\n");
    }

    /** Holds the four aggregation maps for the {@code getStores} tool. */
    private record StoreVisitMaps(
            Map<String, Integer> visitCounts,
            Map<String, String> storeCities,
            Map<String, String> storeCountries,
            Map<String, String> storeLastDates) {

        StoreVisitMaps() {
            this(new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>());
        }
    }
}


