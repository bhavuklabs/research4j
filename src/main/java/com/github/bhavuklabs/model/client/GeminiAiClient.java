package com.github.bhavuklabs.model.client;

import java.time.Duration;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.service.AiServices;
import com.github.bhavuklabs.config.Research4jConfig;
import com.github.bhavuklabs.core.contracts.LLMClient;
import com.github.bhavuklabs.core.payloads.LLMResponse;
import com.github.bhavuklabs.exceptions.client.LLMClientException;
import com.github.bhavuklabs.model.config.ModelApiConfig;
import com.github.bhavuklabs.model.parser.LLMExtractor;

public class GeminiAiClient implements LLMClient, AutoCloseable {

    private static final Logger logger = Logger.getLogger(GeminiAiClient.class.getName());
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ChatModel chatModel;
    private final LLMExtractor extractor;
    private final String modelName;
    private final Duration timeout;

    public GeminiAiClient(ModelApiConfig config) throws LLMClientException {
        this(config, Duration.ofSeconds(30));
    }

    public GeminiAiClient(ModelApiConfig config, Duration timeout) throws LLMClientException {
        validateConfig(config);

        this.modelName = config.getModelName();
        this.timeout = timeout;

        try {
            this.chatModel = GoogleAiGeminiChatModel.builder()
                .modelName(config.getModelName())
                .apiKey(config.getApiKey())
                .timeout(timeout)
                .build();

            this.extractor = AiServices.create(LLMExtractor.class, chatModel);

            logger.info("GeminiAiClient initialized with model: " + modelName);

        } catch (Exception e) {
            throw new LLMClientException("Failed to initialize Gemini AI client: " + e.getMessage(), e, "GEMINI", "initialization");
        }
    }

    public GeminiAiClient(Research4jConfig config) throws LLMClientException {
        this(new ModelApiConfig(config.getGeminiApiKey(), null, config.getDefaultModel()), config.getRequestTimeout());
    }

