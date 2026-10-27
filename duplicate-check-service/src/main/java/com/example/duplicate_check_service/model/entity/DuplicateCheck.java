package com.example.duplicate_check_service.model.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@AllArgsConstructor
@Table(name = "duplicate_checks")
public class DuplicateCheck {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long checkId;
    
    @Column(name = "original_invoice_id")
    private Long originalInvoiceId;
    
    @Column(name = "potential_duplicate_id")
    private Long potentialDuplicateId;
    
    @Column(name = "confidence_score", nullable = false, precision = 5, scale = 4)
    private BigDecimal confidenceScore;
    
    @Column(name = "check_method", length = 50)
    private String checkMethod;
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
}