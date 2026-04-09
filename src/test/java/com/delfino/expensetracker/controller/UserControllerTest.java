package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.BaseControllerTest;
import com.delfino.expensetracker.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserControllerTest extends BaseControllerTest {

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
}

