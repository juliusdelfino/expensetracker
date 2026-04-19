package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.BaseControllerTest;
import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.ExpenseItem;
import com.delfino.expensetracker.model.ExpenseStatus;
import com.delfino.expensetracker.model.ExpenseType;
import com.delfino.expensetracker.model.Store;
import com.delfino.expensetracker.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ExpenseControllerTest extends BaseControllerTest {

    // ==================== GET /api/expenses ====================

    @Test
    void listExpenses_authenticated_returnsExpenses() throws Exception {
        User user = createTestUser("alice", "pass");
        createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        createTestExpense(user.getId(), "Transport", BigDecimal.valueOf(5), "USD");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/expenses").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void listExpenses_excludesDeletedByDefault() throws Exception {
        User user = createTestUser("alice", "pass");
        createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        Expense deleted = createTestExpense(user.getId(), "Deleted", BigDecimal.ONE, "USD");
        deleted.setDeleted(true);
        expenseRepository.save(deleted);
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/expenses").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void listExpenses_includeDeleted() throws Exception {
        User user = createTestUser("alice", "pass");
        createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        Expense deleted = createTestExpense(user.getId(), "Deleted", BigDecimal.ONE, "USD");
        deleted.setDeleted(true);
        expenseRepository.save(deleted);
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/expenses?includeDeleted=true").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void listExpenses_filterByCategory() throws Exception {
        User user = createTestUser("alice", "pass");
        createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        createTestExpense(user.getId(), "Transport", BigDecimal.valueOf(5), "USD");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/expenses?category=Food").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].category").value("Food"));
    }

    @Test
    void listExpenses_filterByDateRange() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense old = createTestExpense(user.getId(), "Old", BigDecimal.TEN, "USD");
        old.setTransactionDatetime(LocalDateTime.of(2024, 1, 15, 12, 0));
        expenseRepository.save(old);

        Expense recent = createTestExpense(user.getId(), "Recent", BigDecimal.TEN, "USD");
        recent.setTransactionDatetime(LocalDateTime.of(2026, 3, 10, 12, 0));
        expenseRepository.save(recent);

        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/expenses?startDate=2026-01-01&endDate=2026-12-31").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].category").value("Recent"));
    }

    @Test
    void listExpenses_filterBySearch() throws Exception {
        User user = createTestUser("alice", "pass");
        createTestExpense(user.getId(), "Groceries", BigDecimal.TEN, "USD");
        createTestExpense(user.getId(), "Transport", BigDecimal.TEN, "USD");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/expenses?search=grocer").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].category").value("Groceries"));
    }

    @Test
    void listExpenses_filterByCountry() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        Store store = createTestStore(user.getId(), "FairPrice", "Singapore", "SG");
        linkExpenseToStore(expense, store);

        Expense other = createTestExpense(user.getId(), "Transport", BigDecimal.TEN, "USD");
        Store storeUS = createTestStore(user.getId(), "Walmart", "New York", "US");
        linkExpenseToStore(other, storeUS);

        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/expenses?country=SG").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].country").value("SG"));
    }

    @Test
    void listExpenses_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/expenses"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listExpenses_onlyShowsOwnExpenses() throws Exception {
        User alice = createTestUser("alice", "pass");
        User bob = createTestUser("bob", "pass");
        createTestExpense(alice.getId(), "Food", BigDecimal.TEN, "USD");
        createTestExpense(bob.getId(), "Transport", BigDecimal.TEN, "USD");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/expenses").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    // ==================== GET /api/expenses/categories ====================

    @Test
    void categories_returnsDistinctCategories() throws Exception {
        User user = createTestUser("alice", "pass");
        createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        createTestExpense(user.getId(), "Transport", BigDecimal.TEN, "USD");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/expenses/categories").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void categories_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/expenses/categories"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== GET /api/expenses/{urlId} ====================

    @Test
    void getExpense_existingUrl_returnsExpenseData() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        Store store = createTestStore(user.getId(), "FairPrice", "Singapore", "SG");
        linkExpenseToStore(expense, store);
        createTestItem(expense.getId(), "Bread", BigDecimal.ONE, BigDecimal.valueOf(2));

        mockMvc.perform(get("/api/expenses/" + expense.getUrlId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expense.category").value("Food"))
                .andExpect(jsonPath("$.store.name").value("FairPrice"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.isOwner").value(false));
    }

    @Test
    void getExpense_authenticatedOwner_isOwnerTrue() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/expenses/" + expense.getUrlId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isOwner").value(true));
    }

    @Test
    void getExpense_notFound_returns404() throws Exception {
        createTestUser("alice", "pass");
        MockHttpSession session = loginAs("alice", "pass");
        mockMvc.perform(get("/api/expenses/nonexistent-url-id").session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    void getExpense_noStore_storeIsNull() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/expenses/" + expense.getUrlId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.store").isEmpty());
    }

    // ==================== POST /api/expenses/manual ====================

    @Test
    void createManual_success() throws Exception {
        createTestUser("alice", "pass");
        MockHttpSession session = loginAs("alice", "pass");

        Map<String, Object> body = Map.of(
                "category", "Food",
                "amount", 12.50,
                "currency", "USD",
                "transactionDatetime", "2026-04-01T12:00:00"
        );

        mockMvc.perform(post("/api/expenses/manual")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("Food"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.urlId").isString());
    }

    @Test
    void createManual_invalidCurrency_returnsBadRequest() throws Exception {
        createTestUser("alice", "pass");
        MockHttpSession session = loginAs("alice", "pass");

        Map<String, Object> body = Map.of(
                "amount", 10.0,
                "currency", "FAKE"
        );

        mockMvc.perform(post("/api/expenses/manual")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unsupported currency: FAKE"));
    }

    @Test
    void createManual_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/expenses/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createManual_noCurrencyProvided_success() throws Exception {
        createTestUser("alice", "pass");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(post("/api/expenses/manual")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "category", "Misc",
                                "amount", 5.0,
                                "currency", "EUR"
                        ))))
                .andExpect(status().isOk());
    }

    // ==================== POST /api/expenses/scan ====================

    @Test
    void scanReceipt_success() throws Exception {
        createTestUser("alice", "pass");
        MockHttpSession session = loginAs("alice", "pass");

        byte[] minimalJpeg = new byte[] {(byte)0xFF, (byte)0xD8, (byte)0xFF, (byte)0xE0};

        MockMultipartFile file = new MockMultipartFile(
                "file", "receipt.jpg", MediaType.IMAGE_JPEG_VALUE, minimalJpeg
        );

        mockMvc.perform(multipart("/api/expenses/scan")
                        .file(file)
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.type").value("RECEIPT_SCAN"));
    }

    @Test
    void scanReceipt_unauthenticated_returns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "receipt.jpg", MediaType.IMAGE_JPEG_VALUE, "fake-data".getBytes()
        );
        mockMvc.perform(multipart("/api/expenses/scan").file(file))
                .andExpect(status().isUnauthorized());
    }

    // ==================== PUT /api/expenses/{urlId} ====================

    @Test
    void updateExpense_success() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(put("/api/expenses/" + expense.getUrlId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "category", "Groceries",
                                "notes", "Weekly shop"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("Groceries"))
                .andExpect(jsonPath("$.notes").value("Weekly shop"));
    }

    @Test
    void updateExpense_invalidCurrency_returnsBadRequest() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(put("/api/expenses/" + expense.getUrlId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("currency", "FAKE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unsupported currency: FAKE"));
    }

    @Test
    void updateExpense_unauthenticated_returns401() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");

        mockMvc.perform(put("/api/expenses/" + expense.getUrlId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== DELETE /api/expenses/{urlId} ====================

    @Test
    void deleteExpense_success() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(delete("/api/expenses/" + expense.getUrlId()).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Deleted"));

        assertThat(expenseRepository.findByUrlId(expense.getUrlId())
                .map(Expense::isDeleted).orElse(false)).isTrue();
    }

    @Test
    void deleteExpense_unauthenticated_returns401() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");

        mockMvc.perform(delete("/api/expenses/" + expense.getUrlId()))
                .andExpect(status().isUnauthorized());
    }

    // ==================== PATCH /api/expenses/{urlId}/restore ====================

    @Test
    void restoreExpense_success() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        expense.setDeleted(true);
        expenseRepository.save(expense);
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(patch("/api/expenses/" + expense.getUrlId() + "/restore").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Restored"));

        assertThat(expenseRepository.findByUrlId(expense.getUrlId())
                .map(Expense::isDeleted).orElse(true)).isFalse();
    }

    @Test
    void restoreExpense_unauthenticated_returns401() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");

        mockMvc.perform(patch("/api/expenses/" + expense.getUrlId() + "/restore"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== POST /api/expenses/{urlId}/retry ====================

    @Test
    void retryOcr_success() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        expense.setType(ExpenseType.RECEIPT_SCAN);
        expense.setStatus(ExpenseStatus.FAILED);
        expense.setImagePath("target/test-data/receipts/test.jpg");
        expenseRepository.save(expense);
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(post("/api/expenses/" + expense.getUrlId() + "/retry").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Retry initiated"));
    }

    @Test
    void retryOcr_unauthenticated_returns401() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");

        mockMvc.perform(post("/api/expenses/" + expense.getUrlId() + "/retry"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== POST /api/expenses/{urlId}/duplicate ====================

    @Test
    void duplicateExpense_success() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        createTestItem(expense.getId(), "Apple", BigDecimal.ONE, BigDecimal.TEN);
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(post("/api/expenses/" + expense.getUrlId() + "/duplicate").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("Food"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        assertThat(expenseRepository.findAll()).hasSize(2);
    }

    @Test
    void duplicateExpense_unauthenticated_returns401() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");

        mockMvc.perform(post("/api/expenses/" + expense.getUrlId() + "/duplicate"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== POST /api/expenses/{urlId}/items ====================

    @Test
    void addItem_success() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        MockHttpSession session = loginAs("alice", "pass");

        Map<String, Object> item = Map.of(
                "itemName", "Bread",
                "quantity", 2,
                "unitPrice", 3.50,
                "totalPrice", 7.00
        );

        mockMvc.perform(post("/api/expenses/" + expense.getUrlId() + "/items")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(item)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemName").value("Bread"));
    }

    @Test
    void addItem_unauthenticated_returns401() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");

        mockMvc.perform(post("/api/expenses/" + expense.getUrlId() + "/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== PUT /api/expenses/{urlId}/items/{itemId} ====================

    @Test
    void updateItem_success() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        ExpenseItem item = createTestItem(expense.getId(), "Bread", BigDecimal.ONE, BigDecimal.TEN);
        MockHttpSession session = loginAs("alice", "pass");

        Map<String, Object> update = Map.of("itemName", "Whole Grain Bread", "quantity", 1, "unitPrice", 3.50);

        mockMvc.perform(put("/api/expenses/" + expense.getUrlId() + "/items/" + item.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemName").value("Whole Grain Bread"));
    }

    @Test
    void updateItem_unauthenticated_returns401() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        ExpenseItem item = createTestItem(expense.getId(), "Bread", BigDecimal.ONE, BigDecimal.TEN);

        mockMvc.perform(put("/api/expenses/" + expense.getUrlId() + "/items/" + item.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== DELETE /api/expenses/{urlId}/items/{itemId} ====================

    @Test
    void deleteItem_success() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        ExpenseItem item = createTestItem(expense.getId(), "Bread", BigDecimal.ONE, BigDecimal.TEN);
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(delete("/api/expenses/" + expense.getUrlId() + "/items/" + item.getId())
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Item deleted"));

        assertThat(expenseItemRepository.findById(item.getId())
                .map(ExpenseItem::isDeleted).orElse(false)).isTrue();
    }

    @Test
    void deleteItem_unauthenticated_returns401() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        ExpenseItem item = createTestItem(expense.getId(), "Bread", BigDecimal.ONE, BigDecimal.TEN);

        mockMvc.perform(delete("/api/expenses/" + expense.getUrlId() + "/items/" + item.getId()))
                .andExpect(status().isUnauthorized());
    }

    // ==================== PUT /api/expenses/{urlId}/store ====================

    @Test
    void updateStore_createsNewStore() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        MockHttpSession session = loginAs("alice", "pass");

        Map<String, Object> storeBody = Map.of(
                "name", "FairPrice",
                "city", "Singapore",
                "country", "SG"
        );

        mockMvc.perform(put("/api/expenses/" + expense.getUrlId() + "/store")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(storeBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("FairPrice"));

        Expense updated = expenseRepository.findByUrlId(expense.getUrlId()).orElseThrow();
        assertThat(updated.getStoreId()).isNotNull();
    }

    @Test
    void updateStore_reusesExistingStore() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense1 = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        Expense expense2 = createTestExpense(user.getId(), "Drink", BigDecimal.valueOf(5), "USD");
        MockHttpSession session = loginAs("alice", "pass");

        Map<String, Object> storeBody = Map.of(
                "name", "FairPrice",
                "address", "123 Main St",
                "city", "Singapore",
                "country", "SG",
                "postalCode", "123456"
        );

        // Create store for first expense
        mockMvc.perform(put("/api/expenses/" + expense1.getUrlId() + "/store")
                        .session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(storeBody)))
                .andExpect(status().isOk());

        // Second expense should reuse the same store
        mockMvc.perform(put("/api/expenses/" + expense2.getUrlId() + "/store")
                        .session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(storeBody)))
                .andExpect(status().isOk());

        assertThat(storeRepository.findAll()).hasSize(1);
    }

    @Test
    void updateStore_unauthenticated_returns401() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");

        mockMvc.perform(put("/api/expenses/" + expense.getUrlId() + "/store")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== POST /api/expenses/{urlId}/attachments ====================

    @Test
    void addAttachment_success() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        MockHttpSession session = loginAs("alice", "pass");

        MockMultipartFile attachment = new MockMultipartFile(
                "file", "receipt.pdf", MediaType.APPLICATION_PDF_VALUE, "pdf-data".getBytes()
        );

        mockMvc.perform(multipart("/api/expenses/" + expense.getUrlId() + "/attachments")
                        .file(attachment)
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").isString());
    }

    @Test
    void addAttachment_unauthenticated_returns401() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");

        MockMultipartFile attachment = new MockMultipartFile(
                "file", "receipt.pdf", MediaType.APPLICATION_PDF_VALUE, "pdf-data".getBytes()
        );
        mockMvc.perform(multipart("/api/expenses/" + expense.getUrlId() + "/attachments").file(attachment))
                .andExpect(status().isUnauthorized());
    }

    // ==================== DELETE /api/expenses/{urlId}/attachments/{filename} ====================

    @Test
    void removeAttachment_success() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(delete("/api/expenses/" + expense.getUrlId() + "/attachments/nonexistent.pdf")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Attachment removed"));
    }

    @Test
    void removeAttachment_unauthenticated_returns401() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");

        mockMvc.perform(delete("/api/expenses/" + expense.getUrlId() + "/attachments/test.pdf"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== GET /api/expenses/export ====================

    @Test
    void exportJson_success() throws Exception {
        User user = createTestUser("alice", "pass");
        createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/expenses/export?format=json").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void exportCsv_success() throws Exception {
        User user = createTestUser("alice", "pass");
        createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/expenses/export?format=csv").session(session))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("expenses.csv")));
    }

    @Test
    void exportCsv_withSearch_filtersResults() throws Exception {
        User user = createTestUser("alice", "pass");
        createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        createTestExpense(user.getId(), "Transport", BigDecimal.TEN, "USD");
        MockHttpSession session = loginAs("alice", "pass");

        String result = mockMvc.perform(get("/api/expenses/export?format=csv&search=Food").session(session))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(result).contains("Food")
            .doesNotContain("Transport");
    }

    @Test
    void exportExpenses_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/expenses/export"))
                .andExpect(status().isUnauthorized());
    }

    // ==================== Store enrichment in list response ====================

    @Test
    void listExpenses_enrichedWithStoreInfo() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        Store store = createTestStore(user.getId(), "FairPrice", "Singapore", "SG");
        linkExpenseToStore(expense, store);
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/expenses").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].storeName").value("FairPrice"))
                .andExpect(jsonPath("$[0].country").value("SG"))
                .andExpect(jsonPath("$[0].displayName").value("Food — FairPrice"));
    }

    @Test
    void listExpenses_searchMatchesItemName() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Groceries", BigDecimal.TEN, "USD");
        createTestItem(expense.getId(), "Organic Oats", BigDecimal.ONE, BigDecimal.TEN);
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/expenses?search=oats").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].matchingItems.length()").value(1))
                .andExpect(jsonPath("$[0].matchingItems[0].itemName").value("Organic Oats"));
    }

    @Test
    void updateExpense_clearsNullCurrency() throws Exception {
        User user = createTestUser("alice", "pass");
        Expense expense = createTestExpense(user.getId(), "Food", BigDecimal.TEN, "USD");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(put("/api/expenses/" + expense.getUrlId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("currency", ""))))
                .andExpect(status().isOk());
    }
}


