package com.example.duplicate_check_service.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.CosineSimilarity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class SimilarityCalculator {

    public BigDecimal calculateOverallSimilarity(String text1, String text2,
            BigDecimal amount1, BigDecimal amount2,
            LocalDate date1, LocalDate date2,
            String vendor1, String vendor2) {

        double textSimilarity = calculateTextSimilarity(text1, text2);

        double amountSimilarity = calculateAmountSimilarity(amount1, amount2);

        double dateSimilarity = calculateDateSimilarity(date1, date2);

        double vendorSimilarity = calculateVendorSimilarity(vendor1, vendor2);

        double overallSimilarity = (textSimilarity * 0.4) +
                (amountSimilarity * 0.3) +
                (dateSimilarity * 0.2) +
                (vendorSimilarity * 0.1);

        return BigDecimal.valueOf(overallSimilarity);
    }

    private double calculateTextSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null || text1.isEmpty() || text2.isEmpty()) {
            return 0.0;
        }

        Map<CharSequence, Integer> vector1 = createTextVector(text1);
        Map<CharSequence, Integer> vector2 = createTextVector(text2);

        CosineSimilarity cosine = new CosineSimilarity();
        return cosine.cosineSimilarity(vector1, vector2);
    }

    private Map<CharSequence, Integer> createTextVector(String text) {
        Map<CharSequence, Integer> vector = new HashMap<>();

        String[] words = text.toLowerCase().split("\\s+");

        for (String word : words) {
            if (word.length() > 2) {
                vector.put(word, vector.getOrDefault(word, 0) + 1);
            }
        }

        return vector;
    }

    private double calculateAmountSimilarity(BigDecimal amount1, BigDecimal amount2) {
        if (amount1 == null || amount2 == null) {
            return 0.0;
        }

        BigDecimal difference = amount1.subtract(amount2).abs();
        BigDecimal maxAmount = amount1.max(amount2);

        if (maxAmount.compareTo(BigDecimal.ZERO) == 0) {
            return 1.0;
        }

        double similarity = 1.0 - (difference.doubleValue() / maxAmount.doubleValue());

        return Math.max(0.0, Math.min(1.0, similarity));
    }

    private double calculateDateSimilarity(LocalDate date1, LocalDate date2) {
        if (date1 == null || date2 == null) {
            return 0.0;
        }

        long daysDifference = Math.abs(ChronoUnit.DAYS.between(date1, date2));

        if (daysDifference <= 7) {
            return 1.0 - (daysDifference / 7.0);
        }

        return 0.0;
    }

    private double calculateVendorSimilarity(String vendor1, String vendor2) {
        if (vendor1 == null || vendor2 == null) {
            return 0.0;
        }

        vendor1 = vendor1.toLowerCase().trim();
        vendor2 = vendor2.toLowerCase().trim();

        if (vendor1.equals(vendor2)) {
            return 1.0;
        }

        org.apache.commons.text.similarity.JaroWinklerSimilarity jw = new org.apache.commons.text.similarity.JaroWinklerSimilarity();

        return jw.apply(vendor1, vendor2);
    }
}
