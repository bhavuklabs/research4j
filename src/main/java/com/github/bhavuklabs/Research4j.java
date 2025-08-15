package com.github.bhavuklabs;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import com.github.bhavuklabs.agent.ResearchResult;
import com.github.bhavuklabs.agent.ResearchSession;
import com.github.bhavuklabs.citation.config.CitationConfig;
import com.github.bhavuklabs.citation.enums.CitationSource;
import com.github.bhavuklabs.citation.service.CitationService;
import com.github.bhavuklabs.config.Research4jConfig;
import com.github.bhavuklabs.core.contracts.LLMClient;
import com.github.bhavuklabs.core.enums.GraphEngineType;
import com.github.bhavuklabs.core.enums.ModelType;
import com.github.bhavuklabs.core.enums.OutputFormat;
import com.github.bhavuklabs.core.enums.ReasoningMethod;
import com.github.bhavuklabs.core.payloads.ResearchPromptConfig;
import com.github.bhavuklabs.deepresearch.engine.DeepResearchEngine;
import com.github.bhavuklabs.deepresearch.models.DeepResearchConfig;
import com.github.bhavuklabs.deepresearch.models.DeepResearchProgress;
import com.github.bhavuklabs.deepresearch.models.DeepResearchResult;
import com.github.bhavuklabs.exceptions.citation.CitationException;
import com.github.bhavuklabs.exceptions.client.LLMClientException;
import com.github.bhavuklabs.exceptions.config.ConfigurationException;
import com.github.bhavuklabs.model.client.GeminiAiClient;
import com.github.bhavuklabs.model.client.OpenAiClient;
import com.github.bhavuklabs.pipeline.executor.GraphExecutor;
import com.github.bhavuklabs.pipeline.executor.GraphExecutorFactory;
import com.github.bhavuklabs.pipeline.profile.UserProfile;
import com.github.bhavuklabs.reasoning.engine.ReasoningEngine;

public class Research4j implements AutoCloseable {

    private static final Logger logger = Logger.getLogger(Research4j.class.getName());

    private final Research4jConfig config;
    private final LLMClient llmClient;
    private final CitationService citationService;
    private final ReasoningEngine reasoningEngine;

    private final GraphExecutor graphExecutor;

    private final DeepResearchEngine deepResearchEngine;
    private final boolean deepResearchEnabled;

    private Research4j(Builder builder) throws ConfigurationException {
        try {
            this.config = builder.configBuilder.build();
            this.llmClient = createLLMClient();
            this.citationService = createCitationService();
            this.reasoningEngine = new ReasoningEngine(llmClient);

            this.graphExecutor = GraphExecutorFactory.create(config, citationService, reasoningEngine, llmClient);

            this.deepResearchEngine = new DeepResearchEngine(llmClient, citationService);
            this.deepResearchEnabled = builder.deepResearchEnabled;

            logger.info("Research4j initialized successfully with Deep Research capabilities");
            logger.info("Graph Engine: " + graphExecutor.getExecutorType());
            logger.info("LLM Provider: " + (config.hasApiKey(ModelType.GEMINI) ? "Gemini" : "OpenAI"));
            logger.info("Deep Research: " + (deepResearchEnabled ? "ENABLED" : "DISABLED"));

        } catch (Exception e) {
            throw new ConfigurationException("Failed to initialize Research4j: " + e.getMessage(), e);
        }
    }

    public ResearchResult research(String query) {
        validateQuery(query);
        return research(query, createDefaultUserProfile());
    }

    public ResearchResult research(String query, UserProfile userProfile) {
        validateQuery(query);
        return research(query, userProfile, config.getDefaultOutputFormat());
    }

    public ResearchResult research(String query, OutputFormat outputFormat) {
        validateQuery(query);
        return research(query, createDefaultUserProfile(), outputFormat);
    }

