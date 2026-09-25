package com.delfino.expensetracker.service;

import com.delfino.expensetracker.BaseControllerTest;
import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.ExpenseShare;
import com.delfino.expensetracker.model.ShareAccessLog;
import com.delfino.expensetracker.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ShareMaintenanceServiceTest extends BaseControllerTest {

    @Autowired
    private ShareMaintenanceService shareMaintenanceService;

    @Test
    void removeOrphanedReceiptFiles_deletesUnreferencedFilesOnly() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        expense.setImagePath("receipts/" + user.getId() + "/2026_05/keep.txt");
        expenseRepository.save(expense);

        Path receiptDir = Path.of("target/test-data/receipts", String.valueOf(user.getId()), "2026_05");
        Files.createDirectories(receiptDir);
        Path keep = receiptDir.resolve("keep.txt");
        Path orphan = receiptDir.resolve("orphan.txt");
        Files.writeString(keep, "keep");
        Files.writeString(orphan, "orphan");

        int deleted = shareMaintenanceService.removeOrphanedReceiptFiles();

        assertThat(deleted).isEqualTo(1);
        assertThat(Files.exists(keep)).isTrue();
        assertThat(Files.exists(orphan)).isFalse();
    }

    @Test
    void purgeOldShareData_deletesExpiredSharesAndOldAccessLogs() {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");

        ExpenseShare expiredShare = new ExpenseShare();
        expiredShare.setExpenseId(expense.getId());
        expiredShare.setShareToken(UUID.randomUUID().toString());
        expiredShare.setCreatedAt(LocalDateTime.now().minusDays(120));
        expiredShare.setExpiresAt(LocalDateTime.now().minusDays(91));
        expiredShare.setCreatedBy(user.getId());
        expenseShareRepository.save(expiredShare);

        ExpenseShare activeShare = new ExpenseShare();
        activeShare.setExpenseId(expense.getId());
        activeShare.setShareToken(UUID.randomUUID().toString());
        activeShare.setCreatedAt(LocalDateTime.now().minusDays(1));
        activeShare.setExpiresAt(LocalDateTime.now().plusDays(10));
        activeShare.setCreatedBy(user.getId());
        expenseShareRepository.save(activeShare);

        ShareAccessLog oldLog = new ShareAccessLog();
        oldLog.setShareId(expiredShare.getId());
        oldLog.setExpenseId(expense.getId());
        oldLog.setResourceType("EXPENSE");
        oldLog.setAllowed(true);
        oldLog.setStatusCode(200);
        oldLog.setAccessedAt(LocalDateTime.now().minusDays(100));
        shareAccessLogRepository.save(oldLog);

        ShareAccessLog freshLog = new ShareAccessLog();
        freshLog.setShareId(activeShare.getId());
        freshLog.setExpenseId(expense.getId());
        freshLog.setResourceType("EXPENSE");
        freshLog.setAllowed(true);
        freshLog.setStatusCode(200);
        freshLog.setAccessedAt(LocalDateTime.now().minusDays(5));
        shareAccessLogRepository.save(freshLog);

        int deletedShares = shareMaintenanceService.purgeOldShareData();

        assertThat(deletedShares).isEqualTo(1);
        assertThat(expenseShareRepository.findById(expiredShare.getId())).isEmpty();
        assertThat(expenseShareRepository.findById(activeShare.getId())).isPresent();
        assertThat(shareAccessLogRepository.findAll())
                .singleElement()
                .satisfies(log -> assertThat(log.getId()).isEqualTo(freshLog.getId()));
    }
}

