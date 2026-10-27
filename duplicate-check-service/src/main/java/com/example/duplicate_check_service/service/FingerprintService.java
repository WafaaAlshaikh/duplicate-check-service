package com.example.duplicate_check_service.service;

import com.example.duplicate_check_service.model.entity.InvoiceFingerprint;
import com.example.duplicate_check_service.repository.InvoiceFingerprintRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FingerprintService {

    private final InvoiceFingerprintRepository fingerprintRepository;

    public String createTextHash(String text) {
        try {
            if (text == null || text.isEmpty()) {
                return "";
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));

            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("Error creating text hash: {}", e.getMessage());
            return "";
        }
    }

    public List<InvoiceFingerprint> findSimilarTextHashes(String currentHash) {
        if (currentHash == null || currentHash.isEmpty()) {
            return new ArrayList<>();
        }

        List<InvoiceFingerprint> allFingerprints = fingerprintRepository.findAll();
        List<InvoiceFingerprint> similarFingerprints = new ArrayList<>();

        for (InvoiceFingerprint fingerprint : allFingerprints) {
            double similarity = calculateHashSimilarity(currentHash, fingerprint.getHashSignature());

            if (similarity > 0.7) {
                similarFingerprints.add(fingerprint);
            }
        }

        return similarFingerprints;
    }

    public double calculateHashSimilarity(String hash1, String hash2) {
        if (hash1 == null || hash2 == null || hash1.isEmpty() || hash2.isEmpty()) {
            return 0.0;
        }

        if (hash1.equals(hash2)) {
            return 1.0;
        }

        LevenshteinDistance levenshtein = new LevenshteinDistance();
        int distance = levenshtein.apply(hash1, hash2);
        int maxLength = Math.max(hash1.length(), hash2.length());

        if (maxLength == 0)
            return 1.0;

        return 1.0 - ((double) distance / maxLength);
    }

    public void saveFingerprint(Long invoiceId, LocalDate uploadDate,
            BigDecimal totalAmount, String vendor,
            String textContent) {

        log.info("=== SAVING FINGERPRINT - Invoice ID: {} ===", invoiceId);
        log.info("Amount: {}, Date: {}, Vendor: {}", totalAmount, uploadDate, vendor);

        String hashSignature = "";

        if (textContent == null || textContent.trim().isEmpty()) {
            textContent = String.format("Invoice #%d | Vendor: %s | Amount: $%s | Date: %s",
                    invoiceId, vendor, totalAmount, uploadDate);
            log.info("Created text content: {}", textContent);
        }

        hashSignature = createTextHash(textContent);

        InvoiceFingerprint fingerprint = new InvoiceFingerprint();
        fingerprint.setOriginalInvoiceId(invoiceId);
        fingerprint.setUploadDate(uploadDate);
        fingerprint.setTotalAmount(totalAmount);
        fingerprint.setVendorName(vendor);
        fingerprint.setHashSignature(hashSignature);
        fingerprint.setTextContent(textContent);

        fingerprintRepository.save(fingerprint);
        log.info("✅ Saved fingerprint for invoice: {} with text length: {}",
                invoiceId, textContent.length());
    }

    public boolean fingerprintExists(Long invoiceId) {
        return fingerprintRepository.existsByOriginalInvoiceId(invoiceId);
    }
}