    public ResearchResult research(String query, UserProfile userProfile, OutputFormat outputFormat) {
        validateQuery(query);
        validateUserProfile(userProfile);
        validateOutputFormat(outputFormat);

        try {
            logger.info("Starting research using " + graphExecutor.getExecutorType() + " engine for query: " + truncateQuery(query));

            var promptConfig = new ResearchPromptConfig(query, buildSystemInstruction(userProfile, outputFormat), determineOutputType(outputFormat),
                outputFormat);

            var result = graphExecutor.processQuery(generateSessionId(), query, userProfile, promptConfig)
                .get();

            logger.info("Research completed successfully in " + result.getProcessingTime());
            return new ResearchResult(result, config);

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.severe("Research interrupted for query: " + truncateQuery(query) + " - " + ie.getMessage());
            throw new IllegalStateException("Research processing was interrupted", ie);
        } catch (java.util.concurrent.ExecutionException ee) {
            logger.severe("Research failed for query: " + truncateQuery(query) + " - " + ee.getCause());
            throw new IllegalStateException("Research processing failed: " + ee.getCause(), ee.getCause());
        }
    }

    public CompletableFuture<DeepResearchResult> deepResearch(String query) {
        return deepResearch(query, createDefaultUserProfile(), DeepResearchConfig.comprehensiveConfig());
    }

    public CompletableFuture<DeepResearchResult> deepResearch(String query, UserProfile userProfile) {
        return deepResearch(query, userProfile, DeepResearchConfig.comprehensiveConfig());
    }

    public CompletableFuture<DeepResearchResult> deepResearch(String query, DeepResearchConfig deepConfig) {
        return deepResearch(query, createDefaultUserProfile(), deepConfig);
    }

    public CompletableFuture<DeepResearchResult> deepResearch(String query, UserProfile userProfile, DeepResearchConfig deepConfig) {

        validateQuery(query);
        validateUserProfile(userProfile);

        if (!deepResearchEnabled) {
            logger.warning("Deep Research not available");
            return CompletableFuture.failedFuture(new RuntimeException("Deep Research not available"));
        }

        if (deepConfig == null) {
            deepConfig = DeepResearchConfig.comprehensiveConfig();
        }

        logger.info("Starting Deep Research for query: " + truncateQuery(query));
        logger.info("Configuration: " + deepConfig.getResearchDepth() + " depth, " + deepConfig.getMaxSources() + " max sources");

        return deepResearchEngine.executeDeepResearch(query, deepConfig);
    }

    public CompletableFuture<DeepResearchResult> comprehensiveResearch(String query) {
        UserProfile comprehensiveProfile = new UserProfile("comprehensive-researcher", "multi-disciplinary", "expert",
            List.of("comprehensive", "detailed", "multi-perspective"), Map.of("research methodology", 9, "synthesis", 9, "analysis", 8), List.of(),
            OutputFormat.MARKDOWN);

        DeepResearchConfig comprehensiveConfig = DeepResearchConfig.builder()
            .researchDepth(DeepResearchConfig.ResearchDepth.EXPERT)
            .maxProcessingTime(Duration.ofMinutes(15))
            .maxSources(80)
            .maxQuestions(20)
            .enableCrossValidation(true)
            .enableDeepDiveMode(true)
            .enableIterativeRefinement(true)
            .build();

        return deepResearch(query, comprehensiveProfile, comprehensiveConfig);
    }

    public CompletableFuture<DeepResearchResult> technicalResearch(String query) {
        UserProfile techProfile = new UserProfile("tech-researcher", "software-engineering", "expert",
            List.of("technical", "implementation", "code-heavy", "architectural"),
            Map.of("software architecture", 9, "implementation", 9, "best practices", 8, "performance", 8), List.of(), OutputFormat.MARKDOWN);

        DeepResearchConfig techConfig = DeepResearchConfig.builder()
            .researchDepth(DeepResearchConfig.ResearchDepth.COMPREHENSIVE)
            .maxProcessingTime(Duration.ofMinutes(12))
            .maxSources(60)
            .maxQuestions(15)
            .preferredDomains(List.of("implementation", "architecture", "performance", "security", "best-practices"))
            .enableCrossValidation(true)
            .build();

        return deepResearch(query, techProfile, techConfig);
    }

