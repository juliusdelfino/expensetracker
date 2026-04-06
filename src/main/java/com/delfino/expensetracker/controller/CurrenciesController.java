package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.service.SupportedCurrencyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CurrenciesController {

    private final SupportedCurrencyService supportedCurrencyService;

    public CurrenciesController(SupportedCurrencyService supportedCurrencyService) {
        this.supportedCurrencyService = supportedCurrencyService;
    }

    @GetMapping("/currencies")
    public ResponseEntity<?> currencies() {
        return ResponseEntity.ok(supportedCurrencyService.getMap());
    }
}

