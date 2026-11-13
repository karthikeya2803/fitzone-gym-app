package com.gym.gym_management.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Value("${groq.api.key}")
    private String apiKey;

    private String cachedModel = null;

    // Automatically fetch an active chat model from your Groq account
    private String getActiveModel(HttpClient client, String cleanKey) throws Exception {
        if (cachedModel != null) return cachedModel;

        HttpRequest modelsReq = HttpRequest.newBuilder()
                .uri(URI.create("https://api.groq.com/openai/v1/models"))
                .header("Authorization", "Bearer " + cleanKey)
                .GET()
                .build();

        HttpResponse<String> modelsRes = client.send(modelsReq, HttpResponse.BodyHandlers.ofString());
        String body = modelsRes.body();

        // Find all model IDs and pick a standard chat model (skip whisper/guard/tts models)
        int idx = 0;
        while ((idx = body.indexOf("\"id\"", idx)) != -1) {
            int start = body.indexOf("\"", idx + 4) + 1;
            int end = body.indexOf("\"", start);
            String id = body.substring(start, end);
            String lower = id.toLowerCase();

            if (!lower.contains("whisper") && !lower.contains("guard") && !lower.contains("tts") && !lower.contains("prompt")) {
                cachedModel = id;
                System.out.println("AUTO-SELECTED GROQ MODEL: " + cachedModel);
                return cachedModel;
            }
            idx = end + 1;
        }
        return "llama3-8b-8192";
    }

    @PostMapping
    public ResponseEntity<?> chat(@RequestBody Map<String, String> payload) {
        String userMessage = payload.get("message");
        String safeMessage = userMessage.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
        String cleanKey = apiKey.trim();

        String systemPrompt = "You are FitBot, the friendly assistant for FitZone Gym. " +
                "Gym Hours: 6 AM to 10 PM daily. " +
                "Plans: Monthly Starter (Rs 1200/30 days), Quarterly Pro (Rs 3000/90 days), Annual Gold (Rs 9500/365 days). " +
                "Give short, helpful fitness, diet, and gym membership advice in 2-3 sentences.";

        try {
            HttpClient client = HttpClient.newHttpClient();
            String activeModel = getActiveModel(client, cleanKey);

            String jsonBody = "{"
                    + "\"model\": \"" + activeModel + "\","
                    + "\"messages\": ["
                    + "  {\"role\": \"system\", \"content\": \"" + systemPrompt + "\"},"
                    + "  {\"role\": \"user\", \"content\": \"" + safeMessage + "\"}"
                    + "]"
                    + "}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.groq.com/openai/v1/chat/completions"))
                    .header("Authorization", "Bearer " + cleanKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            int contentIdx = body.indexOf("\"content\"");
            if (response.statusCode() == 200 && contentIdx != -1) {
                int start = body.indexOf("\"", contentIdx + 9) + 1;
                int end = body.indexOf("\"", start);
                while (end > 0 && body.charAt(end - 1) == '\\') {
                    end = body.indexOf("\"", end + 1);
                }
                String reply = body.substring(start, end)
                        .replace("\\n", "\n")
                        .replace("\\\"", "\"");
                return ResponseEntity.ok(Map.of("reply", reply));
            }

            return ResponseEntity.ok(Map.of("reply", "API Error (" + response.statusCode() + "): " + body));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("reply", "Chatbot error: " + e.getMessage()));
        }
    }
}