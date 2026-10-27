package com.example.duplicate_check_service.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Service
public class TextExtractionService {

    private final Tika tika = new Tika();

    public String extractText(MultipartFile file) throws IOException, TikaException {
        log.info("Extracting text from file: {}", file.getOriginalFilename());

        String contentType = file.getContentType();
        String text = "";

        if (contentType != null && contentType.equals("application/pdf")) {
            text = extractTextFromPDF(file);
        } else if (contentType != null && contentType.startsWith("image/")) {
            text = extractTextFromImage(file);
        } else {
            text = tika.parseToString(file.getInputStream());
        }

        text = cleanText(text);

        log.debug("Extracted text length: {}", text.length());
        return text;
    }

    private String extractTextFromPDF(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream();
                PDDocument document = PDDocument.load(is)) {

            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String extractTextFromImage(MultipartFile file) throws IOException, TikaException {
        return tika.parseToString(file.getInputStream());
    }

    private String cleanText(String text) {
        if (text == null)
            return "";

        return text.replaceAll("[\\r\\n]+", " ")
                .replaceAll("\\s+", " ")
                .replaceAll("[^\\p{L}\\p{N}\\s.,$€£]", " ")
                .trim();
    }
}
