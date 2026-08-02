package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.BaseControllerTest;
import com.delfino.expensetracker.model.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(SecurityRolePropagationTest.AdminOnlyEndpointConfig.class)
class SecurityRolePropagationTest extends BaseControllerTest {

    @Test
    void adminSession_canAccessAdminOnlyEndpoint() throws Exception {
        createTestUser("admin", "pass", "USD", UserRole.ADMIN, null);
        MockHttpSession session = loginAs("admin", "pass");

        mockMvc.perform(get("/api/test/admin-only").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string("admin-ok"));
    }

    @Test
    void regularUserSession_cannotAccessAdminOnlyEndpoint() throws Exception {
        createTestUser("alice", "pass", "USD", UserRole.USER, null);
        MockHttpSession session = loginAs("alice", "pass");

        mockMvc.perform(get("/api/test/admin-only").session(session))
                .andExpect(status().isForbidden());
    }

    @TestConfiguration
    static class AdminOnlyEndpointConfig {
        @RestController
        static class AdminOnlyTestController {
            @GetMapping("/api/test/admin-only")
            @PreAuthorize("hasRole('ADMIN')")
            ResponseEntity<String> adminOnly() {
                return ResponseEntity.ok("admin-ok");
            }
        }
    }
}

