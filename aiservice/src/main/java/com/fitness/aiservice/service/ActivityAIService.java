package com.fitness.aiservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitness.aiservice.model.Activity;
import com.fitness.aiservice.model.Recommendation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ActivityAIService {
    private final GeminiService geminiService;

    public Recommendation generateRecommendation(Activity activity) {
        String prompt = createPromptForActivity(activity);
        String aiResponse = geminiService.getAnswer(prompt);
        log.info("RESPONSE FROM AI: {} ", aiResponse);
        return processAiResponse(activity, aiResponse);
    }

    private Recommendation processAiResponse(Activity activity, String aiResponse) {
        try {
            ObjectMapper mapper = new ObjectMapper();

            if (aiResponse == null || aiResponse.isBlank()) {
                log.warn("AI response is empty for activity {}", activity == null ? "unknown" : activity.getId());
                return createDefaultRecommendation(activity);
            }

            JsonNode rootNode;
            try {
                rootNode = mapper.readTree(aiResponse);
            } catch (Exception e) {
                // If the raw response is not JSON (e.g., plain text containing JSON), attempt to locate JSON inside it
                String trimmed = aiResponse.trim();
                int start = Math.min(
                        Math.max(trimmed.indexOf('{'), 0),
                        Math.max(trimmed.indexOf('['), 0)
                );
                if (start <= 0) {
                    // fallback: return default
                    log.warn("Unable to parse aiResponse as JSON and no JSON fragment found for activity {}", activity.getId());
                    return createDefaultRecommendation(activity);
                }
                String possibleJson = trimmed.substring(start);
                try {
                    rootNode = mapper.readTree(possibleJson);
                } catch (Exception ex) {
                    log.warn("Failed to parse possible JSON fragment for activity {}: {}", activity.getId(), ex.getMessage());
                    return createDefaultRecommendation(activity);
                }
            }

            // Try multiple common response shapes to extract the text containing the JSON payload
            String jsonContent = null;

            // Shape 1: candidates -> [0] -> content -> parts -> [0] -> text
            JsonNode candidates = rootNode.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                try {
                    JsonNode firstCandidate = candidates.get(0);
                    JsonNode parts = firstCandidate.path("content").path("parts");
                    if (parts.isArray() && !parts.isEmpty()) {
                        JsonNode part0 = parts.get(0);
                        JsonNode textNode = part0.path("text");
                        if (!textNode.isMissingNode() && !textNode.isNull() && textNode.isTextual()) {
                            jsonContent = textNode.asText();
                        }
                    }
                } catch (Exception ignored) { /* continue to other shapes */ }
            }

            // Shape 2: choices -> [0] -> message -> content
            if (jsonContent == null) {
                JsonNode choices = rootNode.path("choices");
                if (choices.isArray() && !choices.isEmpty()) {
                    try {
                        JsonNode firstChoice = choices.get(0);
                        JsonNode messageContent = firstChoice.path("message").path("content");
                        if (!messageContent.isMissingNode() && !messageContent.isNull() && messageContent.isTextual()) {
                            jsonContent = messageContent.asText();
                        } else {
                            // fallback: choices[0].text
                            JsonNode textNode = firstChoice.path("text");
                            if (!textNode.isMissingNode() && !textNode.isNull() && textNode.isTextual()) {
                                jsonContent = textNode.asText();
                            }
                        }
                    } catch (Exception ignored) { /* continue to other shapes */ }
                }
            }

            // Shape 3: root itself contains the JSON object we want
            if (jsonContent == null) {
                // If rootNode contains the expected keys directly, use rootNode as the JSON content
                if (rootNode.has("analysis") || rootNode.has("improvements") || rootNode.has("suggestions")) {
                    jsonContent = rootNode.toString();
                }
            }

            // Last resort: treat raw aiResponse as jsonContent if it starts with { or [
            if (jsonContent == null) {
                String raw = aiResponse.trim();
                if (raw.startsWith("{") || raw.startsWith("[")) {
                    jsonContent = raw;
                }
            }

            if (jsonContent == null || jsonContent.isBlank()) {
                log.warn("Unable to locate JSON content in AI response for activity {}. Raw response: {}", activity.getId(), aiResponse);
                return createDefaultRecommendation(activity);
            }

            // Clean possible markdown fences and trim
            String cleaned = jsonContent
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*$", "")
                    .trim();

            JsonNode analysisJson;
            try {
                analysisJson = mapper.readTree(cleaned);
            } catch (Exception e) {
                // If cleaned is not valid JSON, log and return default
                log.warn("Cleaned AI content is not valid JSON for activity {}. Cleaned content: {}", activity.getId(), cleaned);
                return createDefaultRecommendation(activity);
            }

            JsonNode analysisNode = analysisJson.path("analysis");

            StringBuilder fullAnalysis = new StringBuilder();
            addAnalysisSection(fullAnalysis, analysisNode, "overall", "Overall:");
            addAnalysisSection(fullAnalysis, analysisNode, "pace", "Pace:");
            addAnalysisSection(fullAnalysis, analysisNode, "heartRate", "Heart Rate:");
            addAnalysisSection(fullAnalysis, analysisNode, "caloriesBurned", "Calories:");

            List<String> improvements = extractImprovements(analysisJson.path("improvements"));
            List<String> suggestions = extractSuggestions(analysisJson.path("suggestions"));
            List<String> safety = extractSafetyGuidelines(analysisJson.path("safety"));

            return Recommendation.builder()
                    .activityId(activity.getId())
                    .userId(activity.getUserId())
                    .activityType(activity.getType())
                    .recommendation(fullAnalysis.toString().trim())
                    .improvements(improvements)
                    .suggestions(suggestions)
                    .safety(safety)
                    .createdAt(LocalDateTime.now())
                    .build();

        } catch (Exception e) {
            log.error("Unexpected error while processing AI response for activity {}: {}", activity == null ? "unknown" : activity.getId(), e.getMessage(), e);
            return createDefaultRecommendation(activity);
        }
    }

    private Recommendation createDefaultRecommendation(Activity activity) {
        return Recommendation.builder()
                .activityId(activity.getId())
                .userId(activity.getUserId())
                .activityType(activity.getType())
                .recommendation("Unable to generate detailed analysis")
                .improvements(Collections.singletonList("Continue with your current routine"))
                .suggestions(Collections.singletonList("Consider consulting a fitness professional"))
                .safety(Arrays.asList(
                        "Always warm up before exercise",
                        "Stay hydrated",
                        "Listen to your body"
                ))
                .createdAt(LocalDateTime.now())
                .build();
    }

    private List<String> extractSafetyGuidelines(JsonNode safetyNode) {
        List<String> safety = new ArrayList<>();
        if (safetyNode.isArray()) {
            safetyNode.forEach(item -> safety.add(item.asText()));
        }
        return safety.isEmpty() ?
                Collections.singletonList("Follow general safety guidelines") :
                safety;
    }

    private List<String> extractSuggestions(JsonNode suggestionsNode) {
        List<String> suggestions = new ArrayList<>();
        if (suggestionsNode.isArray()) {
            suggestionsNode.forEach(suggestion -> {
                String workout = suggestion.path("workout").asText();
                String description = suggestion.path("description").asText();
                suggestions.add(String.format("%s: %s", workout, description));
            });
        }
        return suggestions.isEmpty() ?
                Collections.singletonList("No specific suggestions provided") :
                suggestions;
    }

    private List<String> extractImprovements(JsonNode improvementsNode) {
        List<String> improvements = new ArrayList<>();
        if (improvementsNode.isArray()) {
            improvementsNode.forEach(improvement -> {
                String area = improvement.path("area").asText();
                String detail = improvement.path("recommendation").asText();
                improvements.add(String.format("%s: %s", area, detail));
            });
        }
        return improvements.isEmpty() ?
                Collections.singletonList("No specific improvements provided") :
                improvements;
    }

    private void addAnalysisSection(StringBuilder fullAnalysis, JsonNode analysisNode, String key, String prefix) {
        if (analysisNode != null && !analysisNode.path(key).isMissingNode() && !analysisNode.path(key).isNull()) {
            fullAnalysis.append(prefix)
                    .append(analysisNode.path(key).asText())
                    .append("\n\n");
        }
    }

    private String createPromptForActivity(Activity activity) {
        return String.format("""
        Analyze this fitness activity and provide detailed recommendations in the following EXACT JSON format:
        {
          "analysis": {
            "overall": "Overall analysis here",
            "pace": "Pace analysis here",
            "heartRate": "Heart rate analysis here",
            "caloriesBurned": "Calories analysis here"
          },
          "improvements": [
            {
              "area": "Area name",
              "recommendation": "Detailed recommendation"
            }
          ],
          "suggestions": [
            {
              "workout": "Workout name",
              "description": "Detailed workout description"
            }
          ],
          "safety": [
            "Safety point 1",
            "Safety point 2"
          ]
        }

        Analyze this activity:
        Activity Type: %s
        Duration: %d minutes
        Calories Burned: %d
        Additional Metrics: %s
        
        Provide detailed analysis focusing on performance, improvements, next workout suggestions, and safety guidelines.
        Ensure the response follows the EXACT JSON format shown above.
        """,
                activity.getType(),
                activity.getDuration(),
                activity.getCaloriesBurned(),
                activity.getAdditionalMetrics()
        );
    }
}
