package com.delfino.expensetracker.service;

import com.delfino.expensetracker.dto.expense.ExpenseExportDto;
import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.ExpenseItem;
import com.delfino.expensetracker.model.User;
import com.delfino.expensetracker.repository.ExpenseItemRepository;
import com.delfino.expensetracker.repository.ExpenseRepository;
import com.delfino.expensetracker.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Assembles a full account export ZIP archive for the current user.
 *
 * Archive layout:
 * <pre>
 * account-export-YYYY-MM-DD.zip
 *   account.json
 *   expenses.json
 *   expenses.csv
 *   metadata.json
 *   receipts/       (scanned receipt images)
 *   attachments/
 *     {expenseUrlId}/
 *       ...files...
 * </pre>
 */
@Service
public class UserExportService {

    private static final Logger log = LoggerFactory.getLogger(UserExportService.class);
    private static final String FORMAT_VERSION = "1";

    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenseItemRepository expenseItemRepository;
    private final ObjectMapper mapper;

    @Value("${app.data.dir:data}")
    private String dataDir;

    @Value("${app.version:unknown}")
    private String appVersion;

    public UserExportService(UserRepository userRepository, ExpenseRepository expenseRepository,
                             ExpenseItemRepository expenseItemRepository) {
        this.userRepository = userRepository;
        this.expenseRepository = expenseRepository;
        this.expenseItemRepository = expenseItemRepository;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Build the full export archive and return it as a byte array.
     * Only includes active (non-deleted) expenses.
     */
    public byte[] buildExportZip(long userId) throws IOException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("User not found"));
        List<Expense> expenses = expenseRepository.findByUserIdAndDeletedFalse(userId);

        // Batch-load items
        Map<Long, List<ExpenseItem>> itemsByExpense = new HashMap<>();
        if (!expenses.isEmpty()) {
            List<Long> ids = expenses.stream().map(Expense::getId).toList();
            for (ExpenseItem item : expenseItemRepository.findByExpenseIdInAndDeletedFalse(ids)) {
                itemsByExpense.computeIfAbsent(item.getExpenseId(), k -> new ArrayList<>()).add(item);
            }
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {

            // account.json
            Map<String, Object> account = new LinkedHashMap<>();
            account.put("username", user.getUsername());
            account.put("email", user.getEmail());
            account.put("phoneNumber", user.getPhoneNumber());
            account.put("baseCurrency", user.getBaseCurrency());
            account.put("baseCity", user.getBaseCity());
            account.put("baseCountry", user.getBaseCountry());
            account.put("createdAt", user.getCreatedAt());
            account.put("updatedAt", user.getUpdatedAt());
            writeZipEntry(zos, "account.json", mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(account));

            // expenses.json — with items embedded
            List<ExpenseExportDto> expenseDtos = expenses.stream()
                    .map(e -> ExpenseExportDto.fromWithItems(e, itemsByExpense.getOrDefault(e.getId(), List.of())))
                    .toList();
            writeZipEntry(zos, "expenses.json", mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(expenseDtos));

            // expenses.csv — with items column
            writeZipEntry(zos, "expenses.csv", buildCsv(expenses, itemsByExpense).getBytes(StandardCharsets.UTF_8));

            // metadata.json
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("exportedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            meta.put("appVersion", appVersion);
            meta.put("formatVersion", FORMAT_VERSION);
            meta.put("expenseCount", expenses.size());
            writeZipEntry(zos, "metadata.json", mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(meta));

            // Receipt images
            int receiptCount = 0;
            for (Expense e : expenses) {
                if (e.getImagePath() != null && !e.getImagePath().isBlank()) {
                    Path img = Path.of(e.getImagePath());
                    if (Files.exists(img)) {
                        writeZipEntry(zos, "receipts/" + img.getFileName(), Files.readAllBytes(img));
                        receiptCount++;
                    }
                }
            }

            // Attachment directories
            int attachmentCount = 0;
            for (Expense e : expenses) {
                if (e.getUrlId() == null) continue;
                Path attachDir = Path.of(dataDir, "attachments", e.getUrlId());
                if (!Files.isDirectory(attachDir)) continue;
                try (var stream = Files.list(attachDir)) {
                    for (Path f : stream.toList()) {
                        if (Files.isRegularFile(f)) {
                            writeZipEntry(zos, "attachments/" + e.getUrlId() + "/" + f.getFileName(), Files.readAllBytes(f));
                            attachmentCount++;
                        }
                    }
                }
            }

            log.info("Built export archive for user {}: {} expenses, {} receipts, {} attachments",
                    userId, expenses.size(), receiptCount, attachmentCount);
        }
        return baos.toByteArray();
    }

    // --- helpers ---

    private void writeZipEntry(ZipOutputStream zos, String name, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setSize(data.length);
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }

    private String buildCsv(List<Expense> expenses, Map<Long, List<ExpenseItem>> itemsByExpense) {
        StringBuilder sb = new StringBuilder();
        sb.append("ID,Date,Amount,Currency,AmountInBase,Category,ReceiptNumber,Type,Status,Notes,Tags,Items\n");
        for (Expense e : expenses) {
            List<ExpenseItem> items = itemsByExpense.getOrDefault(e.getId(), List.of());
            String itemsSummary = items.isEmpty() ? "" :
                    items.stream()
                            .map(i -> escapeCsv(i.getItemName()) + "(" + i.getQuantity() + "×" + i.getUnitPrice() + ")")
                            .reduce((a, b) -> a + "|" + b).orElse("");
            sb.append(String.join(",",
                    e.getUrlId() != null ? e.getUrlId() : "",
                    e.getTransactionDatetime() != null ? e.getTransactionDatetime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : "",
                    e.getAmount() != null ? e.getAmount().toPlainString() : "",
                    e.getCurrency() != null ? e.getCurrency() : "",
                    e.getAmountInBase() != null ? e.getAmountInBase().toPlainString() : "",
                    escapeCsv(e.getCategory()),
                    escapeCsv(e.getReceiptNumber()),
                    e.getType() != null ? e.getType().name() : "",
                    e.getStatus() != null ? e.getStatus().name() : "",
                    escapeCsv(e.getNotes()),
                    e.getTags() != null ? String.join(";", e.getTags()) : "",
                    escapeCsv(itemsSummary)
            )).append("\n");
        }
        return sb.toString();
    }

    private String escapeCsv(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }
}