    public CompletableFuture<DeepResearchResult> narrativeFocusedResearch(String query) {
        DeepResearchConfig narrativeConfig = DeepResearchConfig.builder()
            .researchDepth(DeepResearchConfig.ResearchDepth.COMPREHENSIVE)
            .maxProcessingTime(Duration.ofMinutes(10))
            .maxSources(50)
            .maxQuestions(12)
            .outputFormat(OutputFormat.MARKDOWN)
            .build();

        return deepResearch(query, createDefaultUserProfile(), narrativeConfig);
    }

    public DeepResearchProgress getDeepResearchProgress(String sessionId) {
        validateResearchSessionId(sessionId);
        if (deepResearchEngine != null) {
            return deepResearchEngine.getSessionProgress(sessionId);
        }
        throw new IllegalStateException("Deep research is not enabled");
    }

    public Map<String, DeepResearchProgress> getAllActiveDeepResearch() {
        if (deepResearchEngine != null) {
            return deepResearchEngine.getAllActiveProgress();
        }
        return Map.of();
    }

    public boolean cancelDeepResearch(String sessionId) {
        validateResearchSessionId(sessionId);
        if (deepResearchEngine != null) {
            return deepResearchEngine.cancelSession(sessionId);
        }
        return false;
    }

    public Object getMemoryManager() {
        if (deepResearchEngine != null) {
            return deepResearchEngine.getMemoryManager();
        }
        throw new IllegalStateException("Deep research is not enabled");
    }    public ResearchQualityReport validateResearchQuality(DeepResearchResult result) {
        ResearchQualityValidator validator = new ResearchQualityValidator();
        return validator.validateComprehensiveQuality(result);
    }

    public ResearchSession createSession() {
        return new ResearchSession(this, generateSessionId());
    }

    public ResearchSession createSession(UserProfile userProfile) {
        validateUserProfile(userProfile);
        return new ResearchSession(this, generateSessionId(), userProfile);
    }

    public EnhancedResearchSession createEnhancedSession() {
        return new EnhancedResearchSession(this, generateSessionId());
    }

    public EnhancedResearchSession createEnhancedSession(UserProfile userProfile) {
        validateUserProfile(userProfile);
        return new EnhancedResearchSession(this, generateSessionId(), userProfile);
    }

    public static Research4j createForSoftwareDevelopment(String geminiKey, String tavilyKey) throws ConfigurationException {
        return builder().withGemini(geminiKey, "gemini-2.0-flash")
            .withTavily(tavilyKey)
            .defaultReasoning(ReasoningMethod.CHAIN_OF_THOUGHT)
            .defaultOutputFormat(OutputFormat.MARKDOWN)
            .maxCitations(25)
            .timeout(Duration.ofMinutes(5))
            .enableDeepResearch()
            .build();
    }

    public static Research4j createForComprehensiveResearch(String geminiKey, String searchKey, String cseId) throws ConfigurationException {
        return builder().withGemini(geminiKey, "gemini-1.5-pro")
            .withGoogleSearch(searchKey, cseId)
            .defaultReasoning(ReasoningMethod.CHAIN_OF_THOUGHT)
            .maxCitations(30)
            .timeout(Duration.ofMinutes(8))
            .enableDeepResearch()
            .enableDebug()
            .build();
    }

    public static Research4j createForNarrativeResearch(String openaiKey, String tavilyKey) throws ConfigurationException {
        return builder().withOpenAI(openaiKey, "gpt-4")
            .withTavily(tavilyKey)
            .defaultReasoning(ReasoningMethod.CHAIN_OF_THOUGHT)
            .defaultOutputFormat(OutputFormat.MARKDOWN)
            .maxCitations(20)
            .timeout(Duration.ofMinutes(6))
            .enableDeepResearch()
            .build();
    }

    public boolean isHealthy() {
        try {
            boolean llmHealthy = llmClient instanceof GeminiAiClient ? ((GeminiAiClient) llmClient).isHealthy() :
                llmClient instanceof OpenAiClient ? ((OpenAiClient) llmClient).isHealthy() : true;

            boolean graphExecutorHealthy = graphExecutor.isHealthy();
            boolean deepResearchHealthy = deepResearchEngine != null && deepResearchEngine.isHealthy();
            return llmHealthy && graphExecutorHealthy && deepResearchHealthy;
        } catch (Exception e) {
            logger.warning("Health check failed: " + e.getMessage());
            return false;
        }
    }

