package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.BaseControllerTest;
import com.delfino.expensetracker.config.AiModelDefinition;
import com.delfino.expensetracker.config.AiProperties;
import com.delfino.expensetracker.config.AiProviderType;
import com.delfino.expensetracker.model.Expense;
import com.delfino.expensetracker.model.Store;
import com.delfino.expensetracker.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserControllerTest extends BaseControllerTest {

    @Autowired
    private AiProperties aiProperties;

    @Test
    void updateProfile_email_success() throws Exception {
        createTestUser("alice", "pass");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(put("/api/user/profile")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "new@email.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Profile updated"));

        User updated = userRepository.findByUsernameIgnoreCase("alice").orElseThrow();
        assertThat(updated.getEmail()).isEqualTo("new@email.com");
    }

    @Test
    void updateProfile_phoneNumber_success() throws Exception {
        createTestUser("alice", "pass");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(put("/api/user/profile")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("phoneNumber", "+1234567890"))))
                .andExpect(status().isOk());

        User updated = userRepository.findByUsernameIgnoreCase("alice").orElseThrow();
        assertThat(updated.getPhoneNumber()).isEqualTo("+1234567890");
    }

    @Test
    void updateProfile_validBaseCurrency_success() throws Exception {
        createTestUser("alice", "pass");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(put("/api/user/profile")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("baseCurrency", "EUR"))))
                .andExpect(status().isOk());

        User updated = userRepository.findByUsernameIgnoreCase("alice").orElseThrow();
        assertThat(updated.getBaseCurrency()).isEqualTo("EUR");
    }

    @Test
    void updateProfile_invalidBaseCurrency_returnsBadRequest() throws Exception {
        createTestUser("alice", "pass");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(put("/api/user/profile")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("baseCurrency", "XYZ"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unsupported base currency: XYZ"));
    }

    @Test
    void updateProfile_baseCity_success() throws Exception {
        createTestUser("alice", "pass");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(put("/api/user/profile")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "baseCity", "Singapore",
                                "baseCountry", "SG"
                        ))))
                .andExpect(status().isOk());

        User updated = userRepository.findByUsernameIgnoreCase("alice").orElseThrow();
        assertThat(updated.getBaseCity()).isEqualTo("Singapore");
        assertThat(updated.getBaseCountry()).isEqualTo("SG");
    }

    @Test
    void updateProfile_aiModel_success() throws Exception {
        createTestUser("alice", "pass");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(put("/api/user/profile")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("aiModel", "gemma4:latest"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Profile updated"));

        User updated = userRepository.findByUsernameIgnoreCase("alice").orElseThrow();
        assertThat(updated.getAiModel()).isEqualTo("gemma4:latest");
    }

    @Test
    void updateProfile_blankAiModel_clearsOverride() throws Exception {
        createTestUser("alice", "pass", "USD", com.delfino.expensetracker.model.UserRole.USER, "gpt-4.1-mini");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(put("/api/user/profile")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("aiModel", "   "))))
                .andExpect(status().isOk());

        User updated = userRepository.findByUsernameIgnoreCase("alice").orElseThrow();
        assertThat(updated.getAiModel()).isNull();
    }

    @Test
    void updateProfile_invalidAiModel_returnsBadRequest() throws Exception {
        createTestUser("alice", "pass");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(put("/api/user/profile")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("aiModel", "unknown-model"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unsupported AI model: unknown-model"))
                .andExpect(jsonPath("$.code").value("AI_MODEL_NOT_ALLOWED"));
    }

    @Test
    void updateProfile_rejectsAiModelThatDoesNotSupportBothChatAndOcr() throws Exception {
        aiProperties.getModels().add(model("chat-only", true, false));
        createTestUser("alice", "pass");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(put("/api/user/profile")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("aiModel", "chat-only"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Selected AI model must support both chat and OCR: chat-only"))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void getAiModels_returnsConfiguredCatalog() throws Exception {
        createTestUser("alice", "pass");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/user/ai/models")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultChatModel").value("gemma4:latest"))
                .andExpect(jsonPath("$.defaultOcrModel").value("gemma4:latest"))
                .andExpect(jsonPath("$.models.length()").value(2))
                .andExpect(jsonPath("$.models[0].id").isString())
                .andExpect(jsonPath("$.models[0].provider").isString());
    }

    @Test
    void getAiModels_excludesModelsThatCannotBeUsedAsAccountOverride() throws Exception {
        aiProperties.getModels().add(model("chat-only", true, false));
        createTestUser("alice", "pass");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/user/ai/models")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.models.length()").value(2));
    }

    @Test
    void getAiStatus_returnsCurrentUsageAndEffectiveModels() throws Exception {
        User user = createTestUser("alice", "pass", "USD", com.delfino.expensetracker.model.UserRole.USER, "qwen3.5-397b-a17b");
        aiUsageService.consume(user.getId(), com.delfino.expensetracker.model.AiUsageType.CHAT);
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/user/ai/status").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedAiModel").value("qwen3.5-397b-a17b"))
                .andExpect(jsonPath("$.effectiveChatModel").value("qwen3.5-397b-a17b"))
                .andExpect(jsonPath("$.effectiveOcrModel").value("qwen3.5-397b-a17b"))
                .andExpect(jsonPath("$.chat.usageCount").value(1))
                .andExpect(jsonPath("$.chat.quota").value(2))
                .andExpect(jsonPath("$.chat.remaining").value(1))
                .andExpect(jsonPath("$.ocr.usageCount").value(0))
                .andExpect(jsonPath("$.ocr.quota").value(2))
                .andExpect(jsonPath("$.chatAllowed").value(true))
                .andExpect(jsonPath("$.ocrAllowed").value(true));
    }

    @Test
    void updateProfile_password_success() throws Exception {
        createTestUser("alice", "oldpass");
        MockHttpSession session = loginAs("alice", "oldpass");

        mockMvc.perform(put("/api/user/profile")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("password", "newpass"))))
                .andExpect(status().isOk());

        // Verify new password is encoded and works
        User updated = userRepository.findByUsernameIgnoreCase("alice").orElseThrow();
        assertThat(passwordEncoder.matches("newpass", updated.getPasswordHash())).isTrue();
    }

    @Test
    void updateProfile_blankPassword_notUpdated() throws Exception {
        createTestUser("alice", "originalpass");
        MockHttpSession session = loginAs("alice", "originalpass");

        mockMvc.perform(put("/api/user/profile")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("password", ""))))
                .andExpect(status().isOk());

        // Original password should still work
        User updated = userRepository.findByUsernameIgnoreCase("alice").orElseThrow();
        assertThat(passwordEncoder.matches("originalpass", updated.getPasswordHash())).isTrue();
    }

    @Test
    void updateProfile_unauthenticated_returns401() throws Exception {
        mockMvc.perform(put("/api/user/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "test@test.com"))))
                .andExpect(status().isUnauthorized());
    }

    private AiModelDefinition model(String id, boolean supportsChat, boolean supportsOcr) {
        AiModelDefinition model = new AiModelDefinition();
        model.setId(id);
        model.setLabel(id);
        model.setProvider(AiProviderType.OLLAMA);
        model.setSupportsChat(supportsChat);
        model.setSupportsOcr(supportsOcr);
        return model;
    }

    // -------------------------------------------------------------------------
    // Account Summary
    // -------------------------------------------------------------------------

    @Test
    void getAccountSummary_returnsZeroWhenNoTrash() throws Exception {
        createTestUser("alice", "pass");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/user/account/summary").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trashedExpenseCount").value(0));
    }

    @Test
    void getAccountSummary_countsOnlyDeletedExpenses() throws Exception {
        User alice = createTestUser("alice", "pass");
        createTestExpense(alice.getId(), "Food", BigDecimal.TEN, "USD");
        Expense trashed = createTestExpense(alice.getId(), "Travel", BigDecimal.TEN, "USD");
        trashed.setDeleted(true);
        expenseRepository.save(trashed);
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/user/account/summary").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trashedExpenseCount").value(1));
    }

    // -------------------------------------------------------------------------
    // Trash: list
    // -------------------------------------------------------------------------

    @Test
    void listTrash_returnsOnlyDeletedExpensesForCurrentUser() throws Exception {
        User alice = createTestUser("alice", "pass");
        User bob = createTestUser("bob", "pass");
        createTestExpense(alice.getId(), "Food", BigDecimal.TEN, "USD"); // active, should not appear
        Expense trashed = createTestExpense(alice.getId(), "Travel", BigDecimal.valueOf(50), "USD");
        trashed.setDeleted(true);
        expenseRepository.save(trashed);
        // Bob's trashed expense should not appear for alice
        Expense bobTrashed = createTestExpense(bob.getId(), "Other", BigDecimal.ONE, "USD");
        bobTrashed.setDeleted(true);
        expenseRepository.save(bobTrashed);

        MockHttpSession session = loginAs("alice", "pass");
        mockMvc.perform(get("/api/user/trash/expenses").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].category").value("Travel"));
    }

    @Test
    void listTrash_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/user/trash/expenses"))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // Trash: restore
    // -------------------------------------------------------------------------

    @Test
    void restoreFromTrash_success() throws Exception {
        User alice = createTestUser("alice", "pass");
        Expense trashed = createTestExpense(alice.getId(), "Food", BigDecimal.TEN, "USD");
        trashed.setDeleted(true);
        trashed = expenseRepository.save(trashed);
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(post("/api/user/trash/expenses/" + trashed.getUrlId() + "/restore").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Expense restored"));

        assertThat(expenseRepository.findByUrlId(trashed.getUrlId()).orElseThrow().isDeleted()).isFalse();
    }

    @Test
    void restoreFromTrash_activeExpense_returnsBadRequest() throws Exception {
        User alice = createTestUser("alice", "pass");
        Expense active = createTestExpense(alice.getId(), "Food", BigDecimal.TEN, "USD");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(post("/api/user/trash/expenses/" + active.getUrlId() + "/restore").session(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    void restoreFromTrash_otherUserExpense_returns403() throws Exception {
        User alice = createTestUser("alice", "pass");
        User bob = createTestUser("bob", "pass");
        Expense bobTrashed = createTestExpense(bob.getId(), "Food", BigDecimal.TEN, "USD");
        bobTrashed.setDeleted(true);
        bobTrashed = expenseRepository.save(bobTrashed);
        MockHttpSession aliceSession = loginAs("alice", "pass");

        mockMvc.perform(post("/api/user/trash/expenses/" + bobTrashed.getUrlId() + "/restore").session(aliceSession))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // Trash: purge one
    // -------------------------------------------------------------------------

    @Test
    void purgeOne_success() throws Exception {
        User alice = createTestUser("alice", "pass");
        Expense trashed = createTestExpense(alice.getId(), "Food", BigDecimal.TEN, "USD");
        trashed.setDeleted(true);
        trashed = expenseRepository.save(trashed);
        String urlId = trashed.getUrlId();
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(delete("/api/user/trash/expenses/" + urlId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Expense permanently deleted"));

        assertThat(expenseRepository.findByUrlId(urlId)).isEmpty();
    }

    @Test
    void purgeOne_activeExpense_returnsBadRequest() throws Exception {
        User alice = createTestUser("alice", "pass");
        Expense active = createTestExpense(alice.getId(), "Food", BigDecimal.TEN, "USD");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(delete("/api/user/trash/expenses/" + active.getUrlId()).session(session))
                .andExpect(status().isBadRequest());
    }

    @Test
    void purgeOne_otherUser_returns403() throws Exception {
        User alice = createTestUser("alice", "pass");
        User bob = createTestUser("bob", "pass");
        Expense bobTrashed = createTestExpense(bob.getId(), "Food", BigDecimal.TEN, "USD");
        bobTrashed.setDeleted(true);
        bobTrashed = expenseRepository.save(bobTrashed);
        MockHttpSession aliceSession = loginAs("alice", "pass");

        mockMvc.perform(delete("/api/user/trash/expenses/" + bobTrashed.getUrlId()).session(aliceSession))
                .andExpect(status().isForbidden());
    }

    // -------------------------------------------------------------------------
    // Trash: bulk purge
    // -------------------------------------------------------------------------

    @Test
    void bulkPurge_all_deletesAllTrashedExpenses() throws Exception {
        User alice = createTestUser("alice", "pass");
        Expense t1 = createTestExpense(alice.getId(), "Food", BigDecimal.TEN, "USD");
        t1.setDeleted(true); expenseRepository.save(t1);
        Expense t2 = createTestExpense(alice.getId(), "Travel", BigDecimal.TEN, "USD");
        t2.setDeleted(true); expenseRepository.save(t2);
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(post("/api/user/trash/expenses/purge")
                        .session(session).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(2));

        assertThat(expenseRepository.findByUserIdAndDeletedTrue(alice.getId())).isEmpty();
    }

    @Test
    void bulkPurge_specific_deletesOnlySelectedExpenses() throws Exception {
        User alice = createTestUser("alice", "pass");
        Expense t1 = createTestExpense(alice.getId(), "Food", BigDecimal.TEN, "USD");
        t1.setDeleted(true); t1 = expenseRepository.save(t1);
        Expense t2 = createTestExpense(alice.getId(), "Travel", BigDecimal.TEN, "USD");
        t2.setDeleted(true); expenseRepository.save(t2);
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(post("/api/user/trash/expenses/purge")
                        .session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("expenseIds", java.util.List.of(t1.getUrlId())))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(1));

        assertThat(expenseRepository.findByUserIdAndDeletedTrue(alice.getId())).hasSize(1);
    }

    // -------------------------------------------------------------------------
    // Export
    // -------------------------------------------------------------------------

    @Test
    void exportAccount_returnsZipWithExpectedContentType() throws Exception {
        User alice = createTestUser("alice", "pass");
        createTestExpense(alice.getId(), "Food", BigDecimal.TEN, "USD");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/user/export").session(session))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("application/zip")))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("account-export-")));
    }

    @Test
    void exportAccount_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/user/export"))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // Account Deletion
    // -------------------------------------------------------------------------

    @Test
    void deleteAccount_success_removesUserAndExpenses() throws Exception {
        User alice = createTestUser("alice", "pass");
        createTestExpense(alice.getId(), "Food", BigDecimal.TEN, "USD");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(post("/api/user/delete-account")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("password", "pass", "confirmation", "DELETE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Account permanently deleted"));

        assertThat(userRepository.findByUsernameIgnoreCase("alice")).isEmpty();
        assertThat(expenseRepository.findByUserId(alice.getId())).isEmpty();
    }

    @Test
    void deleteAccount_wrongPassword_returnsBadRequest() throws Exception {
        createTestUser("alice", "pass");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(post("/api/user/delete-account")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("password", "wrong", "confirmation", "DELETE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Incorrect password"));
    }

    @Test
    void deleteAccount_wrongConfirmation_returnsBadRequest() throws Exception {
        createTestUser("alice", "pass");
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(post("/api/user/delete-account")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("password", "pass", "confirmation", "delete"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Confirmation phrase must be exactly: DELETE"));
    }

    @Test
    void deleteAccount_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/user/delete-account")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("password", "pass", "confirmation", "DELETE"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteAccount_alsoDeletesStoresAndExpenseItems() throws Exception {
        User alice = createTestUser("alice", "pass");
        Store store = createTestStore(alice.getId(), "TestShop", "Singapore", "SG");
        Expense expense = createTestExpense(alice.getId(), "Food", BigDecimal.TEN, "USD");
        linkExpenseToStore(expense, store);
        createTestItem(expense.getId(), "Apple", BigDecimal.ONE, BigDecimal.valueOf(2));
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(post("/api/user/delete-account")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("password", "pass", "confirmation", "DELETE"))))
                .andExpect(status().isOk());

        assertThat(userRepository.findByUsernameIgnoreCase("alice")).isEmpty();
        assertThat(storeRepository.findByUserId(alice.getId())).isEmpty();
        assertThat(expenseItemRepository.findByExpenseIdAndDeletedFalse(expense.getId())).isEmpty();
    }
}

