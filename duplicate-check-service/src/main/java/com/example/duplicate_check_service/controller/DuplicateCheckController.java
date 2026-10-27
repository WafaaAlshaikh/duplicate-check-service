package com.example.duplicate_check_service.controller;

import com.example.duplicate_check_service.dto.DuplicateCheckRequest;
import com.example.duplicate_check_service.dto.DuplicateCheckResponse;
import com.example.duplicate_check_service.service.DuplicateDetectionService;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/duplicate-check")
@RequiredArgsConstructor
public class DuplicateCheckController {
    
    private final DuplicateDetectionService duplicateDetectionService;
    
    @PostMapping("/check")
    public ResponseEntity<DuplicateCheckResponse> checkDuplicate(
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestParam("invoiceDate") String invoiceDate,
            @RequestParam("totalAmount") String totalAmount,
            @RequestParam("username") String username,
            @RequestParam("role") String role,
            @RequestParam(value = "invoiceId", required = false) Long invoiceId,
            @RequestParam(value = "vendor", required = false) String vendor,
            @RequestParam(value = "fileName", required = false) String fileName,
            @RequestParam(value = "isTemporary", required = false, defaultValue = "false") boolean isTemporary) {
        
        log.info("Duplicate check request from user: {}, role: {}, invoiceId: {}", 
                username, role, invoiceId);
        
        DuplicateCheckRequest request = new DuplicateCheckRequest();
        request.setFile(file);
        request.setInvoiceDate(LocalDate.parse(invoiceDate));
        request.setTotalAmount(new BigDecimal(totalAmount));
        request.setUsername(username);
        request.setRole(role);
        request.setInvoiceId(invoiceId);
        request.setVendor(vendor);
        request.setFileName(fileName);
        request.setIsTemporary(isTemporary); 
        
        DuplicateCheckResponse response = duplicateDetectionService.checkForDuplicates(request);
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/save-fingerprint")
    public ResponseEntity<Void> saveFingerprint(@RequestBody DuplicateCheckRequest request) {
        log.info("Saving fingerprint for invoice: {}", request.getInvoiceId());
        
        try {
            duplicateDetectionService.saveTemporaryFingerprint(request);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to save fingerprint: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @PostMapping("/check-without-file")
    public ResponseEntity<DuplicateCheckResponse> checkDuplicateWithoutFile(
            @RequestBody DuplicateCheckRequest request) {
        
        log.info("Duplicate check without file for user: {}, invoiceId: {}", 
                request.getUsername(), request.getInvoiceId());
        
        DuplicateCheckResponse response = duplicateDetectionService.checkForDuplicates(request);
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/history/{invoiceId}")
    public ResponseEntity<List<DuplicateCheckResponse>> getDuplicateHistory(
            @PathVariable Long invoiceId,
            @RequestParam String username,
            @RequestParam String role) {
        
        log.info("Fetching duplicate history for invoice: {} by user: {}", invoiceId, username);
        
        List<DuplicateCheckResponse> history = 
            duplicateDetectionService.getDuplicateHistory(invoiceId, username, role);
        
        return ResponseEntity.ok(history);
    }
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", "duplicate-check-service");
        health.put("timestamp", LocalDateTime.now());
        health.put("version", "1.0.0");
        
        try {
            if (duplicateDetectionService.isDatabaseConnected()) {
                long fingerprintCount = duplicateDetectionService.getFingerprintCount();
                Map<String, Object> stats = duplicateDetectionService.getFingerprintStats();
                
                health.put("database", "CONNECTED");
                health.put("fingerprintCount", fingerprintCount);
                health.put("stats", stats);
            } else {
                health.put("database", "DISCONNECTED");
                health.put("error", "Database connection failed");
            }
        } catch (Exception e) {
            health.put("database", "ERROR");
            health.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(health);
    }
    
    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "pong");
        response.put("timestamp", LocalDateTime.now());
        response.put("service", "duplicate-check-service");
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        try {
            Map<String, Object> stats = duplicateDetectionService.getFingerprintStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Failed to get stats: {}", e.getMessage());
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @PostMapping("/save-temporary")
    public ResponseEntity<Void> saveTemporaryFingerprint(@RequestBody DuplicateCheckRequest request) {
        log.info("📝 Saving TEMPORARY fingerprint for invoice/temp: {}", request.getInvoiceId());
        
        try {
            duplicateDetectionService.saveTemporaryFingerprint(request);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to save temporary fingerprint: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/replace-temporary")
    public ResponseEntity<Void> replaceTemporaryWithFinal(@RequestBody DuplicateCheckRequest request) {
        log.info("🔄 Replacing temporary fingerprint: {} with final: {}", 
                 request.getTemporaryId(), request.getFinalInvoiceId());
        
        try {
            duplicateDetectionService.replaceTemporaryWithFinal(request);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Failed to replace temporary fingerprint: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

@Data
class ReplaceFingerprintRequest {
    private Long temporaryId;
    private Long finalInvoiceId;
    private LocalDate invoiceDate;
    private BigDecimal totalAmount;
    private String vendor;
    private String textContent;
    private String username;
    private String role;
}