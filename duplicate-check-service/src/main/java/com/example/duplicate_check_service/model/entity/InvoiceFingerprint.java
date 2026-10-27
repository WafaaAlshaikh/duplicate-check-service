package com.example.duplicate_check_service.model.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;


@Entity
@Table(name = "invoice_fingerprints")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceFingerprint {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fingerprint_id")
    private Long fingerprintId;
    
    @Column(name = "original_invoice_id", nullable = false)
    private Long originalInvoiceId;
    
    @Column(name = "upload_date", nullable = false)
    private LocalDate uploadDate;
    
    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;
    
    @Column(name = "vendor_name")
    private String vendorName;
    
    @Column(name = "hash_signature", nullable = false, length = 512)
    private String hashSignature;
    
    @Lob
    @Column(name = "text_content", columnDefinition = "LONGTEXT")
    private String textContent;
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
