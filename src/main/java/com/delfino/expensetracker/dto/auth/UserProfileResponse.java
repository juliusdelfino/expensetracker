package com.delfino.expensetracker.dto.auth;

import com.delfino.expensetracker.model.UserRole;

public record UserProfileResponse(
        Long id,
        String username,
        String email,
        String phoneNumber,
        String baseCurrency,
        String baseCity,
        String baseCountry,
        String role,
        String aiModel) {

    public UserProfileResponse(
            Long id,
            String username,
            String email,
            String phoneNumber,
            String baseCurrency,
            String baseCity,
            String baseCountry,
            UserRole role,
            String aiModel) {
        this(id, username, email, phoneNumber, baseCurrency, baseCity, baseCountry,
                role != null ? role.name() : null, aiModel);
    }
}