    public Research4jConfig getConfig() {
        return config;
    }

    public String getGraphEngineType() {
        return graphExecutor.getExecutorType();
    }

    public ResearchCapabilities getCapabilities() {
        return new ResearchCapabilities(true, true, deepResearchEnabled, config.hasApiKey(ModelType.GEMINI), config.hasApiKey(ModelType.OPENAI),
            config.getDefaultCitationSource());
    }

    public static class Builder {

        private final Research4jConfig.Builder configBuilder = Research4jConfig.builder();
        private boolean deepResearchEnabled = false;

        private Builder() {
        }

        public Builder withGraphEngine(GraphEngineType engineType) {
            configBuilder.withGraphEngine(engineType);
            return this;
        }

        public Builder enableLangGraph4j() {
            return withGraphEngine(GraphEngineType.LANGGRAPH4J);
        }

        public Builder withOpenAI(String apiKey) {
            configBuilder.openAiApiKey(apiKey);
            return this;
        }

        public Builder withGemini(String apiKey) {
            configBuilder.geminiApiKey(apiKey);
            return this;
        }

        public Builder withGemini(String apiKey, String modelName) {
            configBuilder.geminiApiKey(apiKey)
                .defaultModel(modelName);
            return this;
        }

        public Builder withOpenAI(String apiKey, String modelName) {
            configBuilder.openAiApiKey(apiKey)
                .defaultModel(modelName);
            return this;
        }

        public Builder withTavily(String apiKey) {
            configBuilder.tavilyApiKey(apiKey)
                .defaultCitationSource(CitationSource.TAVILY);
            return this;
        }

        public Builder withGoogleSearch(String searchApiKey, String cseId) {
            configBuilder.googleSearchApiKey(searchApiKey)
                .googleCseId(cseId)
                .defaultCitationSource(CitationSource.GOOGLE_GEMINI);
            return this;
        }

        public Builder defaultReasoning(ReasoningMethod method) {
            configBuilder.defaultReasoningMethod(method);
            return this;
        }

        public Builder defaultOutputFormat(OutputFormat format) {
            configBuilder.defaultOutputFormat(format);
            return this;
        }

        public Builder timeout(Duration timeout) {
            configBuilder.requestTimeout(timeout);
            return this;
        }

        public Builder maxCitations(int maxCitations) {
            configBuilder.maxCitations(maxCitations);
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            configBuilder.maxRetries(maxRetries);
            return this;
        }

        public Builder enableDebug() {
            configBuilder.debugEnabled(true);
            return this;
        }

        public Builder disableCache() {
            configBuilder.cacheEnabled(false);
            return this;
        }

        public Builder enableDeepResearch() {
            this.deepResearchEnabled = true;
            return this;
        }

        public Builder config(String key, Object value) {
            configBuilder.property(key, value);
            return this;
        }

        public Builder withGeminiApiKey(String apiKey) {
            return withGemini(apiKey);
        }

        public Builder withOpenAiApiKey(String apiKey) {
            return withOpenAI(apiKey);
        }

        public Builder withTavilyApiKey(String apiKey) {
            return withTavily(apiKey);
        }

        public Builder withGoogleSearchConfig(String apiKey, String cseId) {
            return withGoogleSearch(apiKey, cseId);
        }

        public Builder withDefaultOutputFormat(OutputFormat format) {
            return defaultOutputFormat(format);
        }

        public Builder withDefaultCitationSource(CitationSource source) {
            configBuilder.defaultCitationSource(source);
            return this;
        }

