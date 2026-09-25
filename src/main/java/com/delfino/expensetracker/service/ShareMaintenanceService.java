package com.delfino.expensetracker.service;

import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.ExpenseShare;
import com.delfino.expensetracker.repository.ExpenseRepository;
import com.delfino.expensetracker.repository.ExpenseShareRepository;
import com.delfino.expensetracker.repository.ShareAccessLogRepository;
import com.delfino.expensetracker.util.ReceiptStorageUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class ShareMaintenanceService {

    private static final Logger log = LoggerFactory.getLogger(ShareMaintenanceService.class);

    private final ExpenseRepository expenseRepository;
    private final ExpenseShareRepository expenseShareRepository;
    private final ShareAccessLogRepository shareAccessLogRepository;
    private final TransactionTemplate transactionTemplate;

    @Value("${app.data.dir:data}")
    private String dataDir;

    @Value("${app.sharing.cleanup-retention-days:90}")
    private long cleanupRetentionDays;

    public ShareMaintenanceService(ExpenseRepository expenseRepository,
                                   ExpenseShareRepository expenseShareRepository,
                                   ShareAccessLogRepository shareAccessLogRepository,
                                   TransactionTemplate transactionTemplate) {
        this.expenseRepository = expenseRepository;
        this.expenseShareRepository = expenseShareRepository;
        this.shareAccessLogRepository = shareAccessLogRepository;
        this.transactionTemplate = transactionTemplate;
    }

    @Scheduled(cron = "${app.sharing.cleanup-cron:0 17 3 * * *}")
    public void runNightlyCleanup() {
        removeOrphanedReceiptFiles();
        purgeOldShareData();
    }

    public int removeOrphanedReceiptFiles() {
        Path receiptsRoot = Path.of(dataDir).resolve("receipts");
        if (!Files.exists(receiptsRoot)) {
            return 0;
        }

        Set<String> referencedReceipts = expenseRepository.findAll().stream()
                .map(Expense::getImagePath)
                .filter(path -> path != null && !path.isBlank())
                .map(path -> ReceiptStorageUtils.relativizeToDataDir(dataDir,
                        ReceiptStorageUtils.resolveStoredPath(dataDir, path)))
                .map(ReceiptStorageUtils::normalizeSeparators)
                .filter(path -> path.startsWith("receipts/"))
                .collect(HashSet::new, Set::add, Set::addAll);

        int deleted = 0;
        try (Stream<Path> paths = Files.walk(receiptsRoot)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String relativePath = ReceiptStorageUtils.relativizeToDataDir(dataDir, path);
                if (referencedReceipts.contains(relativePath)) {
                    continue;
                }
                Files.deleteIfExists(path);
                deleted++;
                log.info("Deleted orphaned receipt file {}", relativePath);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed cleaning orphaned receipt files", e);
        }

        removeEmptyDirectories(receiptsRoot);
        return deleted;
    }

    public int purgeOldShareData() {
        return transactionTemplate.execute(status -> {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(Math.max(1, cleanupRetentionDays));
            Set<Long> shareIdsToDelete = new HashSet<>();
            expenseShareRepository.findByRevokedAtBefore(cutoff).stream()
                    .map(ExpenseShare::getId)
                    .forEach(shareIdsToDelete::add);
            expenseShareRepository.findByExpiresAtBefore(cutoff).stream()
                    .map(ExpenseShare::getId)
                    .forEach(shareIdsToDelete::add);

            int deletedShares = 0;
            if (!shareIdsToDelete.isEmpty()) {
                List<ExpenseShare> shares = expenseShareRepository.findAllById(shareIdsToDelete);
                deletedShares = shares.size();
                expenseShareRepository.deleteAll(shares);
                log.info("Deleted {} expired/revoked share records older than {} days", deletedShares, cleanupRetentionDays);
            }

            long deletedAccessLogs = shareAccessLogRepository.deleteByAccessedAtBefore(cutoff);
            if (deletedAccessLogs > 0) {
                log.info("Deleted {} historical share access log rows older than {} days", deletedAccessLogs, cleanupRetentionDays);
            }
            return deletedShares;
        });
    }

    private void removeEmptyDirectories(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder())
                    .filter(Files::isDirectory)
                    .filter(path -> !path.equals(root))
                    .forEach(path -> {
                        try (Stream<Path> children = Files.list(path)) {
                            if (children.findAny().isEmpty()) {
                                Files.deleteIfExists(path);
                            }
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed removing empty receipt directories", e);
        }
    }
}

