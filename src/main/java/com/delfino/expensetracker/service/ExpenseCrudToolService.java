package com.delfino.expensetracker.service;

import com.delfino.expensetracker.config.CountryConfig;
import com.delfino.expensetracker.config.UserContext;
import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.ExpenseItem;
import com.delfino.expensetracker.repository.ExpenseItemRepository;
import com.delfino.expensetracker.repository.ExpenseRepository;
import com.delfino.expensetracker.repository.StoreRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Provides @Tool-annotated methods for the LLM to perform CRUD operations
 * on the authenticated user's own Expenses, ExpenseItems, and Stores.
 *
 * <h3>Practical Usage Notes</h3>
 * <ul>
 *   <li><b>updateExpenseNotes / updateExpenseCategory</b> — Useful for quick edits via chat.</li>
 *   <li><b>deleteExpense</b> — Useful but potentially dangerous; soft-delete only.</li>
 *   <li><b>Store CRUD</b> — Rarely used in practice since stores are auto-populated from OCR.
 *       Users will almost never ask to create/update stores via chat.</li>
 *   <li><b>ExpenseItem CRUD</b> — Low practical usage via chat. Item-level edits are
 *       better done through the UI detail page.</li>
 * </ul>
 *
 * <h3>Security Considerations</h3>
 * <ul>
 *   <li>All methods are scoped to the authenticated user's own data only.</li>
 *   <li>Delete operations use soft-delete (recoverable).</li>
 *   <li>No methods expose other users' data or allow privilege escalation.</li>
 * </ul>
 */
@Service
public class ExpenseCrudToolService {

    private static final Logger log = LoggerFactory.getLogger(ExpenseCrudToolService.class);

    private final ExpenseRepository expenseRepository;
    private final ExpenseItemRepository expenseItemRepository;
    private final StoreRepository storeRepository;
    private final ExpenseService expenseService;
    private final UserContext userContext;

    public ExpenseCrudToolService(ExpenseRepository expenseRepository,
                                  ExpenseItemRepository expenseItemRepository,
                                  StoreRepository storeRepository,
                                  ExpenseService expenseService,
                                  UserContext userContext) {
        this.expenseRepository = expenseRepository;
        this.expenseItemRepository = expenseItemRepository;
        this.storeRepository = storeRepository;
        this.expenseService = expenseService;
        this.userContext = userContext;
    }

    // ─────────────────────────────────────────────────────────────
    // EXPENSE CRUD
    // ─────────────────────────────────────────────────────────────

