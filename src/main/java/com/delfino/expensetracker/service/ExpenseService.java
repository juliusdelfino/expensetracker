package com.delfino.expensetracker.service;

import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.ExpenseItem;
import com.delfino.expensetracker.model.ExpenseStatus;
import com.delfino.expensetracker.model.ExpenseType;
import com.delfino.expensetracker.model.Store;
import com.delfino.expensetracker.model.User;
import com.delfino.expensetracker.repository.ExpenseItemRepository;
import com.delfino.expensetracker.repository.ExpenseRepository;
import com.delfino.expensetracker.repository.StoreRepository;
import com.delfino.expensetracker.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ExpenseService {

    private static final Logger log = LoggerFactory.getLogger(ExpenseService.class);

    private final ExpenseRepository expenseRepository;
    private final ExpenseItemRepository expenseItemRepository;
    private final StoreRepository storeRepository;
    private final CurrencyService currencyService;
    private final OcrService ocrService;
    private final UserRepository userRepository;
    private final SupportedCurrencyService supportedCurrencyService;
    private final CountryService countryService;

    @Value("${app.data.dir:data}")
    private String dataDir;

    public ExpenseService(ExpenseRepository expenseRepository, ExpenseItemRepository expenseItemRepository,
                          StoreRepository storeRepository, CurrencyService currencyService,
                          OcrService ocrService, UserRepository userRepository, SupportedCurrencyService supportedCurrencyService,
                          CountryService countryService) {
        this.expenseRepository = expenseRepository;
        this.expenseItemRepository = expenseItemRepository;
        this.storeRepository = storeRepository;
        this.currencyService = currencyService;
        this.ocrService = ocrService;
        this.userRepository = userRepository;
        this.supportedCurrencyService = supportedCurrencyService;
        this.countryService = countryService;
    }

    public Expense createManualExpense(Expense expense, Long userId) {
        // Validate currency if provided

        if (!supportedCurrencyService.isSupported(expense.getCurrency())) {
            throw new IllegalArgumentException("Unsupported currency: " + expense.getCurrency());
        }
        expense.setCurrency(expense.getCurrency());
        User user = userRepository.findById(userId).orElse(null);
        if (user != null && user.getBaseCurrency() == null) {
            user.setBaseCurrency(expense.getCurrency());
            userRepository.save(user);
        }
        expense.setUserId(userId);
        expense.setType(ExpenseType.MANUAL);
        expense.setStatus(ExpenseStatus.COMPLETED);
        expense.setCreatedAt(LocalDateTime.now());
        expense.setUpdatedAt(LocalDateTime.now());
        expense.setUrlId(UUID.randomUUID().toString());
        if (expense.getAttachments() == null) expense.setAttachments(new ArrayList<>());
        if (expense.getTags() == null) expense.setTags(new ArrayList<>());
        computeCurrency(expense, userId);
        return expenseRepository.save(expense);
    }

    public Expense createReceiptScanExpense(Long userId, String imagePath) {
        Expense expense = new Expense();
        expense.setUserId(userId);
        expense.setType(ExpenseType.RECEIPT_SCAN);
        expense.setStatus(ExpenseStatus.PROCESSING);
        expense.setImagePath(imagePath);
        expense.setDeleted(false);
        expense.setAttachments(new ArrayList<>());
        expense.setTags(new ArrayList<>());
        expense.setScannedAt(LocalDateTime.now());
        expense.setCreatedAt(LocalDateTime.now());
        expense.setUpdatedAt(LocalDateTime.now());
        expense.setUrlId(UUID.randomUUID().toString());
        expenseRepository.save(expense);

        ocrService.processReceipt(expense.getId(), imagePath);
        return expense;
    }

    /**
     * Creates a receipt scan expense without immediately triggering OCR (for batch processing).
     */
    public Expense createReceiptScanExpenseQueued(Long userId, String imagePath) {
        Expense expense = new Expense();
        expense.setUserId(userId);
        expense.setType(ExpenseType.RECEIPT_SCAN);
        expense.setStatus(ExpenseStatus.PROCESSING);
        expense.setImagePath(imagePath);
        expense.setDeleted(false);
        expense.setAttachments(new ArrayList<>());
        expense.setTags(new ArrayList<>());
        expense.setScannedAt(LocalDateTime.now());
        expense.setCreatedAt(LocalDateTime.now());
        expense.setUpdatedAt(LocalDateTime.now());
        expense.setUrlId(UUID.randomUUID().toString());
        expenseRepository.save(expense);
        return expense;
    }

    /**
     * Processes a queue of expenses through OCR with configurable interval delays between each call.
     */
    @Async
    public void processOcrQueue(List<Expense> expenses) {
        for (int i = 0; i < expenses.size(); i++) {
            Expense expense = expenses.get(i);
            log.info("Processing OCR queue item {}/{}: expense {}", i + 1, expenses.size(), expense.getId());
            ocrService.processReceiptSync(expense.getId(), expense.getImagePath());
        }
    }

    @Transactional
    public Expense updateExpense(String urlId, Expense updates, long userId) {
        Expense expense = expenseRepository.findByUrlId(urlId)
                .orElseThrow(() -> new IllegalStateException("Expense not found"));
        if (expense.getUserId() != userId) throw new AuthorizationDeniedException("Not authorized");

        if (updates.getTransactionDatetime() != null) expense.setTransactionDatetime(updates.getTransactionDatetime());
        if (updates.getAmount() != null) expense.setAmount(updates.getAmount());
        if (updates.getCurrency() != null) {
            String c = updates.getCurrency();
            if (StringUtils.hasText(c)) {
                if (!supportedCurrencyService.isSupported(c)) {
                    throw new IllegalArgumentException("Unsupported currency: " + c);
                }
                expense.setCurrency(c.toUpperCase());
            }
        }
        if (updates.getReceiptNumber() != null) expense.setReceiptNumber(updates.getReceiptNumber());
        if (updates.getCategory() != null) expense.setCategory(updates.getCategory());
        if (updates.getTags() != null) expense.setTags(updates.getTags());
        if (updates.getNotes() != null) expense.setNotes(updates.getNotes());
        if (updates.getExchangeRate() != null) expense.setExchangeRate(updates.getExchangeRate());

        expense.setUpdatedAt(LocalDateTime.now());
        computeCurrency(expense, userId);
        return expenseRepository.save(expense);
    }

    @Transactional
    public void softDelete(String urlId, long userId) {
        Expense expense = expenseRepository.findByUrlId(urlId)
                .orElseThrow(() -> new IllegalStateException("Expense not found"));
        if (expense.getUserId() != userId) throw new AuthorizationDeniedException("Not authorized");
        expense.setDeleted(true);
        expense.setUpdatedAt(LocalDateTime.now());
        expenseRepository.save(expense);
        expenseItemRepository.softDeleteByExpenseId(expense.getId());
    }

    @Transactional
    public void restore(String urlId, long userId) {
        Expense expense = expenseRepository.findByUrlId(urlId)
                .orElseThrow(() -> new IllegalStateException("Expense not found"));
        if (expense.getUserId() != userId) throw new AuthorizationDeniedException("Not authorized");
        expense.setDeleted(false);
        expense.setUpdatedAt(LocalDateTime.now());
        expenseRepository.save(expense);
        expenseItemRepository.restoreByExpenseId(expense.getId());
    }

    public void retryOcr(String urlId, long userId) {
        Expense expense = expenseRepository.findByUrlId(urlId)
                .orElseThrow(() -> new IllegalStateException("Expense not found"));
        if (expense.getUserId() != userId) throw new AuthorizationDeniedException("Not authorized");
        expense.setStatus(ExpenseStatus.PROCESSING);
        expense.setUpdatedAt(LocalDateTime.now());
        expense.setNotes("");
        expenseRepository.save(expense);
        ocrService.processReceipt(expense.getId(), expense.getImagePath());
    }

    @Transactional
    public Expense duplicate(String urlId, long userId) {
        Expense original = expenseRepository.findByUrlId(urlId)
                .orElseThrow(() -> new IllegalStateException("Expense not found"));
        if (original.getUserId() != userId) throw new AuthorizationDeniedException("Not authorized");

        Expense copy = new Expense();
        copy.setUserId(userId);
        copy.setType(original.getType());
        copy.setTransactionDatetime(original.getTransactionDatetime());
        copy.setAmount(original.getAmount());
        copy.setCurrency(original.getCurrency());
        copy.setAmountInBase(original.getAmountInBase());
        copy.setExchangeRate(original.getExchangeRate());
        copy.setReceiptNumber(null);
        copy.setCategory(original.getCategory());
        copy.setTags(new ArrayList<>(original.getTags() != null ? original.getTags() : List.of()));
        copy.setNotes(original.getNotes());
        copy.setStatus(ExpenseStatus.COMPLETED);
        copy.setImagePath(original.getImagePath());
        copy.setAttachments(new ArrayList<>(original.getAttachments() != null ? original.getAttachments() : List.of()));
        copy.setDeleted(false);
        copy.setStoreId(original.getStoreId()); // reuse same store
        copy.setCreatedAt(LocalDateTime.now());
        copy.setUpdatedAt(LocalDateTime.now());
        copy.setUrlId(UUID.randomUUID().toString());
        expenseRepository.save(copy);

        // Copy items
        List<ExpenseItem> originalItems = expenseItemRepository.findByExpenseIdAndDeletedFalse(original.getId());
        List<ExpenseItem> copiedItems = new ArrayList<>();
        for (ExpenseItem oi : originalItems) {
            ExpenseItem ci = new ExpenseItem();
            ci.setExpenseId(copy.getId());
            ci.setItemName(oi.getItemName());
            ci.setQuantity(oi.getQuantity());
            ci.setUnitPrice(oi.getUnitPrice());
            ci.setTotalPrice(oi.getTotalPrice());
            ci.setDeleted(false);
            copiedItems.add(ci);
        }
        if (!copiedItems.isEmpty()) expenseItemRepository.saveAll(copiedItems);

        return copy;
    }

    public void softDeleteItem(String urlId, Long itemId, long userId) {
        Expense expense = expenseRepository.findByUrlId(urlId)
                .orElseThrow(() -> new IllegalStateException("Expense not found"));
        if (expense.getUserId() != userId) throw new AuthorizationDeniedException("Not authorized");

        ExpenseItem item = expenseItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalStateException("Item not found"));
        item.setDeleted(true);
        expenseItemRepository.save(item);
        recomputeTotal(expense.getId());
    }

    public void recomputeTotal(Long expenseId) {
        List<ExpenseItem> activeItems = expenseItemRepository.findByExpenseIdAndDeletedFalse(expenseId);
        BigDecimal total = activeItems.stream()
                .map(ExpenseItem::getTotalPrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Expense expense = expenseRepository.findById(expenseId).orElse(null);
        if (expense != null) {
            expense.setAmount(total);
            expense.setUpdatedAt(LocalDateTime.now());
            // Recompute base amount
            if (expense.getExchangeRate() != null) {
                expense.setAmountInBase(total.multiply(expense.getExchangeRate()));
            }
            expenseRepository.save(expense);
        }
    }

    public List<Expense> search(Long userId, String query, boolean includeDeleted) {
        List<Expense> expenses = includeDeleted
                ? expenseRepository.findByUserId(userId)
                : expenseRepository.findByUserIdAndDeletedFalse(userId);
        if (query == null || query.isBlank()) {
            return expenses;
        }
        // Batch-load stores + items ONCE to avoid N+1 queries during filtering
        Map<Long, Store> storeMap = getStoreMapForUser(userId);
        Map<Long, List<ExpenseItem>> itemsByExpenseId = getItemsByExpenseId(expenses);
        return expenses.stream()
                .filter(e -> matchesSearch(e, query, storeMap, itemsByExpenseId))
                .toList();
    }

    private boolean matchesSearch(Expense e, String query,
                                  Map<Long, Store> storeMap,
                                  Map<Long, List<ExpenseItem>> itemsByExpenseId) {
        String q = query.toLowerCase();
        // Match expense fields
        if (contains(e.getCategory(), q) || contains(e.getNotes(), q)
                || contains(e.getReceiptNumber(), q) || contains(e.getCurrency(), q)
                || (e.getAmount() != null && e.getAmount().toPlainString().contains(q))
                || (e.getTags() != null && e.getTags().stream().anyMatch(t -> t.toLowerCase().contains(q)))
                || (e.getTransactionDatetime() != null && e.getTransactionDatetime().toString().contains(q))) {
            return true;
        }
        // Match expense items (from the pre-loaded index)
        List<ExpenseItem> items = itemsByExpenseId.getOrDefault(e.getId(), List.of());
        if (items.stream().anyMatch(i -> contains(i.getItemName(), q))) {
            return true;
        }
        // Match store details (from the pre-loaded map)
        Store s = e.getStoreId() != null ? storeMap.get(e.getStoreId()) : null;
        if (s != null) {
            if (contains(s.getName(), q) || contains(s.getAddress(), q)
                    || contains(s.getCity(), q) || contains(s.getCountry(), q)) {
                return true;
            }
            // Match country name against the stored 2-letter code
            if (s.getCountry() != null) {
                String countryName = countryService.getName(s.getCountry());
                return countryName != null && countryName.toLowerCase().contains(q);
            }
        }
        return false;
    }

    /** Build a {storeId -> Store} map for a user with a single query. */
    public Map<Long, Store> getStoreMapForUser(Long userId) {
        Map<Long, Store> map = new HashMap<>();
        for (Store s : storeRepository.findByUserId(userId)) {
            map.put(s.getId(), s);
        }
        return map;
    }

    /** Build a {expenseId -> [items]} index for the given expenses with a single query. */
    public Map<Long, List<ExpenseItem>> getItemsByExpenseId(List<Expense> expenses) {
        if (expenses.isEmpty()) return Map.of();
        List<Long> ids = expenses.stream().map(Expense::getId).toList();
        Map<Long, List<ExpenseItem>> map = new HashMap<>();
        for (ExpenseItem item : expenseItemRepository.findByExpenseIdInAndDeletedFalse(ids)) {
            map.computeIfAbsent(item.getExpenseId(), k -> new ArrayList<>()).add(item);
        }
        return map;
    }

    private boolean contains(String field, String query) {
        return field != null && field.toLowerCase().contains(query);
    }

    public String addAttachment(String urlId, String originalFilename, byte[] fileBytes) throws IOException {
        Path attachDir = Path.of(dataDir, "attachments", urlId);
        Files.createDirectories(attachDir);
        Path filePath = attachDir.resolve(originalFilename);
        Files.write(filePath, fileBytes);

        Expense expense = expenseRepository.findByUrlId(urlId).orElseThrow(() -> new IllegalStateException("Expense not found"));
        if (expense.getAttachments() == null) expense.setAttachments(new ArrayList<>());
        expense.getAttachments().add(filePath.toString());
        expense.setUpdatedAt(LocalDateTime.now());
        expenseRepository.save(expense);
        return filePath.toString();
    }

    public void removeAttachment(String urlId, String filename) throws IOException {
        Expense expense = expenseRepository.findByUrlId(urlId).orElseThrow(() -> new IllegalStateException("Expense not found"));
        Path attachDir = Path.of(dataDir, "attachments", urlId);
        Path filePath = attachDir.resolve(filename);
        Files.deleteIfExists(filePath);
        if (expense.getAttachments() != null) {
            expense.getAttachments().removeIf(a -> a.endsWith(filename));
            expense.setUpdatedAt(LocalDateTime.now());
            expenseRepository.save(expense);
        }
    }

    private void computeCurrency(Expense expense, Long userId) {
        if (expense.getCurrency() == null || expense.getAmount() == null) return;
        String baseCurrency = userRepository.findById(userId).map(User::getBaseCurrency).orElse(null);
        if (expense.getCurrency().equalsIgnoreCase(baseCurrency)) {
            expense.setExchangeRate(BigDecimal.ONE);
            expense.setAmountInBase(expense.getAmount());
            return;
        }
        // If user provided an exchange rate (manual fallback), use it
        if (expense.getExchangeRate() != null) {
            expense.setAmountInBase(expense.getAmount().multiply(expense.getExchangeRate()));
            return;
        }
        LocalDate date = expense.getTransactionDatetime() != null
                ? expense.getTransactionDatetime().toLocalDate() : LocalDate.now();
        BigDecimal rate = currencyService.getRate(expense.getCurrency(), baseCurrency, date);
        if (rate != null) {
            expense.setExchangeRate(rate);
            expense.setAmountInBase(expense.getAmount().multiply(rate));
        }
    }

    // Item CRUD
    public ExpenseItem saveItem(String urlId, ExpenseItem item) {
        Expense expense = expenseRepository.findByUrlId(urlId)
                .orElseThrow(() -> new IllegalStateException("Expense not found"));
        item.setExpenseId(expense.getId());
        item.setDeleted(false);
        if (item.getQuantity() != null && item.getUnitPrice() != null) {
            item.setTotalPrice(item.getQuantity().multiply(item.getUnitPrice()));
        }
        expenseItemRepository.save(item);
        recomputeTotal(expense.getId());
        return item;
    }

    /**
     * Save or reuse a store for an expense. If a store with matching key fields
     * (name, address, city, country, postalCode) already exists for this user, reuse it.
     * Otherwise create a new store.
     */
    public Store saveStore(String urlId, Store store, Long userId) {
        Expense expense = expenseRepository.findByUrlId(urlId)
                .orElseThrow(() -> new IllegalStateException("Expense not found"));
        store.setUserId(userId);

        // Try to find an existing matching store for this user
        Optional<Store> existing = storeRepository.findMatchingStore(
                userId,
                store.getName(), store.getAddress(), store.getCity(),
                store.getCountry(), store.getPostalCode());

        if (existing.isPresent()) {
            Store matched = getMatched(store, existing);
            storeRepository.save(matched);
            // Link expense to this store
            expense.setStoreId(matched.getId());
            expense.setUpdatedAt(LocalDateTime.now());
            expenseRepository.save(expense);
            return matched;
        }

        // Create new store
        Store saved = storeRepository.save(store);
        expense.setStoreId(saved.getId());
        expense.setUpdatedAt(LocalDateTime.now());
        expenseRepository.save(expense);
        return saved;
    }

    private static Store getMatched(Store store, Optional<Store> existing) {
        Store matched = existing.get();
        // Update mutable fields on the matched store
        if (store.getPhoneNumber() != null) matched.setPhoneNumber(store.getPhoneNumber());
        if (store.getWebsite() != null) matched.setWebsite(store.getWebsite());
        if (store.getLatitude() != null) matched.setLatitude(store.getLatitude());
        if (store.getLongitude() != null) matched.setLongitude(store.getLongitude());
        if (store.getSourceId() != null) matched.setSourceId(store.getSourceId());
        return matched;
    }

    /** Backward-compatible overload */
    public Store saveStore(String urlId, Store store) {
        Expense expense = expenseRepository.findByUrlId(urlId)
                .orElseThrow(() -> new IllegalStateException("Expense not found"));
        return saveStore(urlId, store, expense.getUserId());
    }

    /**
     * Get the store associated with an expense, if any.
     */
    public Optional<Store> getStoreForExpense(Expense expense) {
        if (expense.getStoreId() == null) return Optional.empty();
        return storeRepository.findById(expense.getStoreId());
    }

    /**
     * Get the store associated with an expense by expense ID.
     */
    public Optional<Store> getStoreForExpense(Long expenseId) {
        return expenseRepository.findById(expenseId)
                .flatMap(e -> e.getStoreId() != null ? storeRepository.findById(e.getStoreId()) : Optional.empty());
    }

    /**
     * Derive the user's home city and country based on the city+country with the most expenses.
     * Only sets baseCity/baseCountry when they are currently null.
     * Returns the updated User, or the original if no change was needed.
     */
    public User deriveAndSetBaseLocation(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return null;

        // Only derive if baseCity and baseCountry are both null
        if (user.getBaseCity() != null && user.getBaseCountry() != null) {
            return user;
        }

        List<Expense> expenses = expenseRepository.findByUserIdAndDeletedFalse(userId);
        if (expenses.isEmpty()) return user;

        // Batch-load this user's stores in a single query (avoids N+1)
        Map<Long, Store> storeMap = getStoreMapForUser(userId);

        // Count expenses by city+country combination
        Map<String, Integer> locationCounts = new HashMap<>();
        Map<String, String> locationCity = new HashMap<>();
        Map<String, String> locationCountry = new HashMap<>();

        for (Expense e : expenses) {
            Store store = e.getStoreId() != null ? storeMap.get(e.getStoreId()) : null;
            if (store == null) continue;
            String city = store.getCity();
            String country = store.getCountry();
            if (city != null && !city.isBlank() && country != null && !country.isBlank()) {
                String key = city.toLowerCase() + "|" + country.toUpperCase();
                locationCounts.merge(key, 1, Integer::sum);
                locationCity.put(key, city);
                locationCountry.put(key, country);
            }
        }

        if (locationCounts.isEmpty()) return user;

        // Find the city+country with the most expenses
        String topKey = locationCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        if (topKey != null) {
            String derivedCity = locationCity.get(topKey);
            String derivedCountry = locationCountry.get(topKey);

            if (user.getBaseCity() == null) user.setBaseCity(derivedCity);
            if (user.getBaseCountry() == null) user.setBaseCountry(derivedCountry);
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);
            log.info("Derived base location for user {}: city={}, country={}", userId, derivedCity, derivedCountry);
        }

        return user;
    }
}
