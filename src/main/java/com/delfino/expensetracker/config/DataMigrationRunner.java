package com.delfino.expensetracker.config;

import com.delfino.expensetracker.model.*;
import com.delfino.expensetracker.repository.*;
import com.delfino.expensetracker.service.GeocodingService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * One-time migration runner that reads existing JSON data files
 * and imports them into the SQLite database on the first startup.
 *
 * The migration runs only when the database is empty AND JSON files exist.
 * After successful migration, JSON files are renamed to *.json.migrated as backup.
 *
 * Can be disabled by setting app.migration.enabled=false in application.yml.
 */
@Component
public class DataMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataMigrationRunner.class);

    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseItemRepository expenseItemRepository;
    private final StoreRepository storeRepository;
    private final ExchangeRateCacheRepository exchangeRateCacheRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final GeocodingService geocodingService;
    private final DataSource dataSource;

    @Value("${app.data.dir:data}")
    private String dataDir;

    @Value("${app.migration.enabled:true}")
    private boolean migrationEnabled;

    public DataMigrationRunner(ObjectMapper objectMapper,
                               UserRepository userRepository,
                               ExpenseRepository expenseRepository,
                               ExpenseItemRepository expenseItemRepository,
                               StoreRepository storeRepository,
                               ExchangeRateCacheRepository exchangeRateCacheRepository,
                               ChatMessageRepository chatMessageRepository,
                               GeocodingService geocodingService,
                               DataSource dataSource) {
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.expenseRepository = expenseRepository;
        this.expenseItemRepository = expenseItemRepository;
        this.storeRepository = storeRepository;
        this.exchangeRateCacheRepository = exchangeRateCacheRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.geocodingService = geocodingService;
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!migrationEnabled) {
            log.info("JSON-to-PostgreSQL migration is disabled (app.migration.enabled=false)");
        } else {
            runMigration();
        }

        // Migrate store-expense relationship: move expenseId from Store to storeId on Expense
        migrateStoreExpenseRelationship();

        // Always retroactively geocode stores without coordinates
        try {
            geocodingService.geocodeAllStoresWithoutCoordinates();
        } catch (Exception e) {
            log.error("Retroactive geocoding failed", e);
        }
    }

    /**
     * One-time migration: links existing stores to their expenses.
     *
     * Strategy A (preferred): if stores still have the old expense_id column populated,
     *   use it to set expenses.store_id and stores.user_id directly.
     *
     * Strategy B (fallback): if expense_id is all null (already cleared), link stores to
     *   RECEIPT_SCAN expenses by id insertion order (both were created together during OCR).
     *
     * Also sets stores.user_id from the linked expense's user_id.
     */
    private void migrateStoreExpenseRelationship() {
        try (Connection conn = dataSource.getConnection()) {

            // Check if there are any expenses that already have store_id set
            int alreadyLinked = 0;
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM expenses WHERE store_id IS NOT NULL")) {
                if (rs.next()) alreadyLinked = rs.getInt(1);
            }

            // Check if stores have expense_id data
            int storesWithExpenseId = 0;
            boolean hasExpenseIdCol;
            try (ResultSet rs = conn.getMetaData().getColumns(null, null, "stores", "expense_id")) {
                hasExpenseIdCol = rs.next();
            }
            if (hasExpenseIdCol) {
                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM stores WHERE expense_id IS NOT NULL")) {
                    if (rs.next()) storesWithExpenseId = rs.getInt(1);
                }
            }

            int totalStores = 0;
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM stores")) {
                if (rs.next()) totalStores = rs.getInt(1);
            }

            if (totalStores == 0) {
                log.debug("No stores found — skipping store-expense relationship migration");
                return;
            }

            log.info("Store migration: {} stores total, {} with expense_id, {} expenses already linked",
                    totalStores, storesWithExpenseId, alreadyLinked);

            // ── Strategy A: use expense_id if available ──
            if (storesWithExpenseId > 0) {
                log.info("=== Strategy A: migrating via stores.expense_id ===");
                int count = 0;
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(
                             "SELECT id, expense_id FROM stores WHERE expense_id IS NOT NULL")) {
                    while (rs.next()) {
                        long storeId = rs.getLong("id");
                        long expenseId = rs.getLong("expense_id");
                        if (rs.wasNull()) continue;
                        try (PreparedStatement ps = conn.prepareStatement(
                                "UPDATE expenses SET store_id = ? WHERE id = ? AND store_id IS NULL")) {
                            ps.setLong(1, storeId);
                            ps.setLong(2, expenseId);
                            ps.executeUpdate();
                        }
                        try (PreparedStatement ps = conn.prepareStatement(
                                "UPDATE stores SET user_id = (SELECT user_id FROM expenses WHERE id = ?) " +
                                        "WHERE id = ? AND user_id IS NULL")) {
                            ps.setLong(1, expenseId);
                            ps.setLong(2, storeId);
                            ps.executeUpdate();
                        }
                        count++;
                    }
                }
                log.info("Strategy A: linked {} stores via expense_id", count);
                return;
            }

            // ── Strategy B: id ordering ──
            // Stores were created in the same order as RECEIPT_SCAN expenses during OCR.
            // Match them by id insertion order.
            if (alreadyLinked == 0) {
                log.info("=== Strategy B: linking stores to RECEIPT_SCAN expenses by id order ===");

                List<Long> scanExpenseIds = new ArrayList<>();
                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery(
                             "SELECT id FROM expenses WHERE type='RECEIPT_SCAN' ORDER BY created_at ASC, id ASC")) {
                    while (rs.next()) scanExpenseIds.add(rs.getLong("id"));
                }

                List<Long> storeIds = new ArrayList<>();
                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery("SELECT id FROM stores ORDER BY id ASC")) {
                    while (rs.next()) storeIds.add(rs.getLong("id"));
                }

                int linked = 0;
                int limit = Math.min(storeIds.size(), scanExpenseIds.size());
                for (int i = 0; i < limit; i++) {
                    long sid = storeIds.get(i);
                    long eid = scanExpenseIds.get(i);
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE expenses SET store_id = ? WHERE id = ? AND store_id IS NULL")) {
                        ps.setLong(1, sid);
                        ps.setLong(2, eid);
                        ps.executeUpdate();
                    }
                    linked++;
                }
                log.info("Strategy B: linked {} stores to RECEIPT_SCAN expenses", linked);
            }

            // Always: set user_id on stores that don't have it yet
            int userIdSet = 0;
            // Get the user's id first (single-user app)
            long primaryUserId = 0;
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT id FROM users LIMIT 1")) {
                if (rs.next()) primaryUserId = rs.getLong("id");
            }
            if (primaryUserId != 0) {
                try (Statement st = conn.createStatement();
                     ResultSet rs = st.executeQuery("SELECT id FROM stores WHERE user_id IS NULL OR user_id = 0")) {
                    List<Long> noUserStores = new ArrayList<>();
                    while (rs.next()) noUserStores.add(rs.getLong("id"));
                    for (long sid : noUserStores) {
                        // Try to find the user via linked expense
                        long uid = 0;
                        try (PreparedStatement ps = conn.prepareStatement(
                                "SELECT e.user_id FROM expenses e WHERE e.store_id = ? LIMIT 1")) {
                            ps.setLong(1, sid);
                            ResultSet er = ps.executeQuery();
                            if (er.next()) uid = er.getLong("user_id");
                        }
                        if (uid == 0) uid = primaryUserId; // fallback to primary user
                        try (PreparedStatement ps = conn.prepareStatement(
                                "UPDATE stores SET user_id = ? WHERE id = ? AND (user_id IS NULL OR user_id = 0)")) {
                            ps.setLong(1, uid);
                            ps.setLong(2, sid);
                            userIdSet += ps.executeUpdate();
                        }
                    }
                }
            }
            if (userIdSet > 0) log.info("Set user_id on {} stores", userIdSet);

        } catch (Exception e) {
            log.warn("Store-expense relationship migration warning: {}", e.getMessage());
        }
    }

    private void runMigration() {
        // Only migrate if the database is empty
        if (userRepository.count() > 0 || expenseRepository.count() > 0) {
            log.info("Database already contains data — skipping JSON migration");
            return;
        }

        File dir = new File(dataDir);
        if (!dir.exists()) {
            log.info("No data directory found at '{}' — skipping JSON migration", dataDir);
            return;
        }

        // Check if any JSON files exist
        boolean hasJsonFiles = new File(dir, "users.json").exists()
                || new File(dir, "expenses.json").exists();

        if (!hasJsonFiles) {
            log.info("No JSON data files found in '{}' — skipping migration", dataDir);
            return;
        }

        log.info("=== Starting JSON-to-PostgreSQL data migration ===");

        try {
            migrateUsers(dir);
            migrateExpenses(dir);
            migrateExpenseItems(dir);
            migrateStores(dir);
            migrateExchangeRateCache(dir);
            migrateChatMessages(dir);

            log.info("=== JSON-to-PostgreSQL migration completed successfully ===");
        } catch (Exception e) {
            log.error("JSON-to-PostgreSQL migration FAILED — database may be partially populated", e);
        }
    }

    private void migrateUsers(File dir) throws IOException {
        File file = new File(dir, "users.json");
        if (!file.exists()) return;

        List<User> users = objectMapper.readValue(file, new TypeReference<List<User>>() {});
        if (!users.isEmpty()) {
            userRepository.saveAll(users);
            log.info("Migrated {} users", users.size());
        }
        renameToMigrated(file);
    }

    private void migrateExpenses(File dir) throws IOException {
        File file = new File(dir, "expenses.json");
        if (!file.exists()) return;

        List<Expense> expenses = objectMapper.readValue(file, new TypeReference<List<Expense>>() {});
        if (!expenses.isEmpty()) {
            expenseRepository.saveAll(expenses);
            log.info("Migrated {} expenses", expenses.size());
        }
        renameToMigrated(file);
    }

    private void migrateExpenseItems(File dir) throws IOException {
        File file = new File(dir, "expense-items.json");
        if (!file.exists()) return;

        List<ExpenseItem> items = objectMapper.readValue(file, new TypeReference<List<ExpenseItem>>() {});
        if (!items.isEmpty()) {
            expenseItemRepository.saveAll(items);
            log.info("Migrated {} expense items", items.size());
        }
        renameToMigrated(file);
    }

    private void migrateStores(File dir) throws IOException {
        File file = new File(dir, "stores.json");
        if (!file.exists()) return;

        List<Store> stores = objectMapper.readValue(file, new TypeReference<List<Store>>() {});
        if (!stores.isEmpty()) {
            storeRepository.saveAll(stores);
            log.info("Migrated {} stores", stores.size());
        }
        renameToMigrated(file);
    }

    private void migrateExchangeRateCache(File dir) throws IOException {
        File file = new File(dir, "exchange-rates-cache.json");
        if (!file.exists()) return;

        List<ExchangeRateCache> entries = objectMapper.readValue(file,
                new TypeReference<List<ExchangeRateCache>>() {});
        if (!entries.isEmpty()) {
            exchangeRateCacheRepository.saveAll(entries);
            log.info("Migrated {} exchange rate cache entries", entries.size());
        }
        renameToMigrated(file);
    }

    private void migrateChatMessages(File dir) throws IOException {
        File file = new File(dir, "chat-messages.json");
        if (!file.exists()) return;

        List<ChatMessage> messages = objectMapper.readValue(file,
                new TypeReference<List<ChatMessage>>() {});
        if (!messages.isEmpty()) {
            chatMessageRepository.saveAll(messages);
            log.info("Migrated {} chat messages", messages.size());
        }
        renameToMigrated(file);
    }

    private void renameToMigrated(File file) {
        try {
            Path source = file.toPath();
            Path target = source.resolveSibling(file.getName() + ".migrated");
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("Renamed {} → {}", file.getName(), target.getFileName());
        } catch (IOException e) {
            log.warn("Could not rename {} to .migrated: {}", file.getName(), e.getMessage());
        }
    }
}

