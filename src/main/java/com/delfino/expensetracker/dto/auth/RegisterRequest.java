package com.delfino.expensetracker.dto.auth;

public record RegisterRequest(
        String username,
        String password,
        String email,
        String phoneNumber,
        String baseCurrency) {}
