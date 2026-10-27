package com.example.duplicate_check_service.dto;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class DuplicateCheckRequest {
    private Long invoiceId;
    private MultipartFile file;
    private LocalDate invoiceDate;
    private BigDecimal totalAmount;
    private String vendor;
    private String fileName;
    private String username;
    private String role;
    private String textContent;
    
    private Boolean isTemporary;
    private Long temporaryId;
    private Long finalInvoiceId;
    private String action;
    
    public static DuplicateCheckRequestBuilder builder() {
        return new DuplicateCheckRequestBuilder();
    }
    
    public static class DuplicateCheckRequestBuilder {
        private DuplicateCheckRequest request = new DuplicateCheckRequest();
        
        public DuplicateCheckRequestBuilder invoiceId(Long invoiceId) {
            request.invoiceId = invoiceId;
            return this;
        }
        
        public DuplicateCheckRequestBuilder file(MultipartFile file) {
            request.file = file;
            return this;
        }
        
        public DuplicateCheckRequestBuilder invoiceDate(LocalDate invoiceDate) {
            request.invoiceDate = invoiceDate;
            return this;
        }
        
        public DuplicateCheckRequestBuilder totalAmount(BigDecimal totalAmount) {
            request.totalAmount = totalAmount;
            return this;
        }
        
        public DuplicateCheckRequestBuilder vendor(String vendor) {
            request.vendor = vendor;
            return this;
        }
        
        public DuplicateCheckRequestBuilder fileName(String fileName) {
            request.fileName = fileName;
            return this;
        }
        
        public DuplicateCheckRequestBuilder username(String username) {
            request.username = username;
            return this;
        }
        
        public DuplicateCheckRequestBuilder role(String role) {
            request.role = role;
            return this;
        }
        
        public DuplicateCheckRequest build() {
            return request;
        }
    }
}