    private void validateConfig(ModelApiConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("ModelApiConfig cannot be null");
        }
        if (config.getApiKey() == null || config.getApiKey().trim().isEmpty()) {
            throw new IllegalArgumentException("Gemini API key cannot be null or empty");
        }
        if (config.getModelName() == null || config.getModelName().trim().isEmpty()) {
            throw new IllegalArgumentException("Model name cannot be null or empty");
        }
    }

    @Override
    public <T> LLMResponse<T> complete(String prompt, Class<T> type) throws LLMClientException {
        if (prompt == null || prompt.trim().isEmpty()) {
            throw new IllegalArgumentException("Prompt cannot be null or empty");
        }
        if (type == null) {
            throw new IllegalArgumentException("Type cannot be null");
        }

        try {
            logger.fine("Processing prompt with Gemini model: " + modelName);

            String rawResponse = generateRobustResponse(prompt, type);
            T structuredOutput = parseResponseRobustly(rawResponse, type);

            return new LLMResponse<>(rawResponse, structuredOutput);

        } catch (Exception e) {
            String errorMsg = String.format("Failed to complete prompt with Gemini model %s: %s", modelName, e.getMessage());
            logger.severe(errorMsg);
            throw new LLMClientException(errorMsg, e, "GEMINI", "completion");
        }
    }

    private String generateRobustResponse(String prompt, Class<?> type) throws LLMClientException {
        try {
            
            String safePrompt = createSafePrompt(prompt, type);

            if (type == String.class) {
                return extractor.analyze(safePrompt);
            } else {
                String jsonPrompt = enhancePromptForJsonSafely(safePrompt, type);
                return extractor.extractJson(jsonPrompt);
            }
        } catch (Exception e) {
            throw new LLMClientException("Failed to generate response: " + e.getMessage(), e, "GEMINI", "generation");
        }
    }

    private String createSafePrompt(String originalPrompt, Class<?> type) {
        
        String safePrompt = originalPrompt
            .replaceAll("<[^>]*>", "")  
            .replaceAll("\\{[^}]*\\}", "")  
            .trim();

        
        StringBuilder enhancedPrompt = new StringBuilder();
        enhancedPrompt.append(safePrompt);
        enhancedPrompt.append("\n\nIMPORTANT FORMATTING INSTRUCTIONS:\n");
        enhancedPrompt.append("- Do not use any XML tags, brackets, or special markup in your response\n");
        enhancedPrompt.append("- Use plain text with clear section headers using ## or ### markdown\n");
        enhancedPrompt.append("- For code examples, use standard code blocks with triple backticks\n");
        enhancedPrompt.append("- Do not repeat any content or sections\n");
        enhancedPrompt.append("- Provide a complete, single response without duplicated elements\n");

        return enhancedPrompt.toString();
    }

    private String enhancePromptForJsonSafely(String originalPrompt, Class<?> type) {
        StringBuilder enhancedPrompt = new StringBuilder();
        enhancedPrompt.append(originalPrompt);
        enhancedPrompt.append("\n\nRESPONSE FORMAT: Respond with valid JSON format only. ");
        enhancedPrompt.append("The response should be parseable as ").append(type.getSimpleName()).append(". ");
        enhancedPrompt.append("Do not include any text outside the JSON structure. ");
        enhancedPrompt.append("Ensure all JSON properties are properly quoted and escaped. ");
        enhancedPrompt.append("Do not use markdown code blocks, XML tags, or any formatting around the JSON. ");
        enhancedPrompt.append("Provide only clean, valid JSON without any duplicated elements or malformed structure.");
        return enhancedPrompt.toString();
    }

    @SuppressWarnings("unchecked")
    private <T> T parseResponseRobustly(String rawResponse, Class<T> type) throws LLMClientException {
        if (type == String.class) {
            return (T) cleanResponseTextSafely(rawResponse);
        }

        
        String cleanedResponse = null;

        try {
            
            cleanedResponse = cleanJsonResponseSafely(rawResponse);
            JsonNode jsonNode = objectMapper.readTree(cleanedResponse);
            return objectMapper.treeToValue(jsonNode, type);

        } catch (Exception e1) {
            logger.warning("Direct JSON parsing failed: " + e1.getMessage());

            try {
                
                String extractedJson = extractJsonFromTextSafely(rawResponse);
                if (extractedJson != null && !extractedJson.trim().isEmpty()) {
                    JsonNode jsonNode = objectMapper.readTree(extractedJson);
                    return objectMapper.treeToValue(jsonNode, type);
                }
            } catch (Exception e2) {
                logger.warning("JSON extraction failed: " + e2.getMessage());
            }

            try {
                
                String fallbackJson = createIntelligentFallback(rawResponse, type);
                JsonNode jsonNode = objectMapper.readTree(fallbackJson);
                return objectMapper.treeToValue(jsonNode, type);

            } catch (Exception e3) {
                logger.warning("Fallback JSON creation failed: " + e3.getMessage());

                
                if (type == String.class) {
                    return (T) cleanResponseTextSafely(rawResponse);
                }

                
                try {
                    String simpleWrapper = "{\"content\": \"" + escapeJsonStringSafely(cleanResponseTextSafely(rawResponse)) + "\"}";
                    JsonNode jsonNode = objectMapper.readTree(simpleWrapper);
                    return objectMapper.treeToValue(jsonNode, type);
                } catch (Exception e4) {
                    throw new LLMClientException(
                        String.format("Cannot parse response to type %s after all strategies. Original error: %s",
                            type.getSimpleName(), e1.getMessage()),
                        e1, "GEMINI", "parsing");
                }
            }
        }
    }

    private String cleanJsonResponseSafely(String response) {
        if (response == null || response.trim().isEmpty()) {
            return "{}";
        }

        String cleaned = response.trim();

        
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }

        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        cleaned = cleaned.trim();

        
        if (!cleaned.startsWith("{") && !cleaned.startsWith("[")) {
            cleaned = "{\"text\": \"" + escapeJsonStringSafely(cleaned) + "\"}";
        }

        
        cleaned = removeDuplicateJsonObjects(cleaned);

        return cleaned;
    }

    private String removeDuplicateJsonObjects(String jsonText) {
        try {
            
            int firstBraceEnd = findMatchingBrace(jsonText, 0);
            if (firstBraceEnd > 0 && firstBraceEnd < jsonText.length() - 1) {
                
                String potentialDuplicate = jsonText.substring(firstBraceEnd + 1).trim();
                if (potentialDuplicate.startsWith("{")) {
                    logger.warning("Detected potential duplicate JSON object, using only the first one");
                    return jsonText.substring(0, firstBraceEnd + 1);
                }
            }
            return jsonText;
        } catch (Exception e) {
            logger.warning("Error removing duplicate JSON objects: " + e.getMessage());
            return jsonText;
        }
    }

    private int findMatchingBrace(String text, int startIndex) {
        int braceCount = 0;
        boolean inString = false;
        char prevChar = '\0';

        for (int i = startIndex; i < text.length(); i++) {
            char c = text.charAt(i);

            if (c == '"' && prevChar != '\\') {
                inString = !inString;
            } else if (!inString) {
                if (c == '{') {
                    braceCount++;
                } else if (c == '}') {
                    braceCount--;
                    if (braceCount == 0) {
                        return i;
                    }
                }
            }
            prevChar = c;
        }
        return -1;
    }

    private String extractJsonFromTextSafely(String text) {
        if (text == null) return "{}";

        try {
            
            int start = text.indexOf('{');
            if (start >= 0) {
                int end = findMatchingBrace(text, start);
                if (end > start) {
                    return text.substring(start, end + 1);
                }
            }

            
            start = text.indexOf('[');
            if (start >= 0) {
                int end = text.lastIndexOf(']');
                if (end > start) {
                    return text.substring(start, end + 1);
                }
            }

            return "{}";
        } catch (Exception e) {
            logger.warning("Error extracting JSON: " + e.getMessage());
            return "{}";
        }
    }

    private String createIntelligentFallback(String rawResponse, Class<?> type) {
        String cleanedText = cleanResponseTextSafely(rawResponse);

        
        if (type.getSimpleName().toLowerCase().contains("analysis") ||
            type.getSimpleName().toLowerCase().contains("query")) {
            return String.format("""
                {
                    "intent": "research",
                    "complexityScore": 5,
                    "topics": ["general"],
                    "requiresCitations": true,
                    "estimatedTime": "2-3 minutes",
                    "suggestedReasoning": "CHAIN_OF_THOUGHT"
                }
                """);
        }

        
        return String.format("{\"content\": \"%s\"}", escapeJsonStringSafely(cleanedText));
    }

    private String cleanResponseTextSafely(String text) {
        if (text == null) return "No response generated";

        return text
            .replaceAll("\\s+", " ")
            .replaceAll("[\\r\\n]+", " ")
            .replaceAll("<[^>]*>", "")  
            .trim();
    }

    private String escapeJsonStringSafely(String text) {
        if (text == null) return "";

        return text.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t");
    }

    public String getModelName() {
        return modelName;
    }

    public Duration getTimeout() {
        return timeout;
    }

    @Override
    public void close() {
        try {
            logger.info("GeminiAiClient closed");
        } catch (Exception e) {
            logger.warning("Error during GeminiAiClient cleanup: " + e.getMessage());
        }
    }

    public boolean isHealthy() {
        try {
            LLMResponse<String> response = complete("Hello", String.class);
            return response != null && response.rawText() != null;
        } catch (Exception e) {
            logger.warning("Health check failed for Gemini client: " + e.getMessage());
            return false;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String apiKey;
        private String modelName = "gemini-1.5-flash";
        private Duration timeout = Duration.ofSeconds(30);

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public GeminiAiClient build() throws LLMClientException {
            ModelApiConfig config = new ModelApiConfig(apiKey, null, modelName);
            return new GeminiAiClient(config, timeout);
        }
    }
}