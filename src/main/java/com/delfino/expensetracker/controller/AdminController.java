package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.dto.admin.AdminAiUsageOverviewResponse;
import com.delfino.expensetracker.dto.admin.AdminUserSummaryResponse;
import com.delfino.expensetracker.dto.admin.AdminUsersResponse;
import com.delfino.expensetracker.dto.admin.UpdateUserAiModelRequest;
import com.delfino.expensetracker.dto.admin.UpdateUserRoleRequest;
import com.delfino.expensetracker.dto.auth.UserToken;
import com.delfino.expensetracker.service.AdminUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminUserService adminUserService;

    public AdminController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping("/users")
    public ResponseEntity<AdminUsersResponse> getUsers(@RequestParam(required = false) String month) {
        return ResponseEntity.ok(adminUserService.getUsers(parseMonth(month)));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<AdminUserSummaryResponse> getUser(@PathVariable long id,
                                                            @RequestParam(required = false) String month) {
        return ResponseEntity.ok(adminUserService.getUser(id, parseMonth(month)));
    }

    @PatchMapping("/users/{id}/role")
    public ResponseEntity<AdminUserSummaryResponse> updateRole(@PathVariable long id,
                                                               @RequestBody UpdateUserRoleRequest body,
                                                               UserToken userToken) {
        return ResponseEntity.ok(adminUserService.updateUserRole(id, body.role(), userToken.getUserId()));
    }

    @PatchMapping("/users/{id}/ai-model")
    public ResponseEntity<AdminUserSummaryResponse> updateAiModel(@PathVariable long id,
                                                                  @RequestBody UpdateUserAiModelRequest body,
                                                                  UserToken userToken) {
        return ResponseEntity.ok(adminUserService.updateUserAiModel(id, body.aiModel(), userToken.getUserId()));
    }

    @GetMapping("/ai/usage")
    public ResponseEntity<AdminAiUsageOverviewResponse> getUsageOverview(@RequestParam(required = false) String month) {
        return ResponseEntity.ok(adminUserService.getUsageOverview(parseMonth(month)));
    }

    @GetMapping("/ai/usage/users/{id}")
    public ResponseEntity<AdminUserSummaryResponse> getUserUsage(@PathVariable long id,
                                                                 @RequestParam(required = false) String month) {
        return ResponseEntity.ok(adminUserService.getUser(id, parseMonth(month)));
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid month format. Use yyyy-MM");
        }
    }
}


