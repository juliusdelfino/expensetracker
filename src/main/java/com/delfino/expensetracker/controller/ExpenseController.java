package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.config.CountryConfig;
import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.ExpenseItem;
import com.delfino.expensetracker.model.Store;
import com.delfino.expensetracker.repository.ExpenseItemRepository;
import com.delfino.expensetracker.repository.ExpenseRepository;
import com.delfino.expensetracker.repository.StoreRepository;
import com.delfino.expensetracker.service.ExpenseService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
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

    @Value("${app.data.dir:data}")
    private String dataDir;

    public ExpenseController(ExpenseService expenseService, ExpenseRepository expenseRepository,
                             ExpenseItemRepository expenseItemRepository, StoreRepository storeRepository) {
        this.expenseService = expenseService;
        this.expenseRepository = expenseRepository;
        this.expenseItemRepository = expenseItemRepository;
        this.storeRepository = storeRepository;
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestParam(required = false) String search,
                                  @RequestParam(required = false, defaultValue = "false") boolean includeDeleted,
                                  @RequestParam(required = false) String startDate,
                                  @RequestParam(required = false) String endDate,
                                  @RequestParam(required = false) String category,
                                  @RequestParam(required = false) String country,
                                  HttpSession session) {
        Long userId = getUserId(session);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        List<Expense> expenses = expenseService.search(userId, search, includeDeleted);

        // Apply date range filter
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
        // Apply category filter
        if (category != null && !category.isBlank()) {
            expenses = expenses.stream()
                    .filter(e -> category.equalsIgnoreCase(e.getCategory()))
                    .toList();
        }
        // Apply country filter (supports both code and name)
        if (country != null && !country.isBlank()) {
            final String countryFilter = country.toLowerCase();
            // Try to resolve name to code
            final String resolvedCode = CountryConfig.findCodeByName(country);
            expenses = expenses.stream()
                    .filter(e -> {
                        if (e.getStoreId() == null) return false;
                        return storeRepository.findById(e.getStoreId())
                            .map(s -> {
                                if (s.getCountry() == null) return false;
                                String sc = s.getCountry().toLowerCase();
                                if (sc.contains(countryFilter)) return true;
                                if (sc.equalsIgnoreCase(resolvedCode)) return true;
                                String name = CountryConfig.getName(s.getCountry());
                                return name != null && name.toLowerCase().contains(countryFilter);
                            })
                            .orElse(false);
                    })
                    .toList();
        }

        // Sort by date descending
        expenses = new ArrayList<>(expenses);
        expenses.sort((a, b) -> {
            if (a.getTransactionDatetime() == null && b.getTransactionDatetime() == null) return 0;
            if (a.getTransactionDatetime() == null) return 1;
            if (b.getTransactionDatetime() == null) return -1;
            return b.getTransactionDatetime().compareTo(a.getTransactionDatetime());
        });
        // Enrich with store name for display
        String searchLower = (search != null && !search.isBlank()) ? search.toLowerCase() : null;
        List<Map<String, Object>> enriched = new ArrayList<>();
        for (Expense e : expenses) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", e.getId());
            map.put("userId", e.getUserId());
            map.put("type", e.getType());
            map.put("transactionDatetime", e.getTransactionDatetime());
            map.put("amount", e.getAmount());
            map.put("currency", e.getCurrency());
            map.put("amountInBase", e.getAmountInBase());
            map.put("exchangeRate", e.getExchangeRate());
            map.put("receiptNumber", e.getReceiptNumber());
            map.put("category", e.getCategory());
            map.put("tags", e.getTags());
            map.put("notes", e.getNotes());
            map.put("status", e.getStatus());
            map.put("imagePath", e.getImagePath());
            map.put("attachments", e.getAttachments());
            map.put("deleted", e.isDeleted());
            map.put("createdAt", e.getCreatedAt());
            map.put("updatedAt", e.getUpdatedAt());
            map.put("scannedAt", e.getScannedAt());
            map.put("urlId", e.getUrlId());
            Store store = e.getStoreId() != null ? storeRepository.findById(e.getStoreId()).orElse(null) : null;
            String storeName = store != null ? store.getName() : null;
            map.put("storeName", storeName);
            map.put("country", store != null ? store.getCountry() : null);
            map.put("countryName", store != null && store.getCountry() != null
                    ? CountryConfig.getName(store.getCountry()) : null);
            String cat = e.getCategory() != null ? e.getCategory() : "Uncategorized";
            map.put("displayName", storeName != null && !storeName.isBlank()
                    ? cat + " — " + storeName : cat);

            // Include matching items when search is active
            if (searchLower != null) {
                List<ExpenseItem> items = expenseItemRepository.findByExpenseIdAndDeletedFalse(e.getId());
                List<Map<String, Object>> matchingItems = items.stream()
                        .filter(i -> i.getItemName() != null && i.getItemName().toLowerCase().contains(searchLower))
                        .map(i -> {
                            Map<String, Object> im = new LinkedHashMap<>();
                            im.put("itemName", i.getItemName());
                            im.put("unitPrice", i.getUnitPrice());
                            im.put("quantity", i.getQuantity());
                            im.put("totalPrice", i.getTotalPrice());
                            return im;
                        })
                        .toList();
                if (!matchingItems.isEmpty()) {
                    map.put("matchingItems", matchingItems);
                }
            }

            enriched.add(map);
        }
        return ResponseEntity.ok(enriched);
    }

    @GetMapping("/categories")
    public ResponseEntity<?> categories(HttpSession session) {
        Long userId = getUserId(session);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
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
    public ResponseEntity<?> get(@PathVariable String expenseUrlId, HttpSession session) {
        long userId = getUserId(session);
        if (userId == 0) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        return expenseRepository.findByUrlId(expenseUrlId)
                .filter(e -> e.getUserId() == userId)
                .map(expense -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("expense", expense);
                    result.put("items", expenseItemRepository.findByExpenseId(expense.getId()));
                    result.put("store", expense.getStoreId() != null ? storeRepository.findById(expense.getStoreId()).orElse(null) : null);
                    return ResponseEntity.ok(result);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/manual")
    public ResponseEntity<?> createManual(@RequestBody Expense expense, HttpSession session) {
        Long userId = getUserId(session);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        Expense saved = expenseService.createManualExpense(expense, userId);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/scan")
    public ResponseEntity<?> uploadReceipt(@RequestParam("file") MultipartFile file, HttpSession session) throws IOException {
        Long userId = getUserId(session);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));

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
    public ResponseEntity<?> update(@PathVariable String expenseUrlId, @RequestBody Expense updates, HttpSession session) {
        Long userId = getUserId(session);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        Expense updated = expenseService.updateExpense(expenseUrlId, updates, userId);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{expenseUrlId}")
    public ResponseEntity<?> delete(@PathVariable String expenseUrlId, HttpSession session) {
        Long userId = getUserId(session);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        expenseService.softDelete(expenseUrlId, userId);
        return ResponseEntity.ok(Map.of("message", "Deleted"));
    }

    @PatchMapping("/{expenseUrlId}/restore")
    public ResponseEntity<?> restore(@PathVariable String expenseUrlId, HttpSession session) {
        Long userId = getUserId(session);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        expenseService.restore(expenseUrlId, userId);
        return ResponseEntity.ok(Map.of("message", "Restored"));
    }

    @PostMapping("/{expenseUrlId}/retry")
    public ResponseEntity<?> retry(@PathVariable String expenseUrlId, HttpSession session) {
        Long userId = getUserId(session);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        expenseService.retryOcr(expenseUrlId, userId);
        return ResponseEntity.ok(Map.of("message", "Retry initiated"));
    }

    @PostMapping("/{expenseUrlId}/duplicate")
    public ResponseEntity<?> duplicate(@PathVariable String expenseUrlId, HttpSession session) {
        Long userId = getUserId(session);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        Expense copy = expenseService.duplicate(expenseUrlId, userId);
        return ResponseEntity.ok(copy);
    }

    // --- Items ---
    @PostMapping("/{expenseUrlId}/items")
    public ResponseEntity<?> addItem(@PathVariable String expenseUrlId, @RequestBody ExpenseItem item, HttpSession session) {
        Long userId = getUserId(session);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        ExpenseItem saved = expenseService.saveItem(expenseUrlId, item);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{expenseUrlId}/items/{itemId}")
    public ResponseEntity<?> updateItem(@PathVariable String expenseUrlId, @PathVariable Long itemId,
                                        @RequestBody ExpenseItem item, HttpSession session) {
        Long userId = getUserId(session);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        item.setId(itemId);
        ExpenseItem saved = expenseService.saveItem(expenseUrlId, item);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{expenseUrlId}/items/{itemId}")
    public ResponseEntity<?> deleteItem(@PathVariable String expenseUrlId, @PathVariable Long itemId, HttpSession session) {
        Long userId = getUserId(session);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        expenseService.softDeleteItem(expenseUrlId, itemId, userId);
        return ResponseEntity.ok(Map.of("message", "Item deleted"));
    }

    // --- Store ---
    @PutMapping("/{expenseUrlId}/store")
    public ResponseEntity<?> updateStore(@PathVariable String expenseUrlId, @RequestBody Store store, HttpSession session) {
        Long userId = getUserId(session);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        Store saved = expenseService.saveStore(expenseUrlId, store, userId);
        return ResponseEntity.ok(saved);
    }

    // --- Attachments ---
    @PostMapping("/{expenseUrlId}/attachments")
    public ResponseEntity<?> addAttachment(@PathVariable String expenseUrlId, @RequestParam("file") MultipartFile file,
                                           HttpSession session) throws IOException {
        Long userId = getUserId(session);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        String path = expenseService.addAttachment(expenseUrlId, file.getOriginalFilename(), file.getBytes());
        return ResponseEntity.ok(Map.of("path", path));
    }

    @DeleteMapping("/{expenseUrlId}/attachments/{filename}")
    public ResponseEntity<?> removeAttachment(@PathVariable String expenseUrlId, @PathVariable String filename,
                                              HttpSession session) throws IOException {
        Long userId = getUserId(session);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        expenseService.removeAttachment(expenseUrlId, filename);
        return ResponseEntity.ok(Map.of("message", "Attachment removed"));
    }

    // --- Export ---
    @GetMapping("/export")
    public ResponseEntity<?> export(@RequestParam(defaultValue = "json") String format,
                                    @RequestParam(required = false) String search,
                                    HttpSession session) {
        Long userId = getUserId(session);
        if (userId == null) return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
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

    private String escapeCsv(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }

    private Long getUserId(HttpSession session) {
        return (Long) session.getAttribute("userId");
    }
}

