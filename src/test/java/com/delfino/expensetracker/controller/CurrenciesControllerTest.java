package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.BaseControllerTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CurrenciesControllerTest extends BaseControllerTest {

    @Test
    void currencies_returnsMap() throws Exception {
        mockMvc.perform(get("/api/currencies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.USD").value("United States Dollar"))
                .andExpect(jsonPath("$.EUR").value("Euro"))
                .andExpect(jsonPath("$.GBP").value("British Pound"))
                .andExpect(jsonPath("$.SGD").value("Singapore Dollar"));
    }

    @Test
    void currencies_noAuthRequired() throws Exception {
        // Public endpoint - should not require authentication
        mockMvc.perform(get("/api/currencies"))
                .andExpect(status().isOk());
    }
}