        public Research4j build() throws ConfigurationException {
            return new Research4j(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    private String buildSystemInstruction(UserProfile userProfile, OutputFormat outputFormat) {
        StringBuilder instruction = new StringBuilder();

        instruction.append("You are an elite AI research assistant with expertise across multiple domains. ");
        instruction.append("Your primary objective is to provide comprehensive, accurate, and well-researched answers ");
        instruction.append("that demonstrate deep understanding and critical analysis.\n\n");

        if (userProfile != null) {
            instruction.append("USER PROFILE CONTEXT:\n");
            instruction.append("- Domain: ")
                .append(userProfile.getDomain())
                .append("\n");
            instruction.append("- Expertise Level: ")
                .append(userProfile.getExpertiseLevel())
                .append("\n");
            instruction.append("- Preferences: ")
                .append(String.join(", ", userProfile.getPreferences()))
                .append("\n\n");
        }

        instruction.append("OUTPUT FORMAT REQUIREMENTS:\n");
        switch (outputFormat) {
            case MARKDOWN -> instruction.append(
                "- Use clear Markdown formatting with proper headers, lists, and emphasis\n" + "- Structure content with logical sections and subsections\n" +
                    "- Include code blocks for technical examples when relevant\n");
            case JSON -> instruction.append("- Provide response in valid JSON format only\n" + "- Structure data logically with appropriate nesting\n" +
                "- Ensure all JSON is properly escaped and parseable\n");
            case TABLE -> instruction.append("- Present information in well-structured table format\n" + "- Use clear headers and logical row organization\n" +
                "- Include summary sections where appropriate\n");
        }

        instruction.append("\nRESEARCH METHODOLOGY:\n");
        instruction.append("1. Analyze the query thoroughly to understand all implicit and explicit requirements\n");
        instruction.append("2. Synthesize information from provided citations with your knowledge base\n");
        instruction.append("3. Apply critical thinking to evaluate source reliability and information quality\n");
        instruction.append("4. Present findings with logical progression and clear reasoning\n");
        instruction.append("5. Acknowledge limitations or uncertainties when present\n");
        instruction.append("6. Prioritize accuracy over speculation while maintaining comprehensiveness\n\n");

        instruction.append("QUALITY STANDARDS:\n");
        instruction.append("- Ensure factual accuracy and cite sources appropriately\n");
        instruction.append("- Maintain professional tone while being accessible\n");
        instruction.append("- Provide sufficient detail without overwhelming the reader\n");
        instruction.append("- Use examples and analogies to clarify complex concepts\n");
        instruction.append("- Structure responses for optimal readability and comprehension\n");

        if (deepResearchEnabled) {
            instruction.append("\nDEEP RESEARCH REQUIREMENTS:\n");
            instruction.append("- Include complete, runnable code examples with dependencies\n");
            instruction.append("- Provide all necessary imports and configuration files\n");
            instruction.append("- Show unit tests and integration examples\n");
            instruction.append("- Demonstrate real-world usage patterns\n");
            instruction.append("- Focus on implementation details and specific examples\n");
            instruction.append("- Avoid generic statements - be specific and actionable\n\n");
        }

        return instruction.toString();
    }

    private Class<?> determineOutputType(OutputFormat format) {
        return switch (format) {
            case JSON -> Map.class;
            case TABLE -> List.class;
            default -> String.class;
        };
    }

    private UserProfile createDefaultUserProfile() {
        return new UserProfile("default-user", "general", "intermediate", List.of("balanced", "comprehensive", "detailed"),
            Map.of("general knowledge", 7, "analysis", 8, "implementation", 7), List.of(), config.getDefaultOutputFormat());
    }

    private String generateSessionId() {
        return "research-session-" + System.currentTimeMillis() + "-" + Integer.toHexString((int) (Math.random() * 0x10000));
    }

    private LLMClient createLLMClient() throws ConfigurationException, LLMClientException {
        if (config.hasApiKey(ModelType.GEMINI)) {
            logger.info("Initializing Gemini AI client with model: " + config.getDefaultModel());
            return new GeminiAiClient(config);
        } else if (config.hasApiKey(ModelType.OPENAI)) {
            logger.info("Initializing OpenAI client with model: " + config.getDefaultModel());
            return new OpenAiClient(config);
        } else {
            throw new ConfigurationException("No LLM provider configured. Please set either GEMINI_API_KEY or OPENAI_API_KEY environment variable, " +
                "or configure them programmatically using the builder pattern.");
        }
    }

    private CitationService createCitationService() throws ConfigurationException, CitationException {
        CitationSource source = config.getDefaultCitationSource();
        CitationConfig citationConfig;

        switch (source) {
            case GOOGLE_GEMINI -> {
                validateGoogleSearchConfig();
                citationConfig = new CitationConfig(source, config.getGoogleSearchApiKey());
                return new CitationService(citationConfig, config.getGoogleCseId());
            }
            case TAVILY -> {
                if (!config.hasApiKey(CitationSource.TAVILY)) {
                    throw new ConfigurationException(
                        "Tavily API key required for Tavily citation source. " + "Set TAVILY_API_KEY environment variable or configure via builder.");
                }
                citationConfig = new CitationConfig(source, config.getTavilyApiKey());
                return new CitationService(citationConfig);
            }
            default -> throw new ConfigurationException("Unsupported citation source: " + source);
        }
    }

    private void validateGoogleSearchConfig() throws ConfigurationException {
        if (!config.hasApiKey(CitationSource.GOOGLE_GEMINI)) {
            throw new ConfigurationException(
                "Google Search API key required for Google citation source. " + "Set GOOGLE_SEARCH_API_KEY environment variable or configure via builder.");
        }
        if (config.getGoogleCseId() == null || config.getGoogleCseId()
            .trim()
            .isEmpty()) {
            throw new ConfigurationException(
                "Google Custom Search Engine ID required for Google citation source. " + "Set GOOGLE_CSE_ID environment variable or configure via builder.");
        }
    }

    private void validateQuery(String query) {
        if (query == null || query.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("Research query cannot be null or empty");
        }
        if (query.length() > 10000) {
            throw new IllegalArgumentException("Research query exceeds maximum length of 10,000 characters");
        }
    }

    private void validateUserProfile(UserProfile userProfile) {
        if (userProfile == null) {
            throw new IllegalArgumentException("User profile cannot be null");
        }
    }

    private void validateOutputFormat(OutputFormat outputFormat) {
        if (outputFormat == null) {
            throw new IllegalArgumentException("Output format cannot be null");
        }
    }

    private void validateResearchSessionId(String sessionId) {
        if (sessionId == null || sessionId.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("Research session ID cannot be null or empty");
        }
    }

    private String truncateQuery(String query) {
        if (query == null) {
            return "null";
        }
        return query.length() > 100 ? query.substring(0, 100) + "..." : query;
    }

    @Override
    public void close() {
        try {
            logger.info("Shutting down Research4j");

            if (graphExecutor != null) {
                graphExecutor.shutdown();
            }
            if (reasoningEngine != null) {
                reasoningEngine.shutdown();
            }
            if (deepResearchEngine != null) {
                deepResearchEngine.shutdown();
            }
            if (llmClient instanceof AutoCloseable) {
                ((AutoCloseable) llmClient).close();
            }

            logger.info("Research4j closed successfully");
        } catch (Exception e) {
            logger.warning("Error during shutdown: " + e.getMessage());
        }
    }

    public static class ResearchCapabilities {

        private final boolean standardResearch;
        private final boolean deepResearch;
        private final boolean enhancedDeepResearch;
        private final boolean geminiSupport;
        private final boolean openaiSupport;
        private final CitationSource defaultCitationSource;

        public ResearchCapabilities(boolean standardResearch, boolean deepResearch, boolean enhancedDeepResearch, boolean geminiSupport, boolean openaiSupport,
            CitationSource defaultCitationSource) {
            this.standardResearch = standardResearch;
            this.deepResearch = deepResearch;
            this.enhancedDeepResearch = enhancedDeepResearch;
            this.geminiSupport = geminiSupport;
            this.openaiSupport = openaiSupport;
            this.defaultCitationSource = defaultCitationSource;
        }

        public boolean isStandardResearch() {
            return standardResearch;
        }

        public boolean isDeepResearch() {
            return deepResearch;
        }

        public boolean isEnhancedDeepResearch() {
            return enhancedDeepResearch;
        }

        public boolean isGeminiSupport() {
            return geminiSupport;
        }

        public boolean isOpenaiSupport() {
            return openaiSupport;
        }

        public CitationSource getDefaultCitationSource() {
            return defaultCitationSource;
        }
    }

    public static class ResearchQualityValidator {

        public ResearchQualityReport validateComprehensiveQuality(DeepResearchResult result) {
            ResearchQualityReport report = new ResearchQualityReport();

            report.addCheck("narrative_length", result.getNarrative()
                .length() >= 3000, "Report length: " + result.getNarrative()
                .length() + " characters");

            report.addCheck("citation_coverage", result.getResults()
                .getAllCitations()
                .size() >= 15, "Citations: " + result.getResults()
                .getAllCitations()
                .size() + " sources");

            report.addCheck("specificity", hasSpecificExamples(result.getNarrative()), "Contains specific examples and implementation details");

            report.addCheck("coherence", hasLogicalFlow(result.getNarrative()), "Logical flow and narrative coherence");

            return report;
        }

        private boolean hasSpecificExamples(String content) {
            String[] indicators = { "example", "implementation", "case study", "specific", "detailed" };
            String contentLower = content.toLowerCase();

            return java.util.Arrays.stream(indicators)
                .anyMatch(contentLower::contains);
        }

        private boolean hasLogicalFlow(String content) {
            return content.contains("##") && content.contains("###") && content.length() > 2000;
        }
    }

    public static class ResearchQualityReport {

        private final Map<String, QualityCheck> checks = new java.util.HashMap<>();

        public void addCheck(String name, boolean passed, String description) {
            checks.put(name, new QualityCheck(name, passed, description));
        }

        public boolean allChecksPassed() {
            return checks.values()
                .stream()
                .allMatch(QualityCheck::isPassed);
        }

        public Map<String, QualityCheck> getChecks() {
            return checks;
        }

        public static class QualityCheck {

            private final String name;
            private final boolean passed;
            private final String description;

            public QualityCheck(String name, boolean passed, String description) {
                this.name = name;
                this.passed = passed;
                this.description = description;
            }

            public String getName() {
                return name;
            }

            public boolean isPassed() {
                return passed;
            }

            public String getDescription() {
                return description;
            }
        }
    }

    public static class EnhancedResearchSession extends ResearchSession {

        private final Research4j research4j;

        public EnhancedResearchSession(Research4j research4j, String sessionId) {
            super(research4j, sessionId);
            this.research4j = research4j;
        }

        public EnhancedResearchSession(Research4j research4j, String sessionId, UserProfile userProfile) {
            super(research4j, sessionId, userProfile);
            this.research4j = research4j;
        }

        public CompletableFuture<DeepResearchResult> deepResearch(String query) {
            return research4j.deepResearch(query, getUserProfile());
        }

        public CompletableFuture<DeepResearchResult> deepResearch(String query, DeepResearchConfig config) {
            return research4j.deepResearch(query, getUserProfile(), config);
        }
    }

    public static Research4j createDefault() throws ConfigurationException {
        return builder().build();
    }

    public static Research4j createWithGemini(String geminiKey, String searchKey, String cseId) throws ConfigurationException {
        return builder().withGemini(geminiKey)
            .withGoogleSearch(searchKey, cseId)
            .enableDeepResearch()
            .build();
    }

    public static Research4j createWithOpenAI(String openaiKey, String tavilyKey) throws ConfigurationException {
        return builder().withOpenAI(openaiKey)
            .withTavily(tavilyKey)
            .enableDeepResearch()
            .build();
    }

    public boolean isLangGraph4jEnabled() {
        return graphExecutor.getExecutorType()
            .equals("LANGGRAPH4J");
    }

    public String getEngineInfo() {
        return String.format("Engine: %s, Deep Research: %s, LLM: %s, Citations: %s", graphExecutor.getExecutorType(),
            deepResearchEnabled ? "ENABLED" : "DISABLED", config.hasApiKey(ModelType.GEMINI) ? "Gemini" : "OpenAI", config.getDefaultCitationSource());
    }

    public void enableLangGraph4jRuntime() {
        logger.info("Switching to LangGraph4j engine at runtime (not yet implemented)");
    }
}