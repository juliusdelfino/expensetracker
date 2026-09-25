package com.delfino.expensetracker.dto.user;

public record DeleteAccountRequest(
        String password,
        String confirmation   // must equal "DELETE"
) {}

