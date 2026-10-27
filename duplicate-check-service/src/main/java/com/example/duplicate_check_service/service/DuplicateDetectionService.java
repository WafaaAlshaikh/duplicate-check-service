package com.example.duplicate_check_service.service;

import com.example.duplicate_check_service.dto.DuplicateCheckRequest;
import com.example.duplicate_check_service.dto.DuplicateCheckResponse;
import com.example.duplicate_check_service.model.entity.InvoiceFingerprint;
import com.example.duplicate_check_service.repository.InvoiceFingerprintRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DuplicateDetectionService {

    private final TextExtractionService textExtractionService;
    private final FingerprintService fingerprintService;
    private final AIDuplicateCheckService aiDuplicateCheckService;
    private final InvoiceFingerprintRepository fingerprintRepository;

    @Value("${duplicate.check.amount.tolerance:0.01}")
    private BigDecimal amountTolerance;

    @Value("${duplicate.check.date.tolerance.days:7}")
    private int dateToleranceDays;

    @Value("${duplicate.check.threshold:0.8}")
    private BigDecimal duplicateThreshold;

    @Value("${duplicate.check.temporary.max-age-hours:24}")
    private int temporaryMaxAgeHours;

    @Transactional
    public DuplicateCheckResponse checkForDuplicates(DuplicateCheckRequest request) {
        log.info("Starting duplicate check for invoice/temp: {}", request.getInvoiceId());

        List<DuplicateCheckResponse.SimilarInvoice> similarInvoices = new ArrayList<>();
        BigDecimal maxConfidence = BigDecimal.ZERO;
        String checkMethod = "MULTI_LAYER";

        try {
            boolean isTemporaryRequest = request.getIsTemporary() != null && request.getIsTemporary();
            log.info("Request is temporary: {}", isTemporaryRequest);

            List<InvoiceFingerprint> amountDateMatches = checkAmountAndDateMatch(request);

            if (!amountDateMatches.isEmpty()) {
                List<DuplicateCheckResponse.SimilarInvoice> matches = convertToSimilarInvoices(amountDateMatches,
                        "AMOUNT_DATE_MATCH", request);
                similarInvoices.addAll(matches);
                maxConfidence = findMaxConfidence(matches);
            }

            if (request.getFile() != null && !request.getFile().isEmpty()) {
                String extractedText = textExtractionService.extractText(request.getFile());
                request.setTextContent(extractedText);

                List<InvoiceFingerprint> textMatches = findSimilarByText(extractedText, request);

                if (!textMatches.isEmpty()) {
                    List<DuplicateCheckResponse.SimilarInvoice> textSimilar = convertToSimilarInvoices(textMatches,
                            "TEXT_SIMILARITY", request);
                    similarInvoices.addAll(textSimilar);

                    BigDecimal textConfidence = findMaxConfidence(textSimilar);
                    if (textConfidence.compareTo(maxConfidence) > 0) {
                        maxConfidence = textConfidence;
                    }
                }
            }

            if (request.getTextContent() != null && !request.getTextContent().isEmpty()) {
                List<InvoiceFingerprint> textContentMatches = findSimilarByText(request.getTextContent(), request);

                if (!textContentMatches.isEmpty()) {
                    List<DuplicateCheckResponse.SimilarInvoice> contentSimilar = convertToSimilarInvoices(
                            textContentMatches, "TEXT_CONTENT", request);
                    similarInvoices.addAll(contentSimilar);

                    BigDecimal contentConfidence = findMaxConfidence(contentSimilar);
                    if (contentConfidence.compareTo(maxConfidence) > 0) {
                        maxConfidence = contentConfidence;
                    }
                }
            }

            if (!similarInvoices.isEmpty() && request.getFile() != null) {
                DuplicateCheckResponse aiResponse = aiDuplicateCheckService.checkWithAI(request, similarInvoices);

                if (aiResponse != null && aiResponse.getConfidenceScore() != null) {
                    if (aiResponse.getConfidenceScore().compareTo(maxConfidence) > 0) {
                        maxConfidence = aiResponse.getConfidenceScore();
                    }
                    checkMethod = "AI_ENHANCED";

                    if (aiResponse.getSimilarInvoices() != null) {
                        similarInvoices.addAll(aiResponse.getSimilarInvoices());
                    }
                }
            }

            similarInvoices = removeDuplicates(similarInvoices);

            boolean isDuplicate = maxConfidence.compareTo(duplicateThreshold) >= 0;

            logDuplicateCheckEvent(request, isDuplicate, maxConfidence, similarInvoices.size());

            return buildResponse(isDuplicate, maxConfidence, checkMethod, similarInvoices);

        } catch (Exception e) {
            log.error("Error during duplicate check: {}", e.getMessage(), e);
            return buildErrorResponse(e.getMessage());
        }
    }

    private List<InvoiceFingerprint> checkAmountAndDateMatch(DuplicateCheckRequest request) {
        List<InvoiceFingerprint> allFingerprints = fingerprintRepository.findAll();
        List<InvoiceFingerprint> matches = new ArrayList<>();

        BigDecimal amount = request.getTotalAmount();
        LocalDate date = request.getInvoiceDate();
        String vendor = request.getVendor();

        log.info("🔍 DUPLICATE CHECK - ENHANCED VERSION");
        log.info("Looking for: Amount=${}, Date={}, Vendor='{}'", amount, date, vendor);
        log.info("Amount tolerance: ${}, Date tolerance: {} days", amountTolerance, dateToleranceDays);
        log.info("Total fingerprints in DB: {}", allFingerprints.size());

        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            log.warn("⚠️ Amount is zero or null, skipping amount-based check");
        }

        int checked = 0;
        int matchesFound = 0;

        for (InvoiceFingerprint fp : allFingerprints) {
            checked++;

            if (request.getInvoiceId() != null &&
                    request.getInvoiceId().equals(fp.getOriginalInvoiceId())) {
                continue;
            }

            boolean isTemporaryFingerprint = fp.getTextContent() != null &&
                    fp.getTextContent().startsWith("[TEMPORARY");
            if (request.getIsTemporary() != null && !request.getIsTemporary() && isTemporaryFingerprint) {
                continue;
            }

            boolean amountMatch = false;
            if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal amountDiff = fp.getTotalAmount().subtract(amount).abs();
                BigDecimal amountToleranceForCheck = amountTolerance;

                if (amount.compareTo(BigDecimal.valueOf(1000)) > 0) {
                    amountToleranceForCheck = BigDecimal.valueOf(5.0);
                }

                amountMatch = amountDiff.compareTo(amountToleranceForCheck) <= 0;
            }

            boolean dateMatch = false;
            if (date != null) {
                long daysDiff = Math.abs(ChronoUnit.DAYS.between(fp.getUploadDate(), date));
                dateMatch = daysDiff <= dateToleranceDays;
            }

            boolean vendorMatch = isVendorSimilar(vendor, fp.getVendorName());

            if (amountMatch && dateMatch && vendorMatch) {
                log.info("🎯 STRONG MATCH FOUND!");
                log.info("  Invoice ID: {}, Amount: ${}, Date: {}, Vendor: {}",
                        fp.getOriginalInvoiceId(), fp.getTotalAmount(),
                        fp.getUploadDate(), fp.getVendorName());
                matches.add(fp);
                matchesFound++;
            } else if (amountMatch && dateMatch) {
                log.info("⚠️ POTENTIAL MATCH (Amount + Date)");
                log.info("  Invoice ID: {}, Amount: ${}, Date: {}",
                        fp.getOriginalInvoiceId(), fp.getTotalAmount(), fp.getUploadDate());
                matches.add(fp);
                matchesFound++;
            } else if (amountMatch && vendorMatch) {
                log.info("⚠️ POTENTIAL MATCH (Amount + Vendor)");
                log.info("  Invoice ID: {}, Amount: ${}, Vendor: {}",
                        fp.getOriginalInvoiceId(), fp.getTotalAmount(), fp.getVendorName());
                matches.add(fp);
                matchesFound++;
            } else if (dateMatch && vendorMatch && amount != null) {
                log.info("💡 WEAK MATCH (Date + Vendor)");
                log.info("  Invoice ID: {}, Date: {}, Vendor: {}",
                        fp.getOriginalInvoiceId(), fp.getUploadDate(), fp.getVendorName());
                matches.add(fp);
                matchesFound++;
            }
        }

        log.info("📊 Results: Checked {} fingerprints, Found {} matches", checked, matchesFound);
        return matches;
    }

    private List<InvoiceFingerprint> findSimilarByText(String text, DuplicateCheckRequest request) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<InvoiceFingerprint> allFingerprints = fingerprintRepository.findAll();
        List<InvoiceFingerprint> matches = new ArrayList<>();

        String textHash = fingerprintService.createTextHash(text);

        for (InvoiceFingerprint fp : allFingerprints) {
            if (request.getInvoiceId() != null &&
                    request.getInvoiceId().equals(fp.getOriginalInvoiceId())) {
                continue;
            }

            boolean isTemporaryFingerprint = fp.getTextContent() != null &&
                    fp.getTextContent().startsWith("[TEMPORARY");
            if (request.getIsTemporary() != null && !request.getIsTemporary() && isTemporaryFingerprint) {
                continue;
            }

            double similarity = fingerprintService.calculateHashSimilarity(textHash, fp.getHashSignature());

            if (similarity > 0.7) {
                log.debug("Text similarity found: {}% for invoice {}",
                        (int) (similarity * 100), fp.getOriginalInvoiceId());
                matches.add(fp);
            }
        }

        log.info("Found {} text similarity matches for invoice", matches.size());
        return matches;
    }

    private boolean isVendorSimilar(String vendor1, String vendor2) {
        if (vendor1 == null || vendor2 == null) {
            return false;
        }

        vendor1 = vendor1.toLowerCase().trim();
        vendor2 = vendor2.toLowerCase().trim();

        if (vendor1.isEmpty() || vendor2.isEmpty()) {
            return false;
        }

        String[] commonWords = { "company", "inc", "ltd", "corp", "group", "enterprise",
                "llc", "co", "and", "the", "of", "&", "co.", "ltd." };

        for (String word : commonWords) {
            vendor1 = vendor1.replaceAll("\\b" + word + "\\b", "").trim();
            vendor2 = vendor2.replaceAll("\\b" + word + "\\b", "").trim();
        }

        if (vendor1.equals(vendor2)) {
            return true;
        }

        if (vendor1.contains(vendor2) || vendor2.contains(vendor1)) {
            return true;
        }

        String[] words1 = vendor1.split("\\s+");
        String[] words2 = vendor2.split("\\s+");

        if (words1.length > 0 && words2.length > 0) {
            String firstWord1 = words1[0];
            String firstWord2 = words2[0];

            if (firstWord1.length() > 2 && firstWord2.length() > 2) {
                double similarity = calculateWordSimilarity(firstWord1, firstWord2);
                if (similarity > 0.8) {
                    return true;
                }
            }
        }

        int commonWordsCount = 0;
        for (String w1 : words1) {
            if (w1.length() > 2) {
                for (String w2 : words2) {
                    if (w2.length() > 2 && w1.equals(w2)) {
                        commonWordsCount++;
                        if (commonWordsCount >= 2) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private double calculateWordSimilarity(String word1, String word2) {
        if (word1.equals(word2))
            return 1.0;

        int commonChars = 0;
        for (char c : word1.toCharArray()) {
            if (word2.indexOf(c) != -1) {
                commonChars++;
            }
        }

        return (double) commonChars / Math.max(word1.length(), word2.length());
    }

    private List<DuplicateCheckResponse.SimilarInvoice> convertToSimilarInvoices(
            List<InvoiceFingerprint> fingerprints, String matchReason, DuplicateCheckRequest request) {

        List<DuplicateCheckResponse.SimilarInvoice> result = new ArrayList<>();

        for (InvoiceFingerprint fp : fingerprints) {
            BigDecimal similarityScore = calculateSimilarityScore(fp, request);

            DuplicateCheckResponse.SimilarInvoice similarInvoice = DuplicateCheckResponse.SimilarInvoice.builder()
                    .invoiceId(fp.getOriginalInvoiceId())
                    .fileName(fp.getVendorName())
                    .uploadDate(fp.getUploadDate())
                    .totalAmount(fp.getTotalAmount())
                    .similarityScore(similarityScore)
                    .matchReason(matchReason)
                    .isTemporary(fp.getTextContent() != null &&
                            fp.getTextContent().startsWith("[TEMPORARY"))
                    .build();
            result.add(similarInvoice);
        }

        return result;
    }

    private BigDecimal calculateSimilarityScore(InvoiceFingerprint fp, DuplicateCheckRequest request) {
        double score = 0.0;

        if (request.getTotalAmount() != null && request.getTotalAmount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal amountDiff = fp.getTotalAmount().subtract(request.getTotalAmount()).abs();
            if (amountDiff.compareTo(BigDecimal.ZERO) == 0) {
                score += 0.4;
            } else if (amountDiff.compareTo(amountTolerance) <= 0) {
                score += 0.35;
            } else {
                BigDecimal maxAmount = fp.getTotalAmount().max(request.getTotalAmount());
                if (maxAmount.compareTo(BigDecimal.ZERO) != 0) {
                    double amountSimilarity = 1.0 - (amountDiff.doubleValue() / maxAmount.doubleValue());
                    score += Math.max(0, amountSimilarity) * 0.2;
                }
            }
        }

        if (request.getInvoiceDate() != null) {
            long daysDiff = Math.abs(ChronoUnit.DAYS.between(fp.getUploadDate(), request.getInvoiceDate()));
            if (daysDiff == 0) {
                score += 0.3;
            } else if (daysDiff <= 7) {
                score += 0.25;
            } else if (daysDiff <= 30) {
                score += 0.15;
            } else if (daysDiff <= 90) {
                score += 0.05;
            }
        }

        if (request.getVendor() != null && fp.getVendorName() != null) {
            String vendor1 = request.getVendor().toLowerCase().trim();
            String vendor2 = fp.getVendorName().toLowerCase().trim();

            if (vendor1.equals(vendor2)) {
                score += 0.3;
            } else if (vendor1.contains(vendor2) || vendor2.contains(vendor1)) {
                score += 0.25;
            } else if (isVendorSimilar(vendor1, vendor2)) {
                score += 0.2;
            } else {
                String[] words1 = vendor1.split("\\s+");
                String[] words2 = vendor2.split("\\s+");

                int commonWords = 0;
                for (String w1 : words1) {
                    for (String w2 : words2) {
                        if (w1.equals(w2) && w1.length() > 2) {
                            commonWords++;
                        }
                    }
                }

                if (commonWords > 0) {
                    score += (commonWords * 0.05);
                }
            }
        }

        return BigDecimal.valueOf(Math.min(score, 1.0));
    }

    private BigDecimal findMaxConfidence(List<DuplicateCheckResponse.SimilarInvoice> similarInvoices) {
        return similarInvoices.stream()
                .map(DuplicateCheckResponse.SimilarInvoice::getSimilarityScore)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
    }

    private List<DuplicateCheckResponse.SimilarInvoice> removeDuplicates(
            List<DuplicateCheckResponse.SimilarInvoice> similarInvoices) {

        return similarInvoices.stream()
                .collect(Collectors.toMap(
                        DuplicateCheckResponse.SimilarInvoice::getInvoiceId,
                        invoice -> invoice,
                        (existing, replacement) -> {
                            if (existing.getSimilarityScore().compareTo(replacement.getSimilarityScore()) < 0) {
                                return replacement;
                            }
                            return existing;
                        }))
                .values()
                .stream()
                .sorted((a, b) -> b.getSimilarityScore().compareTo(a.getSimilarityScore()))
                .collect(Collectors.toList());
    }

    private DuplicateCheckResponse buildResponse(boolean isDuplicate,
            BigDecimal confidenceScore,
            String checkMethod,
            List<DuplicateCheckResponse.SimilarInvoice> similarInvoices) {

        List<DuplicateCheckResponse.SimilarInvoice> topResults = similarInvoices.stream()
                .limit(5)
                .collect(Collectors.toList());

        String recommendation = generateRecommendation(isDuplicate, confidenceScore, topResults);

        log.info("Duplicate check completed: duplicate={}, confidence={}, method={}, matches={}",
                isDuplicate, confidenceScore, checkMethod, topResults.size());

        DuplicateCheckResponse response = new DuplicateCheckResponse();
        response.setDuplicate(isDuplicate);
        response.setConfidenceScore(confidenceScore);
        response.setCheckMethod(checkMethod);
        response.setRecommendation(recommendation);
        response.setSimilarInvoices(topResults);
        response.setTimestamp(LocalDateTime.now());

        return response;
    }

    private DuplicateCheckResponse buildErrorResponse(String errorMessage) {
        DuplicateCheckResponse response = new DuplicateCheckResponse();
        response.setDuplicate(false);
        response.setConfidenceScore(BigDecimal.ZERO);
        response.setCheckMethod("ERROR");
        response.setRecommendation("Error: " + errorMessage);
        response.setSimilarInvoices(new ArrayList<>());
        response.setTimestamp(LocalDateTime.now());

        return response;
    }

    private String generateRecommendation(boolean isDuplicate,
            BigDecimal confidenceScore,
            List<DuplicateCheckResponse.SimilarInvoice> similarInvoices) {
        if (isDuplicate) {
            if (confidenceScore.compareTo(BigDecimal.valueOf(0.95)) >= 0) {
                return "❌ HIGH RISK: This appears to be a duplicate invoice. Please verify before proceeding.";
            } else if (confidenceScore.compareTo(BigDecimal.valueOf(0.8)) >= 0) {
                return "⚠️ POTENTIAL DUPLICATE: This invoice is very similar to existing ones. Please review carefully.";
            }
        }

        if (similarInvoices != null && !similarInvoices.isEmpty()) {
            if (confidenceScore.compareTo(BigDecimal.valueOf(0.6)) > 0) {
                return "ℹ️ Some similarities found, but not considered duplicate. Proceed with caution.";
            } else if (confidenceScore.compareTo(BigDecimal.valueOf(0.3)) > 0) {
                return "ℹ️ Minor similarities detected. Likely not a duplicate.";
            }
        }

        return "✅ No significant similarities found. You can proceed with uploading.";
    }

    private void logDuplicateCheckEvent(DuplicateCheckRequest request,
            boolean isDuplicate,
            BigDecimal confidenceScore,
            int matchesCount) {
        log.info("📝 DUPLICATE CHECK EVENT LOG");
        log.info("  Invoice/Temp ID: {}", request.getInvoiceId());
        log.info("  User: {}, Role: {}", request.getUsername(), request.getRole());
        log.info("  Amount: ${}, Date: {}, Vendor: {}",
                request.getTotalAmount(), request.getInvoiceDate(), request.getVendor());
        log.info("  Result: {} (Confidence: {}%)",
                isDuplicate ? "DUPLICATE" : "UNIQUE",
                confidenceScore.multiply(BigDecimal.valueOf(100)));
        log.info("  Matches Found: {}", matchesCount);
        log.info("  Timestamp: {}", LocalDateTime.now());
    }

    @Transactional
    public void saveTemporaryFingerprint(DuplicateCheckRequest request) {
        log.info("💾 Saving TEMPORARY fingerprint for ID: {}", request.getInvoiceId());

        try {
            String textContent = request.getTextContent();
            if (textContent == null || textContent.trim().isEmpty()) {
                textContent = String.format("[TEMPORARY] Invoice TempID:%d | Date:%s | Amount:%s | Vendor:%s",
                        request.getInvoiceId(),
                        request.getInvoiceDate(),
                        request.getTotalAmount(),
                        request.getVendor() != null ? request.getVendor() : "Unknown");
            } else {
                textContent = "[TEMPORARY]" + textContent;
            }

            fingerprintService.saveFingerprint(
                    request.getInvoiceId(),
                    request.getInvoiceDate(),
                    request.getTotalAmount(),
                    request.getVendor() != null ? request.getVendor() : "Unknown",
                    textContent);

            log.info("✅ Temporary fingerprint saved for: {}", request.getInvoiceId());

        } catch (Exception e) {
            log.error("❌ Failed to save temporary fingerprint: {}", e.getMessage());
            throw new RuntimeException("Failed to save temporary fingerprint", e);
        }
    }

    @Transactional
    public void replaceTemporaryWithFinal(DuplicateCheckRequest request) {
        log.info("🔄 Replacing temporary: {} with final: {}",
                request.getTemporaryId(), request.getFinalInvoiceId());

        try {
            Optional<InvoiceFingerprint> temporaryFingerprint = Optional
                    .ofNullable(fingerprintRepository.findByOriginalInvoiceId(request.getTemporaryId()));

            if (temporaryFingerprint.isEmpty()) {
                log.warn("⚠️ Temporary fingerprint not found: {}", request.getTemporaryId());
                saveFinalFingerprint(request);
                return;
            }

            InvoiceFingerprint fingerprint = temporaryFingerprint.get();

            fingerprint.setOriginalInvoiceId(request.getFinalInvoiceId());

            String finalTextContent = request.getTextContent();
            if (finalTextContent == null || finalTextContent.trim().isEmpty()) {
                finalTextContent = String.format("InvoiceID:%d|Vendor:%s|Date:%s|Amount:%s",
                        request.getFinalInvoiceId(),
                        request.getVendor(),
                        request.getInvoiceDate(),
                        request.getTotalAmount());
            } else {
                finalTextContent = finalTextContent.replace("[TEMPORARY]", "");
            }
            fingerprint.setTextContent(finalTextContent);

            if (request.getVendor() != null && !request.getVendor().equals(fingerprint.getVendorName())) {
                fingerprint.setVendorName(request.getVendor());
            }

            String newHash = fingerprintService.createTextHash(finalTextContent);
            fingerprint.setHashSignature(newHash);

            fingerprintRepository.save(fingerprint);

            log.info("✅ Successfully replaced temporary {} with final {}",
                    request.getTemporaryId(), request.getFinalInvoiceId());

        } catch (Exception e) {
            log.error("❌ Failed to replace temporary fingerprint: {}", e.getMessage());
            throw new RuntimeException("Failed to replace temporary fingerprint", e);
        }
    }

    private void saveFinalFingerprint(DuplicateCheckRequest request) {
        log.info("💾 Saving FINAL fingerprint for invoice: {}", request.getFinalInvoiceId());

        String textContent = request.getTextContent();
        if (textContent == null || textContent.trim().isEmpty()) {
            textContent = String.format("InvoiceID:%d|Vendor:%s|Date:%s|Amount:%s",
                    request.getFinalInvoiceId(),
                    request.getVendor(),
                    request.getInvoiceDate(),
                    request.getTotalAmount());
        }

        fingerprintService.saveFingerprint(
                request.getFinalInvoiceId(),
                request.getInvoiceDate(),
                request.getTotalAmount(),
                request.getVendor(),
                textContent);

        log.info("✅ Final fingerprint saved for invoice: {}", request.getFinalInvoiceId());
    }

    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupTemporaryFingerprints() {
        log.info("🧹 Starting cleanup of temporary fingerprints...");

        try {
            List<InvoiceFingerprint> allFingerprints = fingerprintRepository.findAll();
            List<InvoiceFingerprint> temporaries = allFingerprints.stream()
                    .filter(fp -> fp.getTextContent() != null &&
                            fp.getTextContent().startsWith("[TEMPORARY"))
                    .collect(Collectors.toList());

            log.info("Found {} temporary fingerprints to check", temporaries.size());

            int deletedCount = 0;
            LocalDateTime cutoffTime = LocalDateTime.now().minusHours(temporaryMaxAgeHours);

            for (InvoiceFingerprint fp : temporaries) {
                if (fp.getCreatedAt() != null && fp.getCreatedAt().isBefore(cutoffTime)) {
                    fingerprintRepository.delete(fp);
                    deletedCount++;
                    log.debug("Deleted old temporary fingerprint: {} (created at {})",
                            fp.getOriginalInvoiceId(), fp.getCreatedAt());
                }
            }

            log.info("🧹 Cleanup completed: {} temporary fingerprints deleted", deletedCount);

        } catch (Exception e) {
            log.error("Error during cleanup of temporary fingerprints: {}", e.getMessage());
        }
    }

    public List<DuplicateCheckResponse> getDuplicateHistory(Long invoiceId, String username, String role) {
        log.info("Retrieving duplicate history for invoice: {}, user: {}", invoiceId, username);

        List<DuplicateCheckResponse> history = new ArrayList<>();

        log.info("Returning {} history records for invoice: {}", history.size(), invoiceId);
        return history;
    }

    public long getFingerprintCount() {
        try {
            long count = fingerprintRepository.count();
            log.debug("Total fingerprints in database: {}", count);
            return count;
        } catch (Exception e) {
            log.error("Failed to get fingerprint count: {}", e.getMessage());
            throw new RuntimeException("Database connection error: " + e.getMessage());
        }
    }

    public Map<String, Object> getFingerprintStats() {
        Map<String, Object> stats = new HashMap<>();

        try {
            long totalCount = fingerprintRepository.count();
            List<InvoiceFingerprint> allFingerprints = fingerprintRepository.findAll();

            BigDecimal totalAmount = BigDecimal.ZERO;
            for (InvoiceFingerprint fp : allFingerprints) {
                totalAmount = totalAmount.add(fp.getTotalAmount());
            }
            BigDecimal averageAmount = totalCount > 0
                    ? totalAmount.divide(BigDecimal.valueOf(totalCount), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            long temporaryCount = allFingerprints.stream()
                    .filter(fp -> fp.getTextContent() != null &&
                            fp.getTextContent().startsWith("[TEMPORARY"))
                    .count();

            Optional<InvoiceFingerprint> latest = allFingerprints.stream()
                    .max(Comparator.comparing(InvoiceFingerprint::getCreatedAt));

            Optional<InvoiceFingerprint> oldest = allFingerprints.stream()
                    .min(Comparator.comparing(InvoiceFingerprint::getCreatedAt));

            stats.put("totalFingerprints", totalCount);
            stats.put("temporaryFingerprints", temporaryCount);
            stats.put("permanentFingerprints", totalCount - temporaryCount);
            stats.put("totalAmount", totalAmount);
            stats.put("averageAmount", averageAmount);
            stats.put("latestFingerprint", latest.map(InvoiceFingerprint::getOriginalInvoiceId).orElse(null));
            stats.put("latestTimestamp", latest.map(InvoiceFingerprint::getCreatedAt).orElse(null));
            stats.put("oldestTimestamp", oldest.map(InvoiceFingerprint::getCreatedAt).orElse(null));

            log.info("Fingerprint stats generated: {} total, {} temporary", totalCount, temporaryCount);

            return stats;

        } catch (Exception e) {
            log.error("Failed to get fingerprint stats: {}", e.getMessage());
            stats.put("error", e.getMessage());
            return stats;
        }
    }

    public boolean isDatabaseConnected() {
        try {
            fingerprintRepository.count();
            return true;
        } catch (Exception e) {
            log.error("Database connection error: {}", e.getMessage());
            return false;
        }
    }

    public List<InvoiceFingerprint> findFingerprintsByVendor(String vendor) {
        log.info("Searching for fingerprints by vendor: {}", vendor);

        List<InvoiceFingerprint> allFingerprints = fingerprintRepository.findAll();
        return allFingerprints.stream()
                .filter(fp -> fp.getVendorName() != null &&
                        fp.getVendorName().toLowerCase().contains(vendor.toLowerCase()))
                .collect(Collectors.toList());
    }

    public boolean hasFingerprint(Long invoiceId) {
        Optional<InvoiceFingerprint> fingerprint = Optional
                .ofNullable(fingerprintRepository.findByOriginalInvoiceId(invoiceId));
        return fingerprint.isPresent();
    }
}