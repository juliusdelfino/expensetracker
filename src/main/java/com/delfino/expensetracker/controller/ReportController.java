package com.delfino.expensetracker.controller;

import com.delfino.expensetracker.dto.auth.UserToken;
import com.delfino.expensetracker.dto.common.ErrorResponse;
import com.delfino.expensetracker.dto.common.MessageResponse;
import com.delfino.expensetracker.dto.report.CreateReportRequest;
import com.delfino.expensetracker.dto.report.ReportResponse;
import com.delfino.expensetracker.model.Report;
import com.delfino.expensetracker.service.ReportPdfService;
import com.delfino.expensetracker.service.ReportQueryService;
import com.delfino.expensetracker.service.ReportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
@Validated
public class ReportController {

    private static final String REPORT_NOT_FOUND = "Report not found";

    private final ReportService reportService;
    private final ReportQueryService reportQueryService;
    private final ReportPdfService reportPdfService;

    public ReportController(ReportService reportService,
                            ReportQueryService reportQueryService,
                            ReportPdfService reportPdfService) {
        this.reportService = reportService;
        this.reportQueryService = reportQueryService;
        this.reportPdfService = reportPdfService;
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Object> createReport(@RequestBody @Valid CreateReportRequest body, UserToken userToken) {
        try {
            Report report = reportService.createReport(userToken.getUserId(), body);
            ReportResponse response = reportQueryService.toResponse(report);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(new ErrorResponse(ex.getMessage()));
        }
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Object> listReports(UserToken userToken) {
        return ResponseEntity.ok(reportQueryService.listReports(userToken.getUserId()));
    }

    @GetMapping("/{reportId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Object> getReport(@PathVariable Long reportId, UserToken userToken) {
        return reportQueryService.getReport(userToken.getUserId(), reportId)
                .<ResponseEntity<Object>>map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse(REPORT_NOT_FOUND)));
    }

    @GetMapping("/{reportId}/pdf")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Object> exportReportPdf(@PathVariable Long reportId, UserToken userToken) {
        return reportQueryService.getReport(userToken.getUserId(), reportId)
                .<ResponseEntity<Object>>map(report -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_PDF)
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"" + reportPdfService.buildFilename(report) + "\"")
                        .body(reportPdfService.generatePdf(userToken.getUserId(), report)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse(REPORT_NOT_FOUND)));
    }

    @DeleteMapping("/{reportId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Object> deleteReport(@PathVariable Long reportId, UserToken userToken) {
        boolean deleted = reportService.deleteReport(userToken.getUserId(), reportId);
        if (!deleted) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(REPORT_NOT_FOUND));
        }
        return ResponseEntity.ok(new MessageResponse("Report deleted"));
    }
}


