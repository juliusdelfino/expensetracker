package com.delfino.expensetracker.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Exposes non-sensitive application configuration to the frontend.
 * No authentication required — only public/non-secret values should be added here.
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    @Value("${logo-dev.token:}")
    private String logoDevToken;

    @GetMapping
    public ResponseEntity<Map<String, Object>> config() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("logoDevToken", logoDevToken);
        return ResponseEntity.ok(cfg);
    }
}

