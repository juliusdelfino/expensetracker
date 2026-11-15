package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.dto.ai.AiQuotaExceededResponse;
import com.delfino.expensetracker.dto.common.ErrorResponse;
import com.delfino.expensetracker.exception.AiModelNotAllowedException;
import com.delfino.expensetracker.exception.AiQuotaExceededException;
import com.delfino.expensetracker.exception.ResourceNotFoundException;
import com.delfino.expensetracker.service.I18nService;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final I18nService i18nService;

    public GlobalExceptionHandler(I18nService i18nService) {
        this.i18nService = i18nService;
    }

    /**
     * Handles @Valid failures on @RequestBody (e.g. Expense, Store, ExpenseItem).
     * Returns the first violation message in the standard {"error": "..."} format.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        String fallback = i18nService.message("error.validation_failed", "Validation failed");
        return ResponseEntity.badRequest().body(new ErrorResponse(message.isBlank() ? fallback : message, "VALIDATION_FAILED"));
    }

    /**
     * Handles @Validated failures on @RequestParam / @PathVariable.
     * Returns the first violation message in the standard {"error": "..."} format.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
                .collect(Collectors.joining("; "));
        String fallback = i18nService.message("error.validation_failed", "Validation failed");
        return ResponseEntity.badRequest().body(new ErrorResponse(message.isBlank() ? fallback : message, "VALIDATION_FAILED"));
    }

    @ExceptionHandler(AiModelNotAllowedException.class)
    public ResponseEntity<ErrorResponse> handleAiModelNotAllowed(AiModelNotAllowedException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = i18nService.message("error.ai_model_not_allowed", "Selected AI model is not allowed");
        }
        return ResponseEntity.badRequest().body(new ErrorResponse(message, "AI_MODEL_NOT_ALLOWED"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = i18nService.message("error.invalid_request", "Invalid request");
        }
        return ResponseEntity.badRequest().body(new ErrorResponse(message, "INVALID_REQUEST"));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = i18nService.message("error.not_found", "Resource not found");
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(message, "NOT_FOUND"));
    }

    @ExceptionHandler(AiQuotaExceededException.class)
    public ResponseEntity<AiQuotaExceededResponse> handleAiQuotaExceeded(AiQuotaExceededException ex) {
        String localized = i18nService.message(
                "error.ai_quota_exceeded",
                "Monthly {0} quota exceeded",
                ex.getType().name());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new AiQuotaExceededResponse(
                        localized,
                        ex.getCode(),
                        ex.getType().name(),
                        ex.getUsageCount(),
                        ex.getQuota(),
                        ex.getRequestedUnits(),
                        ex.getRemaining()));
    }
}

