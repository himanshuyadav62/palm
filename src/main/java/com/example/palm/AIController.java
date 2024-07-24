package com.example.palm;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


@RestController
@RequestMapping("/api")
public class AIController {

    private static final Logger logger = LoggerFactory.getLogger(AIController.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AIController(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/resume/ai/generate")
    public ResponseEntity<?> generateAiResult(@RequestParam MultipartFile file)
            throws UncheckedIOException, IllegalStateException, IOException {
        try {
            byte[] fileBytes = file.getBytes();
            String resumeData = extractTextFromPDF(fileBytes);
            if (resumeData == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error extracting text from PDF");
            }

            // Use the resume data as the prompt text
            String pdfText = new String(prompt) + resumeData;

            // Prepare request data
            String apiKey = "AIzaSyBB1v6pTddtptMJ9Beah365fP9sAqgcjtE";
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent?key="
                    + apiKey;

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");

            Map<String, Object> data = new HashMap<>();
            Map<String, Object> content = new HashMap<>();
            Map<String, Object> part = new HashMap<>();

            part.put("text", pdfText);
            content.put("parts", new Map[] { part });
            data.put("contents", new Map[] { content });

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(data, headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());

            // Extract the text content
            String responseData = null;
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && candidates.size() > 0) {
                JsonNode candidate = candidates.get(0);
                JsonNode parts = candidate.path("content").path("parts");
                if (parts.isArray() && parts.size() > 0) {
                    responseData = parts.get(0).path("text").asText();
                }
            }

            if (responseData == null) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Error extracting text from response");
            }

            responseData = responseData.substring(responseData.indexOf("{"), responseData.lastIndexOf("}") + 1);

            // Parse the extracted text content as JSON
            JsonNode jsonResponse = objectMapper.readTree(responseData);

            // Print token counts to console
            final String USAGE_METADATA = "usageMetadata";
            int promptTokenCount = root.path(USAGE_METADATA).path("promptTokenCount").asInt();
            int candidatesTokenCount = root.path(USAGE_METADATA).path("candidatesTokenCount").asInt();
            int totalTokenCount = root.path(USAGE_METADATA).path("totalTokenCount").asInt();

            logger.info("Prompt Token Count: {}", promptTokenCount);
            logger.info("Candidates Token Count: {}", candidatesTokenCount);
            logger.info("Total Token Count: {}", totalTokenCount);

            return ResponseEntity.ok(jsonResponse);
        } catch (Exception e) {
            logger.info("Error generating resume AI result: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    private String extractTextFromPDF(byte[] fileBytes) {
        try (PDDocument document = Loader.loadPDF(fileBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        } catch (IOException e) {
            logger.error("Error extracting text from PDF {}", e.getMessage());
            return null;
        }
    }

    private String prompt = """
            Please create a JSON structure from the given resume data. Follow the exact format below:

            {
              "phoneNumber": "string",
              "educationalQualification": [
                {
                  "institutionName": "string",
                  "countryOfInstitution": "string",
                  "degree": "string",
                  "yearOfGraduation": "integer"
                }
              ],
              "EmpProfessionalExperience": [
                {
                  "companyName": "string",
                  "workStartDate": "string(date)",
                  "workEndDate": "string(date)",
                  "positionTitle": "string"
                }
              ]
            }

            Notes:
            - Return only the JSON structure without any additional information.
            - If date is like October 2021, use the format "01/10/2021".
            - If any field is missing, leave it blank.
            - don't use any explanations or comments in the JSON structure.

            Here is the resume data:
            """;

}