    @Tool(description = "Update the notes or category of an existing expense. " +
            "Use this when the user wants to change the description or category of a specific expense.")
    public String updateExpense(
            @ToolParam(description = "The Long of the expense to update.") String expenseId,
            @ToolParam(description = "New category value. Pass empty string to skip.") String category,
            @ToolParam(description = "New notes value. Pass empty string to skip.") String notes) {

        log.info("Tool call: updateExpense(expenseId={}, userId={})", expenseId, userContext.getUserId());
        long userId = userContext.getUserId();

        try {
            Expense expense = expenseRepository.findById(Long.valueOf(expenseId)).orElse(null);
            if (expense == null) return "Expense not found.";
            if (expense.getUserId() != userId) return "Not authorized to modify this expense.";

            Expense updates = new Expense();
            if (category != null && !category.isBlank()) updates.setCategory(category);
            if (notes != null && !notes.isBlank()) updates.setNotes(notes);

            Expense updated = expenseService.updateExpense(expense.getUrlId(), updates, userId);
            return "Expense updated. Category: " + updated.getCategory() + ", Notes: " + updated.getNotes();
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

        StringBuilder sb = new StringBuilder();
        sb.append("Expense Details:\n");
        sb.append("- ID: ").append(expense.getId()).append("\n");
        sb.append("- Date: ").append(expense.getTransactionDatetime() != null
                ? expense.getTransactionDatetime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "unknown").append("\n");
        sb.append("- Amount: ").append(expense.getAmount()).append(" ").append(expense.getCurrency()).append("\n");
        if (expense.getAmountInBase() != null)
            sb.append("- Base Amount: ").append(expense.getAmountInBase()).append("\n");
        sb.append("- Category: ").append(expense.getCategory()).append("\n");
        sb.append("- Notes: ").append(expense.getNotes()).append("\n");
        sb.append("- Status: ").append(expense.getStatus()).append("\n");

        // Items
        List<ExpenseItem> items = expenseItemRepository.findByExpenseIdAndDeletedFalse(expense.getId());
        if (!items.isEmpty()) {
            sb.append("\nItems:\n");
            for (ExpenseItem item : items) {
                sb.append("  - ").append(item.getItemName())
                        .append(" (qty: ").append(item.getQuantity())
                        .append(", unit: ").append(item.getUnitPrice())
                        .append(", total: ").append(item.getTotalPrice()).append(")\n");
            }
        }

        // Store
        if (expense.getStoreId() != null) {
            storeRepository.findById(expense.getStoreId())
                    .filter(store -> store.getUserId() == userId)
                    .ifPresent(store -> {
            sb.append("\nStore: ").append(store.getName());
            if (store.getCity() != null) sb.append(", ").append(store.getCity());
            if (store.getCountry() != null) sb.append(", ").append(store.getCountry());
            sb.append("\n");
        });
        }

        return sb.toString();
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

    // ─────────────────────────────────────────────────────────────
    // STORE QUERY
    // ─────────────────────────────────────────────────────────────

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
        List<Expense> expenses = expenseRepository.findByUserIdAndDeletedFalse(userId);

        LocalDate start = (startDate != null && !startDate.isBlank()) ? LocalDate.parse(startDate) : null;
        LocalDate end = (endDate != null && !endDate.isBlank()) ? LocalDate.parse(endDate) : null;
        String countryLower = (country != null && !country.isBlank()) ? country.toLowerCase() : null;
        String resolvedCountryCode = countryLower != null ? CountryConfig.findCodeByName(country) : null;

        // Filter expenses by date range
        List<Expense> filtered = expenses.stream()
                .filter(e -> {
                    if (start != null && e.getTransactionDatetime() != null
                            && e.getTransactionDatetime().toLocalDate().isBefore(start)) return false;
                    return end == null || e.getTransactionDatetime() == null
                            || !e.getTransactionDatetime().toLocalDate().isAfter(end);
                })
                .toList();

        // Build store visit data
        Map<String, Integer> visitCounts = new LinkedHashMap<>();
        Map<String, String> storeCity = new LinkedHashMap<>();
        Map<String, String> storeCountry = new LinkedHashMap<>();
        Map<String, String> storeLastDate = new LinkedHashMap<>();

        for (Expense e : filtered) {
            if (e.getStoreId() == null) continue;
            storeRepository.findById(e.getStoreId())
                    .filter(store -> store.getUserId() == userId)
                    .ifPresent(store -> {
                if (store.getName() == null || store.getName().isBlank()) return;

                // Apply country filter
                if (countryLower != null && store.getCountry() != null) {
                    String sc = store.getCountry().toLowerCase();
                    boolean matchCountry = sc.contains(countryLower);
                    if (!matchCountry && resolvedCountryCode != null) {
                        matchCountry = sc.equalsIgnoreCase(resolvedCountryCode);
                    }
                    if (!matchCountry) {
                        String countryName = CountryConfig.getName(store.getCountry());
                        matchCountry = countryName != null && countryName.toLowerCase().contains(countryLower);
                    }
                    if (!matchCountry) return;
                } else if (countryLower != null) {
                    return; // country filter specified but store has no country
                }

                String key = store.getName();
                visitCounts.merge(key, 1, Integer::sum);
                if (store.getCity() != null) storeCity.putIfAbsent(key, store.getCity());
                if (store.getCountry() != null)
                    storeCountry.putIfAbsent(key, CountryConfig.getName(store.getCountry()));
                if (e.getTransactionDatetime() != null) {
                    String dateStr = e.getTransactionDatetime().format(DateTimeFormatter.ISO_LOCAL_DATE);
                    storeLastDate.merge(key, dateStr, (a, b) -> a.compareTo(b) > 0 ? a : b);
                }
            });
        }

        if (visitCounts.isEmpty()) {
            return "No stores found" +
                    (countryLower != null ? " in " + country : "") +
                    (start != null ? " from " + startDate : "") +
                    (end != null ? " to " + endDate : "") + ".";
        }

        // Sort by visit count descending
        List<Map.Entry<String, Integer>> sorted = visitCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append("Stores visited");
        if (countryLower != null) sb.append(" in ").append(country);
        if (start != null || end != null) {
            sb.append(" (").append(start != null ? startDate : "beginning")
                    .append(" to ").append(end != null ? endDate : "now").append(")");
        }
        sb.append(":\n");

        for (Map.Entry<String, Integer> entry : sorted) {
            String name = entry.getKey();
            sb.append("- ").append(name);
            sb.append(" (").append(entry.getValue()).append(" visit").append(entry.getValue() > 1 ? "s" : "").append(")");
            String loc = storeCity.get(name);
            String ctry = storeCountry.get(name);
            if (loc != null || ctry != null) {
                sb.append(" — ");
                if (loc != null) sb.append(loc);
                if (loc != null && ctry != null) sb.append(", ");
                if (ctry != null) sb.append(ctry);
            }
            String lastDate = storeLastDate.get(name);
            if (lastDate != null) sb.append(", last: ").append(lastDate);
            sb.append("\n");
        }

        sb.append("\nTotal: ").append(sorted.size()).append(" store(s), ")
                .append(visitCounts.values().stream().mapToInt(Integer::intValue).sum()).append(" visit(s).");

        return sb.toString();
    }
}


