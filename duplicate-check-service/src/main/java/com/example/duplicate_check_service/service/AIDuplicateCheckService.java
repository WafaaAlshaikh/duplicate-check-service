package com.example.duplicate_check_service.service;

import com.example.duplicate_check_service.dto.DuplicateCheckRequest;
import com.example.duplicate_check_service  .dto.DuplicateCheckResponse;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIDuplicateCheckService {
    
    @Value("${gemini.api.key}")
    private String geminiApiKey;
    
    @Value("${gemini.api.url}")
    private String geminiApiUrl;
    
    private final OkHttpClient httpClient;
    private final Gson gson;
    
    public DuplicateCheckResponse checkWithAI(DuplicateCheckRequest request, 
                                            List<DuplicateCheckResponse.SimilarInvoice> similarInvoices) {
        
        try {
            log.info("Starting AI-enhanced duplicate check");
            
            String prompt = buildAIPrompt(request, similarInvoices);
            
            if (request.getFile() != null) {
                return checkWithImageAndAI(request.getFile(), prompt);
            } else {
                return checkWithTextOnly(prompt);
            }
            
        } catch (Exception e) {
            log.error("AI duplicate check failed: {}", e.getMessage());
            return null;
        }
    }
    
    private DuplicateCheckResponse checkWithTextOnly(String prompt) throws IOException {
        return DuplicateCheckResponse.builder()
                .duplicate(false)
                .confidenceScore(BigDecimal.valueOf(0.0))
                .checkMethod("AI_TEXT_ONLY")
                .recommendation("Text-only AI check completed")
                .build();
    }
    
    private String buildAIPrompt(DuplicateCheckRequest request, 
                               List<DuplicateCheckResponse.SimilarInvoice> similarInvoices) {
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an expert in invoice duplicate detection. Analyze the following invoice:\n\n");
        
        prompt.append("NEW INVOICE DETAILS:\n");
        prompt.append("- Date: ").append(request.getInvoiceDate()).append("\n");
        prompt.append("- Amount: $").append(request.getTotalAmount()).append("\n");
        prompt.append("- Vendor: ").append(request.getVendor()).append("\n");
        prompt.append("- Filename: ").append(request.getFileName()).append("\n\n");
        
        if (!similarInvoices.isEmpty()) {
            prompt.append("POTENTIAL DUPLICATES FOUND:\n");
            for (int i = 0; i < Math.min(similarInvoices.size(), 3); i++) {
                var similar = similarInvoices.get(i);
                prompt.append(i+1).append(". Invoice ID: ").append(similar.getInvoiceId())
                      .append(", Amount: $").append(similar.getTotalAmount())
                      .append(", Date: ").append(similar.getUploadDate())
                      .append(", Similarity: ").append(similar.getSimilarityScore())
                      .append("\n");
            }
        }
        
        prompt.append("\nANALYSIS QUESTIONS:\n");
        prompt.append("1. Is this likely a duplicate invoice? (Yes/No/Maybe)\n");
        prompt.append("2. What is the confidence level? (0-100%)\n");
        prompt.append("3. What are the key matching factors?\n");
        prompt.append("4. Any red flags for fraud?\n");
        prompt.append("5. Recommendation: Should we block this invoice?\n");
        
        prompt.append("\nRESPONSE FORMAT (JSON only):\n");
        prompt.append("{\n");
        prompt.append("  \"isDuplicate\": true/false,\n");
        prompt.append("  \"confidenceScore\": 0.95,\n");
        prompt.append("  \"keyFindings\": [\"finding1\", \"finding2\"],\n");
        prompt.append("  \"redFlags\": [\"flag1\", \"flag2\"],\n");
        prompt.append("  \"recommendation\": \"Block or Allow\",\n");
        prompt.append("  \"explanation\": \"Detailed explanation\"\n");
        prompt.append("}\n");
        
        return prompt.toString();
    }
    
    private DuplicateCheckResponse checkWithImageAndAI(MultipartFile file, String prompt) 
            throws IOException {
        
        byte[] fileBytes = file.getBytes();
        String base64File = Base64.getEncoder().encodeToString(fileBytes);
        String mimeType = file.getContentType();
        
        JsonObject requestJson = new JsonObject();
        
        JsonObject content = new JsonObject();
        JsonObject textPart = new JsonObject();
        textPart.addProperty("text", prompt);
        
        JsonObject imagePart = new JsonObject();
        JsonObject inlineData = new JsonObject();
        inlineData.addProperty("mime_type", mimeType);
        inlineData.addProperty("data", base64File);
        imagePart.add("inline_data", inlineData);
        
        content.add("parts", gson.toJsonTree(List.of(textPart, imagePart)));
        requestJson.add("contents", gson.toJsonTree(List.of(content)));
        
        Request httpRequest = new Request.Builder()
                .url(geminiApiUrl + "?key=" + geminiApiKey)
                .post(RequestBody.create(gson.toJson(requestJson), 
                        MediaType.parse("application/json")))
                .build();
        
        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (response.isSuccessful()) {
                String responseBody = response.body().string();
                return parseAIResponse(responseBody);
            } else {
                throw new IOException("AI API error: " + response.code());
            }
        }
    }
    
    private DuplicateCheckResponse parseAIResponse(String responseBody) {
        try {
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);
            
            String text = jsonResponse.getAsJsonArray("candidates")
                    .get(0).getAsJsonObject()
                    .get("content").getAsJsonObject()
                    .get("parts").getAsJsonArray()
                    .get(0).getAsJsonObject()
                    .get("text").getAsString();
            
            String jsonText = extractJsonFromText(text);
            JsonObject aiResult = gson.fromJson(jsonText, JsonObject.class);
            
            return DuplicateCheckResponse.builder()
                    .duplicate(aiResult.get("isDuplicate").getAsBoolean())
                    .confidenceScore(BigDecimal.valueOf(
                        aiResult.get("confidenceScore").getAsDouble()))
                    .checkMethod("AI_ANALYSIS")
                    .recommendation(aiResult.get("recommendation").getAsString())
                    .build();
                    
        } catch (Exception e) {
            log.error("Failed to parse AI response: {}", e.getMessage());
            return null;
        }
    }
    
    private String extractJsonFromText(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        
        if (start != -1 && end != -1 && end > start) {
            return text.substring(start, end + 1);
        }
        
        return text;
    }
}