package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.dto.auth.UserToken;
import com.delfino.expensetracker.model.Store;
import com.delfino.expensetracker.repository.StoreRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stores")
public class StoreController {

    private final StoreRepository storeRepository;

    public StoreController(StoreRepository storeRepository) {
        this.storeRepository = storeRepository;
    }

    /**
     * Search the current user's stores by name (case-insensitive, contains).
     * Returns up to 8 results.
     */
    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Store>> search(@RequestParam(required = false, defaultValue = "") String q,
                                              UserToken userToken) {
        long userId = userToken.getUserId();
        String query = q.trim().toLowerCase();
        List<Store> results = storeRepository.findByUserId(userId).stream()
                .filter(s -> s.getName() != null && (query.isEmpty() || s.getName().toLowerCase().contains(query)))
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .limit(8)
                .toList();
        return ResponseEntity.ok(results);
    }
}

