package org.aincraft.project.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.aincraft.project.BuffType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * LLM provider using Groq API for project name/description generation.
 * Uses OpenAI-compatible chat completions format.
 *
 * Configuration via environment or system properties:
 * - groq.api.key: API key (required)
 * - groq.api.model: Model name (default: llama-3.1-8b-instant)
 */
public class GroqProvider implements LLMProvider {
    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String DEFAULT_MODEL = "llama-3.1-8b-instant";

    private final OkHttpClient httpClient;
    private final String apiKey;
    private final String model;
    private final Logger logger;

    /**
     * Constructs a Groq provider with configuration.
     *
     * @param apiKey Groq API key
     * @param logger Logger for errors/debug info
     */
    public GroqProvider(String apiKey, Logger logger) {
        this(apiKey, DEFAULT_MODEL, logger);
    }

    /**
     * Constructs a Groq provider with full configuration.
     *
     * @param apiKey Groq API key
     * @param model Model name (e.g., llama-3.1-8b-instant)
     * @param logger Logger for errors/debug info
     */
    public GroqProvider(String apiKey, String model, Logger logger) {
        this.apiKey = Objects.requireNonNull(apiKey, "API key cannot be null");
        this.model = Objects.requireNonNull(model, "Model cannot be null");
        this.logger = Objects.requireNonNull(logger, "Logger cannot be null");
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    }

    @Override
    public List<ProjectText> generateBatch(BuffType buffType, int count) throws IOException {
        Objects.requireNonNull(buffType, "BuffType cannot be null");
        if (count <= 0) {
            throw new IllegalArgumentException("Count must be positive");
        }

        String prompt = buildPrompt(buffType, count);
        JsonObject requestBody = buildRequestBody(prompt);

        Request request = new Request.Builder()
            .url(GROQ_API_URL)
            .addHeader("Authorization", "Bearer " + apiKey)
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.warning("Groq API request failed: " + response.code() + " " + response.message());
                throw new IOException("Groq API returned " + response.code());
            }

            String responseBody = response.body().string();
            return parseResponse(responseBody);
        }
    }

    /**
     * Builds the system prompt for the LLM.
     * Instructs it to generate creative, thematic project names/descriptions.
     */
    private String buildPrompt(BuffType buffType, int count) {
        String buffContext = switch (buffType) {
            case GLOBAL -> "global guild-wide effects like XP multipliers, luck bonuses";
            case TERRITORY -> "location-based effects that apply in claimed guild territory like crop growth, damage reduction";
        };

        return String.format("""
            Generate %d creative project names and descriptions for a Minecraft guild system.

            Project context: Projects are collaborative guild goals that grant temporary %s.

            Requirements:
            - Each project name should be thematic, fantasy-inspired, and 2-4 words
            - Each description should be brief (1-2 sentences), explaining what the project unlocks
            - Names should feel like Minecraft adventures/quests
            - Descriptions should hint at the buff benefits

            Output format: Generate EXACTLY %d JSON objects in an array, each with "name" and "description" fields.
            Only output valid JSON array with no additional text.
            """, count, buffContext, count);
    }

    /**
     * Builds the OpenAI-compatible request body for Groq API.
     */
    private JsonObject buildRequestBody(String prompt) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", model);
        requestBody.addProperty("temperature", 0.8);
        requestBody.addProperty("max_tokens", 1024);

        JsonArray messages = new JsonArray();

        JsonObject systemMsg = new JsonObject();
        systemMsg.addProperty("role", "system");
        systemMsg.addProperty("content", "You are a creative fantasy name generator for Minecraft guild projects. Generate thematic, engaging content.");
        messages.add(systemMsg);

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", buildPrompt(BuffType.GLOBAL, 10));
        messages.add(userMsg);

        requestBody.add("messages", messages);

        return requestBody;
    }

    /**
     * Parses the Groq API response and extracts project texts.
     */
    private List<ProjectText> parseResponse(String responseJson) throws IOException {
        List<ProjectText> results = new ArrayList<>();

        try {
            JsonObject response = JsonParser.parseString(responseJson).getAsJsonObject();

            if (!response.has("choices") || response.get("choices").getAsJsonArray().size() == 0) {
                logger.warning("Groq API response missing choices array");
                return results;
            }

            JsonObject firstChoice = response.getAsJsonArray("choices").get(0).getAsJsonObject();
            String content = firstChoice.getAsJsonObject("message").get("content").getAsString();

            // Try to parse content as JSON array
            JsonArray projectArray = JsonParser.parseString(content).getAsJsonArray();

            for (int i = 0; i < projectArray.size(); i++) {
                JsonObject projectObj = projectArray.get(i).getAsJsonObject();

                if (projectObj.has("name") && projectObj.has("description")) {
                    String name = projectObj.get("name").getAsString().trim();
                    String description = projectObj.get("description").getAsString().trim();

                    if (!name.isBlank() && !description.isBlank()) {
                        results.add(new ProjectText(name, description));
                    }
                }
            }

            logger.fine("Parsed " + results.size() + " project texts from Groq API response");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to parse Groq API response", e);
            throw new IOException("Failed to parse LLM response", e);
        }

        return results;
    }
}
