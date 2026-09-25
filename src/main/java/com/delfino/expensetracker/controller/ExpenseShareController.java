package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.dto.auth.UserToken;
import com.delfino.expensetracker.dto.common.ErrorResponse;
import com.delfino.expensetracker.dto.share.ExpenseShareStatusResponse;
import com.delfino.expensetracker.model.ExpenseShare;
import com.delfino.expensetracker.service.ExpenseShareService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
public class ExpenseShareController {

    private static final String TTL_DAYS_FIELD = "ttlDays";

    private final ExpenseShareService expenseShareService;

    public ExpenseShareController(ExpenseShareService expenseShareService) {
        this.expenseShareService = expenseShareService;
    }

    @PostMapping("/api/expenses/{urlId}/share")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ExpenseShareStatusResponse> createShare(@PathVariable String urlId,
                                                                  @RequestBody(required = false) Map<String, Object> body,
                                                                  UserToken userToken) {
        Duration ttl = parseTtl(body);
        ExpenseShare share = expenseShareService.createShare(urlId, userToken.getUserId(), ttl);
        return ResponseEntity.ok(toResponse(share));
    }

    @GetMapping("/api/expenses/{urlId}/share")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ExpenseShareStatusResponse> getShare(@PathVariable String urlId, UserToken userToken) {
        return ResponseEntity.ok(expenseShareService.getShareStatus(urlId, userToken.getUserId())
                .map(this::toResponse)
                .orElseGet(() -> expenseShareService.findLatestShareForExpense(urlId, userToken.getUserId())
                        .map(share -> new ExpenseShareStatusResponse(false, null, null, share.getExpiresAt(), share.getRevokedAt()))
                        .orElse(new ExpenseShareStatusResponse(false, null, null, null, null))));
    }

    @DeleteMapping("/api/expenses/{urlId}/share")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ExpenseShareStatusResponse> revokeShare(@PathVariable String urlId, UserToken userToken) {
        return ResponseEntity.ok(expenseShareService.revokeShare(urlId, userToken.getUserId())
                .map(share -> new ExpenseShareStatusResponse(false, null, null, share.getExpiresAt(), share.getRevokedAt()))
                .orElse(new ExpenseShareStatusResponse(false, null, null, null, null)));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException e) {
        return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
    }

    private ExpenseShareStatusResponse toResponse(ExpenseShare share) {
        return new ExpenseShareStatusResponse(
                true,
                share.getShareToken(),
                "/view/share/" + share.getShareToken(),
                share.getExpiresAt(),
                share.getRevokedAt());
    }

    private Duration parseTtl(Map<String, Object> body) {
        if (body == null || !body.containsKey(TTL_DAYS_FIELD) || body.get(TTL_DAYS_FIELD) == null) {
            return null;
        }
        Object ttlValue = body.get(TTL_DAYS_FIELD);
        if (ttlValue instanceof Number number) {
            return Duration.ofDays(number.longValue());
        }
        return null;
    }
}

