package com.fitness.aiservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
public class GeminiService {

    @Value("${gemini.url}")
    private String geminiApiUrl;
    @Value("${gemini.key}")
    private String geminiApiKey;

    private final WebClient webClient;

    public GeminiService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public String getAnswer(String question) {
        // OpenAI chat completion payload (adjust model as needed)
        Map<String, Object> requestBody = Map.of(
                "model", "gpt-4o-mini",                     // change model if you want
                "messages", new Object[] {
                        Map.of("role", "user", "content", question)
                },
                "max_tokens", 800
        );

        String response = webClient.post()
                .uri(geminiApiUrl) // set gemini.api to OpenAI endpoint in application.yml
                .header("Authorization", "Bearer " + geminiApiKey) // gemini.key -> OpenAI key
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block(); // keep block() if you want same sync behavior; consider using reactive Mono in future

        return response;
    }

}
