package com.example.duplicate_check_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DuplicateCheckResponse {
    private boolean duplicate;
    private BigDecimal confidenceScore;
    private String checkMethod;
    private String recommendation;
    private List<SimilarInvoice> similarInvoices;
    
    private LocalDateTime timestamp;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimilarInvoice {
        private Long invoiceId;
        private String fileName;
        private LocalDate uploadDate;
        private BigDecimal totalAmount;
        private BigDecimal similarityScore;
        private String matchReason;
        
        private boolean isTemporary;
    }
}