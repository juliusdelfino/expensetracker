package com.delfino.expensetracker.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/attachments")
public class AttachmentController {

    @Value("${app.data.dir:data}")
    private String dataDir;

    @GetMapping("/receipts/{filename}")
    public ResponseEntity<Resource> getReceipt(@PathVariable String filename) {
        return serveFile(Path.of(dataDir, "receipts", filename));
    }

    @GetMapping("/{expenseId}/{filename}")
    public ResponseEntity<Resource> getAttachment(@PathVariable String expenseId, @PathVariable String filename) {
        return serveFile(Path.of(dataDir, "attachments", expenseId, filename));
    }

    private ResponseEntity<Resource> serveFile(Path path) {
        if (!Files.exists(path)) return ResponseEntity.notFound().build();
        try {
            String contentType = Files.probeContentType(path);
            if (contentType == null) contentType = "application/octet-stream";
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(new FileSystemResource(path));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}

