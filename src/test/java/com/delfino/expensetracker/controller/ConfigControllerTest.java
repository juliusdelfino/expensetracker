package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.BaseControllerTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConfigControllerTest extends BaseControllerTest {

    @Test
    void config_exposesPublicFrontendSharingDefaults() throws Exception {
        mockMvc.perform(get("/api/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoDevToken").exists())
                .andExpect(jsonPath("$.sharingDefaultTtlDays").value(30));
    }
}

