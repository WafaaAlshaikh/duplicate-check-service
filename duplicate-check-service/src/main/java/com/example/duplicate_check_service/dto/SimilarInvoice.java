package com.example.duplicate_check_service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SimilarInvoice {
    private Long invoiceId;
    private String fileName;
    private LocalDate uploadDate;
    private BigDecimal totalAmount;
    private String vendor;
    private BigDecimal similarityScore;
    private String matchReason; 
}
