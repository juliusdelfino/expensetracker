package com.delfino.expensetracker.dto.auth;

public record LoginResponse(String message, Long userId, String username, String baseCurrency) {}
