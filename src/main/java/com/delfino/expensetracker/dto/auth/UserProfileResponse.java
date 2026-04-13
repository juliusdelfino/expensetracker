package com.delfino.expensetracker.dto.auth;

public record UserProfileResponse(
        Long id,
        String username,
        String email,
        String phoneNumber,
        String baseCurrency,
        String baseCity,
        String baseCountry) {}
