package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.service.CountryService;
import com.delfino.expensetracker.dto.auth.UserToken;
import com.delfino.expensetracker.dto.common.ErrorResponse;
import com.delfino.expensetracker.dto.common.MessageResponse;
import com.delfino.expensetracker.dto.expense.AttachmentPathResponse;
import com.delfino.expensetracker.dto.expense.EnrichedExpense;
import com.delfino.expensetracker.dto.expense.ExpenseDetailResponse;
import com.delfino.expensetracker.dto.expense.MatchingItem;
import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.ExpenseItem;
import com.delfino.expensetracker.model.Store;
import com.delfino.expensetracker.repository.ExpenseItemRepository;
import com.delfino.expensetracker.repository.ExpenseRepository;
import com.delfino.expensetracker.repository.StoreRepository;
import com.delfino.expensetracker.service.ExpenseService;
import com.delfino.expensetracker.service.SupportedCurrencyService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;
    private final ExpenseRepository expenseRepository;
    private final ExpenseItemRepository expenseItemRepository;
    private final StoreRepository storeRepository;
    private final SupportedCurrencyService supportedCurrencyService;
    private final CountryService countryService;

    @Value("${app.data.dir:data}")
    private String dataDir;

    public ExpenseController(ExpenseService expenseService, ExpenseRepository expenseRepository,
                             ExpenseItemRepository expenseItemRepository, StoreRepository storeRepository,
                             SupportedCurrencyService supportedCurrencyService, CountryService countryService) {
        this.expenseService = expenseService;
        this.expenseRepository = expenseRepository;
        this.expenseItemRepository = expenseItemRepository;
        this.storeRepository = storeRepository;
        this.supportedCurrencyService = supportedCurrencyService;
        this.countryService = countryService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> list(@RequestParam(required = false) String search,
                                  @RequestParam(required = false, defaultValue = "false") boolean includeDeleted,
                                  @RequestParam(required = false) String startDate,
                                  @RequestParam(required = false) String endDate,
                                  @RequestParam(required = false) String category,
                                  @RequestParam(required = false) String country,
                                  UserToken userToken) {
        long userId = userToken.getUserId();
        if (search != null) search = search.trim();
        List<Expense> expenses = expenseService.search(userId, search, includeDeleted);

        expenses = filterByDateRange(expenses, startDate, endDate);

        // Apply category filter
        if (category != null && !category.isBlank()) {
            expenses = expenses.stream()
                    .filter(e -> category.equalsIgnoreCase(e.getCategory()))
                    .toList();
        }
        // Apply country filter
        if (country != null && !country.isBlank()) {
            expenses = filterByCountry(expenses, country);
        }

        // Sort by date descending
        expenses = new ArrayList<>(expenses);
        expenses.sort((a, b) -> {
            if (a.getTransactionDatetime() == null && b.getTransactionDatetime() == null) return 0;
            if (a.getTransactionDatetime() == null) return 1;
            if (b.getTransactionDatetime() == null) return -1;
            return b.getTransactionDatetime().compareTo(a.getTransactionDatetime());
        });

        List<EnrichedExpense> enriched = enrichExpenses(expenses, search);
        return ResponseEntity.ok(enriched);
    }

    @GetMapping("/categories")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> categories(UserToken userToken) {
        long userId = userToken.getUserId();
        List<String> cats = expenseRepository.findByUserIdAndDeletedFalse(userId).stream()
                .map(Expense::getCategory)
                .filter(Objects::nonNull)
                .filter(c -> !c.isBlank())
                .distinct()
                .sorted()
                .toList();
        return ResponseEntity.ok(cats);
    }

    @GetMapping("/{expenseUrlId}")
    public ResponseEntity<?> get(@PathVariable String expenseUrlId, UserToken userToken) {
        Long userId = userToken != null ? userToken.getUserId() : null;
        return expenseRepository.findByUrlId(expenseUrlId)
                .map(expense -> {
                    boolean isOwner = userId != null && expense.getUserId() == userId;
                    return ResponseEntity.ok((Object) new ExpenseDetailResponse(
                            expense,
                            expenseItemRepository.findByExpenseId(expense.getId()),
                            expense.getStoreId() != null ? storeRepository.findById(expense.getStoreId()).orElse(null) : null,
                            isOwner
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/manual")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> createManual(@RequestBody Expense expense, UserToken userToken) {
        long userId = userToken.getUserId();
        if (!supportedCurrencyService.isSupported(expense.getCurrency())) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Unsupported currency: " + expense.getCurrency()));
        }
        expense.setCurrency(expense.getCurrency().toUpperCase());
        Expense saved = expenseService.createManualExpense(expense, userId);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/scan")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> uploadReceipt(@RequestParam("file") MultipartFile file, UserToken userToken) throws IOException {
        long userId = userToken.getUserId();
        Path uploadDir = Path.of(dataDir, "receipts");
        Files.createDirectories(uploadDir);
        String filename = getDateString() + "_" + userId + "_" + file.getOriginalFilename();
        Path filePath = uploadDir.resolve(filename);
        Files.write(filePath, file.getBytes());
        Expense expense = expenseService.createReceiptScanExpense(userId, filePath.toString());
        return ResponseEntity.ok(expense);
    }

    private String getDateString() {
        return ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS"));
    }

    @PutMapping("/{expenseUrlId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> update(@PathVariable String expenseUrlId, @RequestBody Expense updates, UserToken userToken) {
        long userId = userToken.getUserId();
        if (StringUtils.hasText(updates.getCurrency())) {
            if (!supportedCurrencyService.isSupported(updates.getCurrency())) {
                return ResponseEntity.badRequest().body(new ErrorResponse("Unsupported currency: " + updates.getCurrency()));
            }
            updates.setCurrency(updates.getCurrency().toUpperCase());
        }
        Expense updated = expenseService.updateExpense(expenseUrlId, updates, userId);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{expenseUrlId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MessageResponse> delete(@PathVariable String expenseUrlId, UserToken userToken) {
        expenseService.softDelete(expenseUrlId, userToken.getUserId());
        return ResponseEntity.ok(new MessageResponse("Deleted"));
    }

    @PatchMapping("/{expenseUrlId}/restore")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MessageResponse> restore(@PathVariable String expenseUrlId, UserToken userToken) {
        expenseService.restore(expenseUrlId, userToken.getUserId());
        return ResponseEntity.ok(new MessageResponse("Restored"));
    }

    @PostMapping("/{expenseUrlId}/retry")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MessageResponse> retry(@PathVariable String expenseUrlId, UserToken userToken) {
        expenseService.retryOcr(expenseUrlId, userToken.getUserId());
        return ResponseEntity.ok(new MessageResponse("Retry initiated"));
    }

    @PostMapping("/{expenseUrlId}/duplicate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> duplicate(@PathVariable String expenseUrlId, UserToken userToken) {
        Expense copy = expenseService.duplicate(expenseUrlId, userToken.getUserId());
        return ResponseEntity.ok(copy);
    }

    // --- Items ---
    @PostMapping("/{expenseUrlId}/items")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> addItem(@PathVariable String expenseUrlId, @RequestBody ExpenseItem item) {
        ExpenseItem saved = expenseService.saveItem(expenseUrlId, item);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{expenseUrlId}/items/{itemId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> updateItem(@PathVariable String expenseUrlId, @PathVariable Long itemId,
                                        @RequestBody ExpenseItem item) {
        item.setId(itemId);
        ExpenseItem saved = expenseService.saveItem(expenseUrlId, item);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{expenseUrlId}/items/{itemId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MessageResponse> deleteItem(@PathVariable String expenseUrlId, @PathVariable Long itemId,
                                                      UserToken userToken) {
        expenseService.softDeleteItem(expenseUrlId, itemId, userToken.getUserId());
        return ResponseEntity.ok(new MessageResponse("Item deleted"));
    }

    // --- Store ---
    @PutMapping("/{expenseUrlId}/store")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> updateStore(@PathVariable String expenseUrlId, @RequestBody Store store,
                                         UserToken userToken) {
        Store saved = expenseService.saveStore(expenseUrlId, store, userToken.getUserId());
        return ResponseEntity.ok(saved);
    }

    // --- Attachments ---
    @PostMapping("/{expenseUrlId}/attachments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AttachmentPathResponse> addAttachment(@PathVariable String expenseUrlId,
                                           @RequestParam("file") MultipartFile file) throws IOException {
        String path = expenseService.addAttachment(expenseUrlId, file.getOriginalFilename(), file.getBytes());
        return ResponseEntity.ok(new AttachmentPathResponse(path));
    }

    @DeleteMapping("/{expenseUrlId}/attachments/{filename}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<MessageResponse> removeAttachment(@PathVariable String expenseUrlId,
                                              @PathVariable String filename) throws IOException {
        expenseService.removeAttachment(expenseUrlId, filename);
        return ResponseEntity.ok(new MessageResponse("Attachment removed"));
    }

    // --- Export ---
    @GetMapping("/export")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> export(@RequestParam(defaultValue = "json") String format,
                                    @RequestParam(required = false) String search,
                                    UserToken userToken) {
        long userId = userToken.getUserId();
        if (search != null) search = search.trim();
        List<Expense> expenses = expenseService.search(userId, search, false);

        if ("csv".equalsIgnoreCase(format)) {
            StringBuilder csv = new StringBuilder();
            csv.append("ID,Date,Amount,Currency,AmountInBase,Category,ReceiptNumber,Type,Status,Notes,Tags\n");
            for (Expense e : expenses) {
                csv.append(String.join(",",
                        e.getUrlId(),
                        e.getTransactionDatetime() != null ? e.getTransactionDatetime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "",
                        e.getAmount() != null ? e.getAmount().toPlainString() : "",
                        e.getCurrency() != null ? e.getCurrency() : "",
                        e.getAmountInBase() != null ? e.getAmountInBase().toPlainString() : "",
                        escapeCsv(e.getCategory()),
                        escapeCsv(e.getReceiptNumber()),
                        e.getType() != null ? e.getType().name() : "",
                        e.getStatus() != null ? e.getStatus().name() : "",
                        escapeCsv(e.getNotes()),
                        e.getTags() != null ? String.join(";", e.getTags()) : ""
                )).append("\n");
            }
            return ResponseEntity.ok()
                    .header("Content-Type", "text/csv")
                    .header("Content-Disposition", "attachment; filename=expenses.csv")
                    .body(csv.toString());
        }
        return ResponseEntity.ok(expenses);
    }

    // --- Private helpers ---

    /** Filter expenses by date range (shared pattern used across controllers). */
    static List<Expense> filterByDateRange(List<Expense> expenses, String startDate, String endDate) {
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
        return expenses;
    }

    /** Filter expenses by country (supports both code and name). */
    private List<Expense> filterByCountry(List<Expense> expenses, String country) {
        final String countryFilter = country.toLowerCase();
        final String resolvedCode = countryService.findCodeByName(country);
        return expenses.stream()
                .filter(e -> {
                    if (e.getStoreId() == null) return false;
                    return storeRepository.findById(e.getStoreId())
                        .map(s -> matchesCountry(s, countryFilter, resolvedCode))
                        .orElse(false);
                })
                .toList();
    }

    /** Check if a store matches a country filter (shared logic). */
    private boolean matchesCountry(Store s, String countryFilterLower, String resolvedCode) {
        if (s.getCountry() == null) return false;
        String sc = s.getCountry().toLowerCase();
        if (sc.contains(countryFilterLower)) return true;
        if (resolvedCode != null && sc.equalsIgnoreCase(resolvedCode)) return true;
        String name = countryService.getName(s.getCountry());
        return name != null && name.toLowerCase().contains(countryFilterLower);
    }

    /** Enrich expenses with store info and matching items for the list view. */
    private List<EnrichedExpense> enrichExpenses(List<Expense> expenses, String search) {
        String searchLower = (search != null && !search.isBlank()) ? search.toLowerCase() : null;
        List<EnrichedExpense> enriched = new ArrayList<>();
        for (Expense e : expenses) {
            Store store = e.getStoreId() != null ? storeRepository.findById(e.getStoreId()).orElse(null) : null;
            String storeName = store != null ? store.getName() : null;
            String cat = e.getCategory() != null ? e.getCategory() : "Uncategorized";

            EnrichedExpense.Builder builder = EnrichedExpense.builder()
                    .id(e.getId())
                    .userId(e.getUserId())
                    .type(e.getType())
                    .transactionDatetime(e.getTransactionDatetime())
                    .amount(e.getAmount())
                    .currency(e.getCurrency())
                    .amountInBase(e.getAmountInBase())
                    .exchangeRate(e.getExchangeRate())
                    .receiptNumber(e.getReceiptNumber())
                    .category(e.getCategory())
                    .tags(e.getTags())
                    .notes(e.getNotes())
                    .status(e.getStatus())
                    .imagePath(e.getImagePath())
                    .attachments(e.getAttachments())
                    .deleted(e.isDeleted())
                    .createdAt(e.getCreatedAt())
                    .updatedAt(e.getUpdatedAt())
                    .scannedAt(e.getScannedAt())
                    .urlId(e.getUrlId())
                    .storeName(storeName)
                    .country(store != null ? store.getCountry() : null)
                    .countryName(store != null && store.getCountry() != null
                            ? countryService.getName(store.getCountry()) : null)
                    .displayName(storeName != null && !storeName.isBlank()
                            ? cat + " \u2014 " + storeName : cat);

            if (searchLower != null) {
                List<MatchingItem> matchingItems = getMatchingItems(e, searchLower);
                if (!matchingItems.isEmpty()) {
                    builder.matchingItems(matchingItems);
                }
            }

            enriched.add(builder.build());
        }
        return enriched;
    }

    private List<MatchingItem> getMatchingItems(Expense e, String searchLower) {
        List<ExpenseItem> items = expenseItemRepository.findByExpenseIdAndDeletedFalse(e.getId());
        return items.stream()
                .filter(i -> i.getItemName() != null && i.getItemName().toLowerCase().contains(searchLower))
                .map(i -> new MatchingItem(i.getItemName(), i.getUnitPrice(), i.getQuantity(), i.getTotalPrice()))
                .toList();
    }

    private String escapeCsv(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }
}

