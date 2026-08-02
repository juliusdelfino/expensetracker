package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.BaseControllerTest;
import com.delfino.expensetracker.model.AiUsageType;
import com.delfino.expensetracker.model.User;
import com.delfino.expensetracker.model.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminControllerTest extends BaseControllerTest {

    @Test
    void adminEndpoints_requireAdminRole() throws Exception {
        createTestUser("alice", "pass", "USD", UserRole.USER, null);
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/admin/users").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Access denied"))
                .andExpect(jsonPath("$.code").value("ADMIN_ONLY"));
    }

    @Test
    void getUsers_andOverview_returnUsageSummaries() throws Exception {
        createTestUser("admin", "pass", "USD", UserRole.ADMIN, null);
        User alice = createTestUser("alice", "pass", "USD", UserRole.USER, "qwen3.5-397b-a17b");
        User bob = createTestUser("bob", "pass", "USD", UserRole.ADMIN, null);
        aiUsageService.consume(alice.getId(), AiUsageType.CHAT);
        aiUsageService.consume(bob.getId(), AiUsageType.OCR);
        aiUsageService.consume(bob.getId(), AiUsageType.OCR);

        MockHttpSession session = loginAs("admin", "pass");

        mockMvc.perform(get("/api/admin/users").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthYear").isString())
                .andExpect(jsonPath("$.users.length()").value(3))
                .andExpect(jsonPath("$.users[0].username").value("admin"))
                .andExpect(jsonPath("$.users[1].username").value("alice"))
                .andExpect(jsonPath("$.users[1].role").value("USER"))
                .andExpect(jsonPath("$.users[1].effectiveChatProvider").value("OPENAI"))
                .andExpect(jsonPath("$.users[1].chat.usageCount").value(1))
                .andExpect(jsonPath("$.users[2].username").value("bob"))
                .andExpect(jsonPath("$.users[2].role").value("ADMIN"))
                .andExpect(jsonPath("$.users[2].ocr.usageCount").value(2))
                .andExpect(jsonPath("$.users[2].ocrAllowed").value(false));

        mockMvc.perform(get("/api/admin/ai/usage").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(3))
                .andExpect(jsonPath("$.adminUsers").value(2))
                .andExpect(jsonPath("$.totalChatUsage").value(1))
                .andExpect(jsonPath("$.totalOcrUsage").value(2))
                .andExpect(jsonPath("$.chatByModel.length()").value(2))
                .andExpect(jsonPath("$.ocrByModel.length()").value(2))
                .andExpect(jsonPath("$.ocrExceededUsers").value(1));
    }

    @Test
    void updateRole_promotesUserToAdmin() throws Exception {
        createTestUser("admin", "pass", "USD", UserRole.ADMIN, null);
        User alice = createTestUser("alice", "pass", "USD", UserRole.USER, null);
        MockHttpSession session = loginAs("admin", "pass");

        mockMvc.perform(patch("/api/admin/users/{id}/role", alice.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        assertThat(userRepository.findById(alice.getId())).get().extracting(User::getRole).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void updateRole_rejectsRemovingLastAdmin() throws Exception {
        User admin = createTestUser("admin", "pass", "USD", UserRole.ADMIN, null);
        MockHttpSession session = loginAs("admin", "pass");

        mockMvc.perform(patch("/api/admin/users/{id}/role", admin.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "USER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("You cannot remove the last admin account"))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        assertThat(userRepository.findById(admin.getId())).get().extracting(User::getRole).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void updateAiModel_updatesAndResetsOverride() throws Exception {
        createTestUser("admin", "pass", "USD", UserRole.ADMIN, null);
        User alice = createTestUser("alice", "pass", "USD", UserRole.USER, null);
        MockHttpSession session = loginAs("admin", "pass");

        mockMvc.perform(patch("/api/admin/users/{id}/ai-model", alice.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("aiModel", "qwen3.5-397b-a17b"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedAiModel").value("qwen3.5-397b-a17b"))
                .andExpect(jsonPath("$.effectiveChatProvider").value("OPENAI"));

        mockMvc.perform(patch("/api/admin/users/{id}/ai-model", alice.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("aiModel", "   "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectiveChatModel").value("gemma4:latest"));

        assertThat(userRepository.findById(alice.getId())).get().extracting(User::getAiModel).isNull();
    }

    @Test
    void updateAiModel_invalidOverride_returnsAiModelNotAllowedCode() throws Exception {
        createTestUser("admin", "pass", "USD", UserRole.ADMIN, null);
        User alice = createTestUser("alice", "pass", "USD", UserRole.USER, null);
        MockHttpSession session = loginAs("admin", "pass");

        mockMvc.perform(patch("/api/admin/users/{id}/ai-model", alice.getId())
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("aiModel", "bad-model"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AI_MODEL_NOT_ALLOWED"));
    }

    @Test
    void getUserUsage_returns404WhenUserMissing() throws Exception {
        createTestUser("admin", "pass", "USD", UserRole.ADMIN, null);
        MockHttpSession session = loginAs("admin", "pass");

        mockMvc.perform(get("/api/admin/ai/usage/users/999999").session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("User not found: 999999"))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void getUsageOverview_invalidMonth_returnsBadRequestWithCode() throws Exception {
        createTestUser("admin", "pass", "USD", UserRole.ADMIN, null);
        MockHttpSession session = loginAs("admin", "pass");

        mockMvc.perform(get("/api/admin/ai/usage").param("month", "2026/05").session(session))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid month format. Use yyyy-MM"))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}




