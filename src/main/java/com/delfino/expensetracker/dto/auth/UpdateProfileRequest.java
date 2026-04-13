package com.delfino.expensetracker.dto.auth;

public record UpdateProfileRequest(
        String email,
        String phoneNumber,
        String baseCurrency,
        String baseCity,
        String baseCountry,
        String password) {}
