package com.delfino.expensetracker.config;

import com.delfino.expensetracker.BaseControllerTest;
import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.ExpenseStatus;
import com.delfino.expensetracker.model.ExpenseType;
import com.delfino.expensetracker.model.User;
import com.delfino.expensetracker.util.ReceiptStorageUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DataMigrationRunnerTest extends BaseControllerTest {

    @Autowired
    private DataMigrationRunner dataMigrationRunner;

    @Test
    void migrateReceiptStorageLayout_movesLegacyFlatReceiptAndUpdatesExpensePath() throws Exception {
        User user = createTestUser("alice", "pass");
        Path legacyDir = Path.of("target/test-data/receipts");
        Files.createDirectories(legacyDir);
        Path legacyFile = legacyDir.resolve("20260501_120000_000_" + user.getId() + "_receipt.png");
        Files.writeString(legacyFile, "legacy receipt");

        Expense expense = new Expense();
        expense.setUserId(user.getId());
        expense.setType(ExpenseType.RECEIPT_SCAN);
        expense.setStatus(ExpenseStatus.COMPLETED);
        expense.setDeleted(false);
        expense.setCreatedAt(LocalDateTime.of(2026, 5, 2, 10, 0));
        expense.setUpdatedAt(LocalDateTime.of(2026, 5, 2, 10, 0));
        expense.setScannedAt(LocalDateTime.of(2026, 5, 2, 10, 0));
        expense.setUrlId(java.util.UUID.randomUUID().toString());
        expense.setImagePath("target/test-data/receipts/" + legacyFile.getFileName());
        expense = expenseRepository.save(expense);

        dataMigrationRunner.migrateReceiptStorageLayout();

        Expense migrated = expenseRepository.findById(expense.getId()).orElseThrow();
        assertThat(migrated.getImagePath()).matches("receipts/" + user.getId() + "/2026_05/[0-9a-fA-F-]+\\.png");
        assertThat(Files.exists(legacyFile)).isFalse();
        assertThat(Files.exists(ReceiptStorageUtils.resolveStoredPath("target/test-data", migrated.getImagePath()))).isTrue();

        String migratedPath = migrated.getImagePath();
        dataMigrationRunner.migrateReceiptStorageLayout();
        Expense rerun = expenseRepository.findById(expense.getId()).orElseThrow();
        assertThat(rerun.getImagePath()).isEqualTo(migratedPath);
    }
}


