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
 * LLM provider using HuggingFace Inference API for project name/description generation.
 *
 * Configuration via environment or system properties:
 * - huggingface.api.key: API key (required)
 * - huggingface.api.model: Model name (default: microsoft/Phi-3-mini-4k-instruct)
 */
public class HuggingFaceProvider implements LLMProvider {
    private static final String HUGGINGFACE_API_BASE = "https://api-inference.huggingface.co/models";
    private static final String DEFAULT_MODEL = "microsoft/Phi-3-mini-4k-instruct";

    private final OkHttpClient httpClient;
    private final String apiKey;
    private final String model;
    private final Logger logger;

    /**
     * Constructs a HuggingFace provider with configuration.
     *
     * @param apiKey HuggingFace API key
     * @param logger Logger for errors/debug info
     */
    public HuggingFaceProvider(String apiKey, Logger logger) {
        this(apiKey, DEFAULT_MODEL, logger);
    }

    /**
     * Constructs a HuggingFace provider with full configuration.
     *
     * @param apiKey HuggingFace API key
     * @param model Model name (e.g., microsoft/Phi-3-mini-4k-instruct)
     * @param logger Logger for errors/debug info
     */
    public HuggingFaceProvider(String apiKey, String model, Logger logger) {
        this.apiKey = Objects.requireNonNull(apiKey, "API key cannot be null");
        this.model = Objects.requireNonNull(model, "Model cannot be null");
        this.logger = Objects.requireNonNull(logger, "Logger cannot be null");
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
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
        String url = HUGGINGFACE_API_BASE + "/" + model;

        Request request = new Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer " + apiKey)
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                logger.warning("HuggingFace API request failed: " + response.code() + " " + response.message());
                throw new IOException("HuggingFace API returned " + response.code());
            }

            String responseBody = response.body().string();
            return parseResponse(responseBody);
        }
    }

    /**
     * Builds the prompt for the LLM.
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
     * Builds the request body for HuggingFace Inference API.
     */
    private JsonObject buildRequestBody(String prompt) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("inputs", prompt);

        JsonObject params = new JsonObject();
        params.addProperty("max_new_tokens", 1024);
        params.addProperty("temperature", 0.8);

        requestBody.add("parameters", params);

        return requestBody;
    }

    /**
     * Parses the HuggingFace API response and extracts project texts.
     */
    private List<ProjectText> parseResponse(String responseJson) throws IOException {
        List<ProjectText> results = new ArrayList<>();

        try {
            // HuggingFace returns an array with one object containing generated_text
            JsonArray responseArray = JsonParser.parseString(responseJson).getAsJsonArray();

            if (responseArray.size() == 0) {
                logger.warning("HuggingFace API response is empty");
                return results;
            }

            String generatedText = responseArray.get(0).getAsJsonObject().get("generated_text").getAsString();

            // Extract JSON array from the generated text
            // The model may include the prompt, so we search for the opening bracket
            int jsonStart = generatedText.indexOf('[');
            if (jsonStart == -1) {
                logger.warning("No JSON array found in HuggingFace response");
                return results;
            }

            String jsonPart = generatedText.substring(jsonStart);
            int jsonEnd = jsonPart.lastIndexOf(']');
            if (jsonEnd == -1) {
                logger.warning("Invalid JSON array in HuggingFace response");
                return results;
            }

            jsonPart = jsonPart.substring(0, jsonEnd + 1);
            JsonArray projectArray = JsonParser.parseString(jsonPart).getAsJsonArray();

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

            logger.fine("Parsed " + results.size() + " project texts from HuggingFace API response");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to parse HuggingFace API response", e);
            throw new IOException("Failed to parse LLM response", e);
        }

        return results;
    }
}
