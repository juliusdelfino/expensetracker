package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.dto.ai.AiQuotaExceededResponse;
import com.delfino.expensetracker.dto.common.ErrorResponse;
import com.delfino.expensetracker.exception.AiModelNotAllowedException;
import com.delfino.expensetracker.exception.AiQuotaExceededException;
import com.delfino.expensetracker.exception.ResourceNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles @Valid failures on @RequestBody (e.g. Expense, Store, ExpenseItem).
     * Returns the first violation message in the standard {"error": "..."} format.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(new ErrorResponse(message, "VALIDATION_FAILED"));
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
        return ResponseEntity.badRequest().body(new ErrorResponse(message, "VALIDATION_FAILED"));
    }

    @ExceptionHandler(AiModelNotAllowedException.class)
    public ResponseEntity<ErrorResponse> handleAiModelNotAllowed(AiModelNotAllowedException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse(ex.getMessage(), "AI_MODEL_NOT_ALLOWED"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse(ex.getMessage(), "INVALID_REQUEST"));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage(), "NOT_FOUND"));
    }

    @ExceptionHandler(AiQuotaExceededException.class)
    public ResponseEntity<AiQuotaExceededResponse> handleAiQuotaExceeded(AiQuotaExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new AiQuotaExceededResponse(
                        ex.getMessage(),
                        ex.getCode(),
                        ex.getType().name(),
                        ex.getUsageCount(),
                        ex.getQuota(),
                        ex.getRequestedUnits(),
                        ex.getRemaining()));
    }
}

