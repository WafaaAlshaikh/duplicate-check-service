package com.example.duplicate_check_service.repository;

import com.example.duplicate_check_service.model.entity.InvoiceFingerprint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface InvoiceFingerprintRepository extends JpaRepository<InvoiceFingerprint, Long> {
    
    @Query("SELECT f FROM InvoiceFingerprint f WHERE " +
           "ABS(f.totalAmount - :amount) <= :tolerance AND " +
           "ABS(DATEDIFF(f.uploadDate, :date)) <= :dateTolerance")
    List<InvoiceFingerprint> findByAmountAndDateWithTolerance(
            @Param("amount") BigDecimal amount,
            @Param("date") LocalDate date,
            @Param("tolerance") BigDecimal tolerance,
            @Param("dateTolerance") int dateTolerance);
    
    @Query("SELECT f FROM InvoiceFingerprint f WHERE " +
           "ABS(f.totalAmount - :amount) <= :tolerance")
    List<InvoiceFingerprint> findByAmountWithinTolerance(
            @Param("amount") BigDecimal amount,
            @Param("tolerance") BigDecimal tolerance);
    
    @Query("SELECT f FROM InvoiceFingerprint f WHERE " +
           "f.uploadDate BETWEEN :startDate AND :endDate")
    List<InvoiceFingerprint> findByDateRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
    
    @Query(value = "SELECT * FROM invoice_fingerprints WHERE " +
           "MATCH(text_content) AGAINST(:keywords IN BOOLEAN MODE) LIMIT :limit",
           nativeQuery = true)
    List<InvoiceFingerprint> findByTextContent(@Param("keywords") String keywords, 
                                              @Param("limit") int limit);
    
    boolean existsByOriginalInvoiceId(Long originalInvoiceId);
    
    InvoiceFingerprint findByOriginalInvoiceId(Long originalInvoiceId);

    @Query("SELECT COUNT(f) FROM InvoiceFingerprint f")
    long countAllFingerprints();
    
    @Query("SELECT COUNT(f) FROM InvoiceFingerprint f WHERE f.uploadDate >= :date")
    long countByDateAfter(@Param("date") LocalDate date);
    
    @Query("SELECT COUNT(f) FROM InvoiceFingerprint f WHERE f.totalAmount BETWEEN :min AND :max")
    long countByAmountRange(@Param("min") BigDecimal min, @Param("max") BigDecimal max);



    
    List<InvoiceFingerprint> findByTextContentContaining(String content);
    
    @Query("SELECT COUNT(f) FROM InvoiceFingerprint f WHERE f.textContent LIKE '[TEMPORARY]%'")
    long countTemporaryFingerprints();
    
    @Query("SELECT f FROM InvoiceFingerprint f WHERE f.textContent LIKE '[TEMPORARY]%'")
    List<InvoiceFingerprint> findAllTemporaryFingerprints();

}