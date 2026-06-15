package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.dto.auth.UserToken;
import com.delfino.expensetracker.dto.common.ErrorResponse;
import com.delfino.expensetracker.dto.common.MessageResponse;
import com.delfino.expensetracker.dto.user.BulkPurgeRequest;
import com.delfino.expensetracker.dto.user.DeleteAccountRequest;
import com.delfino.expensetracker.dto.user.TrashExpenseResponse;
import com.delfino.expensetracker.service.UserDeletionService;
import com.delfino.expensetracker.service.UserExportService;
import com.delfino.expensetracker.service.UserTrashService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserDataController {

    private final UserTrashService userTrashService;
    private final UserExportService userExportService;
    private final UserDeletionService userDeletionService;

    public UserDataController(UserTrashService userTrashService,
                              UserExportService userExportService,
                              UserDeletionService userDeletionService) {
        this.userTrashService = userTrashService;
        this.userExportService = userExportService;
        this.userDeletionService = userDeletionService;
    }

    @GetMapping("/trash/expenses")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<TrashExpenseResponse>> listTrash(UserToken userToken) {
        List<TrashExpenseResponse> result = userTrashService.listTrashed(userToken.getUserId())
                .stream().map(TrashExpenseResponse::from).toList();
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/trash/expenses/{expenseUrlId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Object> purgeOne(@PathVariable String expenseUrlId, UserToken userToken) {
        try {
            userTrashService.purgeOne(expenseUrlId, userToken.getUserId());
            return ResponseEntity.ok(new MessageResponse("Expense permanently deleted"));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (AuthorizationDeniedException e) {
            return ResponseEntity.status(403).body(new ErrorResponse("Access denied"));
        }
    }

    @PostMapping("/trash/expenses/purge")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Object> bulkPurge(@RequestBody(required = false) BulkPurgeRequest body,
                                            UserToken userToken) {
        try {
            List<String> ids = body != null ? body.expenseIds() : null;
            int count = userTrashService.purgeAll(userToken.getUserId(), ids);
            return ResponseEntity.ok(Map.of("deleted", count, "message", count + " expense(s) permanently deleted"));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (AuthorizationDeniedException e) {
            return ResponseEntity.status(403).body(new ErrorResponse("Access denied"));
        }
    }

    @PostMapping("/trash/expenses/{expenseUrlId}/restore")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Object> restoreFromTrash(@PathVariable String expenseUrlId, UserToken userToken) {
        try {
            userTrashService.restore(expenseUrlId, userToken.getUserId());
            return ResponseEntity.ok(new MessageResponse("Expense restored"));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (AuthorizationDeniedException e) {
            return ResponseEntity.status(403).body(new ErrorResponse("Access denied"));
        }
    }

    @GetMapping("/export")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> exportAccount(UserToken userToken) {
        try {
            byte[] zip = userExportService.buildExportZip(userToken.getUserId());
            String filename = "account-export-" + LocalDate.now() + ".zip";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.valueOf("application/zip"))
                    .body(zip);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/delete-account")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Object> deleteAccount(@RequestBody DeleteAccountRequest body,
                                                UserToken userToken,
                                                HttpSession session) {
        try {
            userDeletionService.deleteAccount(
                    userToken.getUserId(),
                    body.password(),
                    body.confirmation(),
                    session);
            return ResponseEntity.ok(new MessageResponse("Account permanently deleted"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(500).body(new ErrorResponse(e.getMessage()));
        }
    }
}
