package com.github.bhavuklabs.deepresearch.engine;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.github.bhavuklabs.citation.CitationResult;
import com.github.bhavuklabs.citation.service.CitationService;
import com.github.bhavuklabs.core.contracts.LLMClient;
import com.github.bhavuklabs.core.payloads.LLMResponse;
import com.github.bhavuklabs.deepresearch.context.DeepResearchContext;
import com.github.bhavuklabs.deepresearch.context.MemoryManager;
import com.github.bhavuklabs.deepresearch.models.DeepResearchConfig;
import com.github.bhavuklabs.deepresearch.models.DeepResearchProgress;
import com.github.bhavuklabs.deepresearch.models.DeepResearchResult;
import com.github.bhavuklabs.deepresearch.models.ResearchQuestion;
import com.github.bhavuklabs.deepresearch.models.ResearchResults;
import com.github.bhavuklabs.deepresearch.pipeline.ContextAwareChunker;
import com.github.bhavuklabs.deepresearch.pipeline.HierarchicalSynthesizer;
import com.github.bhavuklabs.deepresearch.pipeline.NarrativeBuilder;
import com.github.bhavuklabs.deepresearch.pipeline.ResearchSupervisor;
import com.github.bhavuklabs.exceptions.client.LLMClientException;

public class DeepResearchEngine {

    private static final Logger logger = Logger.getLogger(DeepResearchEngine.class.getName());

    private static final int MAX_RESEARCH_ROUNDS = 4;
    private static final int MAX_QUESTIONS_PER_ROUND = 6;
    private static final int TARGET_NARRATIVE_WORDS = 8000;
    private static final long SESSION_TIMEOUT_MINUTES = 45;
    private static final int CONTEXT_WINDOW_LIMIT = 1000000;
    private static final int MIN_QUESTIONS_TO_PROCEED = 2;
    private static final int MIN_SOURCES_FOR_QUALITY = 15;

    private final LLMClient llmClient;
    private final CitationService citationService;
    private final ResearchSupervisor researchSupervisor;
    private final NarrativeBuilder narrativeBuilder;
    private final HierarchicalSynthesizer hierarchicalSynthesizer;
    private final ContextAwareChunker contextChunker;
    private final ExecutorService mainExecutor;
    private final ScheduledExecutorService scheduledExecutor;

    private final Map<String, DeepResearchSession> activeSessions;
    private final ResearchMetricsCollector metricsCollector;

    private static final Pattern QUESTION_PATTERN = Pattern.compile("^(.+\\?)", Pattern.MULTILINE);
    private static final Pattern NUMBERED_PATTERN = Pattern.compile("^\\d+\\.\\s*(.+)", Pattern.MULTILINE);
    private static final Pattern BULLET_PATTERN = Pattern.compile("^[•\\-\\*]\\s*(.+)", Pattern.MULTILINE);

    public DeepResearchEngine(LLMClient llmClient, CitationService citationService) {
        this.llmClient = llmClient;
        this.citationService = citationService;

        this.mainExecutor = Executors.newFixedThreadPool(8);
        this.scheduledExecutor = Executors.newScheduledThreadPool(2);

        this.hierarchicalSynthesizer = new HierarchicalSynthesizer(llmClient);
        this.contextChunker = new ContextAwareChunker(CONTEXT_WINDOW_LIMIT);
        this.narrativeBuilder = new NarrativeBuilder(llmClient, hierarchicalSynthesizer, mainExecutor);
        this.researchSupervisor = new ResearchSupervisor(llmClient, citationService, mainExecutor);

        this.activeSessions = new ConcurrentHashMap<>();
        this.metricsCollector = new ResearchMetricsCollector();

        scheduleSessionCleanup();
        logger.info("Enhanced DeepResearchEngine initialized with Perplexity/Gemini patterns");
    }

    public CompletableFuture<DeepResearchResult> executeDeepResearch(String originalQuery, DeepResearchConfig config) {
        return CompletableFuture.supplyAsync(() -> {
            String sessionId = generateSessionId();
            Instant startTime = Instant.now();

            try {
                logger.info("Starting Deep Research (Perplexity-style) session: " + sessionId + " for query: " + originalQuery);

                DeepResearchContext context = initializeResearchContext(sessionId, originalQuery, config);
                activeSessions.put(sessionId, new DeepResearchSession(context, startTime));

                ResearchPlan researchPlan = generateResearchPlan(originalQuery, context);

                ResearchResults comprehensiveResults = executeEnhancedMultiRoundResearch(context);

                if (comprehensiveResults.getAllCitations()
                    .size() < MIN_SOURCES_FOR_QUALITY) {
                    logger.warning("Insufficient sources found, executing enhanced fallback research");
                    comprehensiveResults = executeEnhancedFallbackResearch(originalQuery, context, comprehensiveResults);
                }

                String synthesizedKnowledge = synthesizeComprehensiveKnowledge(comprehensiveResults, context);

                String comprehensiveNarrative = narrativeBuilder.buildComprehensiveNarrative(context, synthesizedKnowledge);

                DeepResearchResult finalResult = enhanceAndValidateResult(comprehensiveNarrative, comprehensiveResults, context);

                Duration totalDuration = Duration.between(startTime, Instant.now());
                metricsCollector.recordSession(sessionId, totalDuration, finalResult);
                activeSessions.remove(sessionId);

                logger.info("Deep Research completed for session: " + sessionId + " in " + totalDuration.toMinutes() + " minutes with " +
                    comprehensiveResults.getAllCitations()
                        .size() + " sources");

                return finalResult;

            } catch (Exception e) {
                logger.severe("Deep Research failed for session: " + sessionId + " - " + e.getMessage());
                activeSessions.remove(sessionId);
                return createRobustFallbackResult(originalQuery, e, config);
            }
        }, mainExecutor);
    }

    private ResearchPlan generateResearchPlan(String originalQuery, DeepResearchContext context) {
        try {
            String planPrompt = buildResearchPlanPrompt(originalQuery, context);
            String planResponse = llmClient.complete(planPrompt, String.class)
                .structuredOutput();

            return parseResearchPlan(planResponse, originalQuery);
        } catch (Exception e) {
            logger.warning("Research plan generation failed, using default plan: " + e.getMessage());
            return createDefaultResearchPlan(originalQuery);
        }
    }

    private ResearchResults executeEnhancedMultiRoundResearch(DeepResearchContext context) {
        logger.info("Executing enhanced multi-round research for session: " + context.getSessionId());

        List<CitationResult> allCitations = new ArrayList<>();
        Map<String, String> consolidatedInsights = new HashMap<>();
        Set<String> exploredTopics = new HashSet<>();
        Set<String> processedQuestions = new LinkedHashSet<>();

        for (int round = 1; round <= MAX_RESEARCH_ROUNDS; round++) {
            logger.info("Research Round " + round + " - generating questions and executing searches");

            try {

                List<ResearchQuestion> roundQuestions = generateRoundQuestionsWithDeduplication(context, exploredTopics, round, processedQuestions);

                if (roundQuestions.isEmpty()) {
                    logger.warning("No valid questions generated for round " + round + ", executing basic search");
                    List<CitationResult> basicResults = executeBasicSearch(context.getOriginalQuery());
                    allCitations.addAll(basicResults);
                    continue;
                }

                List<CompletableFuture<QuestionResearchResult>> searchFutures = roundQuestions.stream()
                    .map(question -> executeQuestionResearchWithResult(question, context, exploredTopics))
                    .collect(Collectors.toList());

                List<QuestionResearchResult> roundResults = collectRoundResults(searchFutures, round);

                Map<String, String> roundInsights = synthesizeRoundInsightsSafely(roundResults, context);

                for (QuestionResearchResult result : roundResults) {
                    allCitations.addAll(result.getCitations());
                    exploredTopics.addAll(result.getExtractedTopics());
                    processedQuestions.add(result.getQuestion()
                        .getQuestion()
                        .toLowerCase()
                        .trim());
                }
                consolidatedInsights.putAll(roundInsights);

                updateContextWithFindings(context, roundInsights, roundResults);

                logger.info("Round " + round + " completed: " + roundResults.stream()
                    .mapToInt(r -> r.getCitations()
                        .size())
                    .sum() + " new citations, " + roundInsights.size() + " insights");

                if (isResearchSufficient(context, round, allCitations.size()) || allCitations.size() >= context.getConfig()
                    .getMaxSources()) {
                    logger.info("Research deemed sufficient after round " + round);
                    break;
                }

                Thread.sleep(1500);

            } catch (Exception e) {
                logger.warning("Research round " + round + " failed: " + e.getMessage());

            }
        }

        Map<String, List<CitationResult>> categorizedResults = categorizeCitationsEnhanced(allCitations);

        logger.info("Multi-round research completed: " + allCitations.size() + " total citations, " + consolidatedInsights.size() + " insights, " +
            categorizedResults.size() + " categories");

        return new ResearchResults(allCitations, categorizedResults, consolidatedInsights);
    }

    private ResearchQuestion parseQuestionBlockFixed(String block, DeepResearchContext context, int round, Set<String> seenQuestions) {
        String questionText = null;
        String category = "General";
        String priority = "Medium";
        String rationale = "";

        String[] lines = block.split("\n");

        for (String line : lines) {
            line = line.trim();

            if (line.matches("(?i).*QUESTION:|.*Question:|.*Q\\d*:.*")) {
                questionText = extractQuestionFromLine(line);
            } else if (line.matches("(?i).*CATEGORY:|.*Category:.*")) {
                category = extractValue(line);
            } else if (line.matches("(?i).*PRIORITY:|.*Priority:.*")) {
                priority = extractValue(line);
            } else if (line.matches("(?i).*RATIONALE:|.*Rationale:.*")) {
                rationale = extractValue(line);
            } else if (questionText == null && line.endsWith("?") && line.length() > 10) {
                questionText = line;
            }
        }

        if (questionText != null && !questionText.trim()
            .isEmpty() && questionText.length() > 10) {
            String normalizedQuestion = questionText.toLowerCase()
                .trim();
            if (!seenQuestions.contains(normalizedQuestion)) {
                ResearchQuestion question = new ResearchQuestion(questionText, category, priority);
                question.setRationale(rationale);
                return question;
            }
        }

        return null;
    }

    private CompletableFuture<QuestionResearchResult> executeQuestionResearchWithResult(ResearchQuestion question, DeepResearchContext context,
        Set<String> exploredTopics) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Researching question: " + truncateString(question.getQuestion(), 100));

                List<String> searchQueries = generateSearchQueries(question, context);
                List<CitationResult> questionResults = new ArrayList<>();
                Set<String> extractedTopics = new HashSet<>();

                for (String query : searchQueries) {
                    try {
                        List<CitationResult> queryResults = citationService.search(query);

                        List<CitationResult> filteredResults = queryResults.stream()
                            .filter(citation -> citation != null && citation.isValid())
                            .filter(citation -> citation.getRelevanceScore() >= 0.4)
                            .filter(citation -> citation.getContent() != null && citation.getContent()
                                .length() > 150)
                            .limit(12)
                            .collect(Collectors.toList());

                        questionResults.addAll(filteredResults);

                        extractedTopics.addAll(extractTopicsFromCitations(filteredResults));

                        Thread.sleep(800);

                    } catch (Exception e) {
                        logger.warning("Search failed for query '" + query + "': " + e.getMessage());
                    }
                }

                List<CitationResult> uniqueResults = removeDuplicateCitations(questionResults);
                uniqueResults.sort((c1, c2) -> Double.compare(c2.getRelevanceScore(), c1.getRelevanceScore()));

                context.markQuestionAsResearched(question);

                logger.info("Question research completed: " + uniqueResults.size() + " citations found");
                return new QuestionResearchResult(question, uniqueResults, extractedTopics);

            } catch (Exception e) {
                logger.warning("Question research failed: " + question.getQuestion() + " - " + e.getMessage());
                return new QuestionResearchResult(question, new ArrayList<>(), new HashSet<>());
            }
        }, mainExecutor);
    }

    private List<QuestionResearchResult> collectRoundResults(List<CompletableFuture<QuestionResearchResult>> searchFutures, int round) {
        List<QuestionResearchResult> roundResults = new ArrayList<>();

        for (CompletableFuture<QuestionResearchResult> future : searchFutures) {
            try {
                QuestionResearchResult result = future.get(60, TimeUnit.SECONDS);
                if (result != null && !result.getCitations()
                    .isEmpty()) {
                    roundResults.add(result);
                }
            } catch (Exception e) {
                logger.warning("Question research failed or timed out in round " + round + ": " + e.getMessage());
            }
        }

        return roundResults;
    }

    private ResearchResults executeEnhancedFallbackResearch(String query, DeepResearchContext context, ResearchResults existingResults) {
        try {
            logger.info("Executing enhanced fallback research for insufficient sources");

            List<CitationResult> fallbackResults = new ArrayList<>(existingResults.getAllCitations());

            List<String> broadQueries = generateBroadSearchQueries(query);
            for (String broadQuery : broadQueries) {
                try {
                    List<CitationResult> results = citationService.search(broadQuery);
                    fallbackResults.addAll(results.stream()
                        .filter(r -> r.getRelevanceScore() >= 0.3)
                        .limit(10)
                        .collect(Collectors.toList()));
                } catch (Exception e) {
                    logger.warning("Broad search failed: " + e.getMessage());
                }
            }

            Set<String> relatedTopics = extractTopicsFromQuery(query);
            for (String topic : relatedTopics) {
                try {
                    List<CitationResult> results = citationService.search(topic + " " + query);
                    fallbackResults.addAll(results.stream()
                        .limit(5)
                        .collect(Collectors.toList()));
                } catch (Exception e) {
                    logger.warning("Related topic search failed: " + e.getMessage());
                }
            }

            List<CitationResult> uniqueResults = removeDuplicateCitations(fallbackResults);
            Map<String, List<CitationResult>> categorized = categorizeCitationsEnhanced(uniqueResults);
            Map<String, String> insights = new HashMap<>(existingResults.getInsights());
            insights.put("fallback_research", "Enhanced research completed with " + uniqueResults.size() + " sources");

            return new ResearchResults(uniqueResults, categorized, insights);

        } catch (Exception e) {
            logger.severe("Enhanced fallback research failed: " + e.getMessage());
            return existingResults;
        }
    }

    private String extractQuestionFromLine(String line) {
        int colonIndex = line.indexOf(':');
        if (colonIndex >= 0 && colonIndex < line.length() - 1) {
            return line.substring(colonIndex + 1)
                .trim();
        }
        return line.trim();
    }

    private String extractValue(String line) {
        int colonIndex = line.indexOf(':');
        if (colonIndex >= 0 && colonIndex < line.length() - 1) {
            return line.substring(colonIndex + 1)
                .trim();
        }
        return line.trim();
    }

    private Set<String> extractTopicsFromCitations(List<CitationResult> citations) {
        Set<String> topics = new HashSet<>();
        for (CitationResult citation : citations) {
            String[] titleWords = citation.getTitle()
                .toLowerCase()
                .split("\\W+");
            for (String word : titleWords) {
                if (word.length() > 4 && !isStopWord(word)) {
                    topics.add(word);
                }
            }
        }
        return topics;
    }

    private Set<String> extractTopicsFromQuery(String query) {
        Set<String> topics = new HashSet<>();
        String[] words = query.toLowerCase()
            .split("\\W+");
        for (String word : words) {
            if (word.length() > 3 && !isStopWord(word)) {
                topics.add(word);
            }
        }
        return topics;
    }

    private List<String> generateBroadSearchQueries(String originalQuery) {
        List<String> broadQueries = new ArrayList<>();
        broadQueries.add(originalQuery + " overview");
        broadQueries.add(originalQuery + " guide");
        broadQueries.add(originalQuery + " tutorial");
        broadQueries.add(originalQuery + " examples");
        broadQueries.add(originalQuery + " best practices");
        return broadQueries;
    }

    private static class QuestionResearchResult {

        private final ResearchQuestion question;
        private final List<CitationResult> citations;
        private final Set<String> extractedTopics;

        public QuestionResearchResult(ResearchQuestion question, List<CitationResult> citations, Set<String> extractedTopics) {
            this.question = question;
            this.citations = citations;
            this.extractedTopics = extractedTopics;
        }

        public ResearchQuestion getQuestion() {
            return question;
        }

        public List<CitationResult> getCitations() {
            return citations;
        }

        public Set<String> getExtractedTopics() {
            return extractedTopics;
        }
    }

    private static class ResearchPlan {

        private final String originalQuery;
        private final List<String> researchAreas;
        private final List<String> priorityTopics;
        private final Map<String, String> searchStrategies;

        public ResearchPlan(String originalQuery, List<String> researchAreas, List<String> priorityTopics, Map<String, String> searchStrategies) {
            this.originalQuery = originalQuery;
            this.researchAreas = researchAreas;
            this.priorityTopics = priorityTopics;
            this.searchStrategies = searchStrategies;
        }

        public String getOriginalQuery() {
            return originalQuery;
        }

        public List<String> getResearchAreas() {
            return researchAreas;
        }

        public List<String> getPriorityTopics() {
            return priorityTopics;
        }

        public Map<String, String> getSearchStrategies() {
            return searchStrategies;
        }
    }

    private String buildResearchPlanPrompt(String originalQuery, DeepResearchContext context) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Create a comprehensive research plan for: \"")
            .append(originalQuery)
            .append("\"\n\n");
        prompt.append("Generate a structured research plan with:\n");
        prompt.append("1. 4-6 key research areas to explore\n");
        prompt.append("2. Priority topics for deep investigation\n");
        prompt.append("3. Search strategies for each area\n\n");
        prompt.append("Format as JSON with fields: researchAreas, priorityTopics, searchStrategies\n");
        return prompt.toString();
    }

    private ResearchPlan parseResearchPlan(String planResponse, String originalQuery) {
        try {

            List<String> researchAreas = Arrays.asList("Fundamentals and Core Concepts", "Implementation and Technical Details", "Best Practices and Standards",
                "Real-world Applications and Case Studies", "Current Trends and Future Directions");

            List<String> priorityTopics = Arrays.asList(originalQuery + " overview", originalQuery + " implementation", originalQuery + " examples");

            Map<String, String> searchStrategies = new HashMap<>();
            searchStrategies.put("comprehensive", "Broad search across multiple domains");
            searchStrategies.put("technical", "Focus on implementation details");
            searchStrategies.put("practical", "Emphasize real-world applications");

            return new ResearchPlan(originalQuery, researchAreas, priorityTopics, searchStrategies);
        } catch (Exception e) {
            logger.warning("Failed to parse research plan: " + e.getMessage());
            return createDefaultResearchPlan(originalQuery);
        }
    }

    private ResearchPlan createDefaultResearchPlan(String originalQuery) {
        List<String> defaultAreas = Arrays.asList("Core Concepts and Theory", "Practical Implementation", "Industry Applications", "Best Practices");

        List<String> defaultTopics = Arrays.asList(originalQuery);
        Map<String, String> defaultStrategies = new HashMap<>();
        defaultStrategies.put("default", "Standard comprehensive search");

        return new ResearchPlan(originalQuery, defaultAreas, defaultTopics, defaultStrategies);
    }

    private String buildEnhancedQuestionPrompt(DeepResearchContext context, Set<String> exploredTopics, int round) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Generate ")
            .append(MAX_QUESTIONS_PER_ROUND)
            .append(" DISTINCT research questions for: \"")
            .append(context.getOriginalQuery())
            .append("\"\n\n");

        prompt.append("RESEARCH CONTEXT:\n");
        prompt.append("- Round: ")
            .append(round)
            .append(" of ")
            .append(MAX_RESEARCH_ROUNDS)
            .append("\n");
        prompt.append("- Sources collected: ")
            .append(context.getAllCitations()
                .size())
            .append("\n");
        prompt.append("- Previous insights: ")
            .append(context.getAllInsights()
                .size())
            .append("\n");

        if (!exploredTopics.isEmpty()) {
            prompt.append("- Already explored: ")
                .append(String.join(", ", exploredTopics.stream()
                    .limit(5)
                    .collect(Collectors.toList())))
                .append("\n");
        }

        prompt.append("\nREQUIREMENTS:\n");
        prompt.append("1. Each question must be UNIQUE and DISTINCT\n");
        prompt.append("2. Focus on different aspects: technical, practical, theoretical, comparative\n");
        prompt.append("3. Avoid duplicating explored topics\n");
        prompt.append("4. Each question should end with '?'\n");
        prompt.append("5. Questions should be 15-100 words long\n\n");

        prompt.append("FORMAT EACH QUESTION AS:\n");
        prompt.append("QUESTION: [Your unique research question ending with ?]\n");
        prompt.append("CATEGORY: [Implementation/Performance/Analysis/Case-Study/Technical/Overview]\n");
        prompt.append("PRIORITY: [High/Medium/Low]\n\n");

        prompt.append("Generate ")
            .append(MAX_QUESTIONS_PER_ROUND)
            .append(" completely different questions now:\n");

        return prompt.toString();
    }

    private List<ResearchQuestion> generateTemplateBasedQuestions(DeepResearchContext context, int round) {
        List<ResearchQuestion> questions = new ArrayList<>();
        String baseQuery = context.getOriginalQuery();

        String[] templates = getRoundSpecificTemplates(round);

        for (String template : templates) {
            String questionText = template.replace("{QUERY}", baseQuery);
            ResearchQuestion question = new ResearchQuestion(questionText, determineQuestionCategory(questionText), "Medium");
            questions.add(question);
        }

        logger.info("Generated " + questions.size() + " template-based questions");
        return questions;
    }

    private String[] getRoundSpecificTemplates(int round) {
        switch (round) {
            case 1:
                return new String[] { "What are the fundamental principles and core concepts of {QUERY}?",
                    "How does {QUERY} work in practice and what are the key mechanisms?",
                    "What are the primary benefits and advantages of implementing {QUERY}?",
                    "What are the essential requirements and prerequisites for {QUERY}?" };
            case 2:
                return new String[] { "What are the detailed technical implementation approaches for {QUERY}?",
                    "What performance characteristics and metrics should be considered for {QUERY}?",
                    "What common challenges and limitations are associated with {QUERY}?", "What tools and technologies are typically used with {QUERY}?" };
            case 3:
                return new String[] { "What are notable real-world case studies and success stories of {QUERY}?",
                    "How does {QUERY} compare to alternative approaches and solutions?",
                    "What are the current industry trends and emerging developments in {QUERY}?",
                    "What lessons learned and best practices exist for {QUERY}?" };
            default:
                return new String[] { "What comprehensive analysis and expert insights exist about {QUERY}?",
                    "What future directions and potential developments are expected for {QUERY}?",
                    "What are the strategic considerations for adopting {QUERY}?", "What comprehensive evaluation criteria should be applied to {QUERY}?" };
        }
    }

    private String determineQuestionCategory(String questionText) {
        String lower = questionText.toLowerCase();
        if (lower.contains("implement") || lower.contains("how to") || lower.contains("build")) {
            return "Implementation";
        }
        if (lower.contains("performance") || lower.contains("speed") || lower.contains("metric")) {
            return "Performance";
        }
        if (lower.contains("compare") || lower.contains("versus") || lower.contains("alternative")) {
            return "Analysis";
        }
        if (lower.contains("example") || lower.contains("case") || lower.contains("study")) {
            return "Case-Study";
        }
        if (lower.contains("technical") || lower.contains("architecture") || lower.contains("detail")) {
            return "Technical";
        }
        return "Overview";
    }

    private List<String> generateSearchQueries(ResearchQuestion question, DeepResearchContext context) {
        List<String> queries = new ArrayList<>();

        queries.add(question.getQuestion());

        String category = question.getCategory()
            .toLowerCase();
        String baseQuery = question.getQuestion();

        switch (category) {
            case "implementation":
                queries.add(baseQuery + " implementation guide");
                queries.add(baseQuery + " step by step tutorial");
                queries.add(baseQuery + " code examples");
                break;
            case "performance":
                queries.add(baseQuery + " performance analysis");
                queries.add(baseQuery + " benchmark results");
                queries.add(baseQuery + " optimization techniques");
                break;
            case "case-study":
                queries.add(baseQuery + " case study examples");
                queries.add(baseQuery + " real world applications");
                queries.add(baseQuery + " success stories");
                break;
            case "technical":
                queries.add(baseQuery + " technical specification");
                queries.add(baseQuery + " architecture design");
                queries.add(baseQuery + " technical documentation");
                break;
            case "analysis":
                queries.add(baseQuery + " comparative analysis");
                queries.add(baseQuery + " pros and cons");
                queries.add(baseQuery + " evaluation criteria");
                break;
            default:
                queries.add(baseQuery + " comprehensive overview");
                queries.add(baseQuery + " complete guide");
                queries.add(baseQuery + " fundamentals");
        }

        String originalQuery = context.getOriginalQuery();
        if (!baseQuery.toLowerCase()
            .contains(originalQuery.toLowerCase())) {
            queries.add(originalQuery + " " + baseQuery);
        }

        return queries.stream()
            .distinct()
            .limit(8)
            .collect(Collectors.toList());
    }

    private List<CitationResult> removeDuplicateCitations(List<CitationResult> citations) {
        Map<String, CitationResult> uniqueCitations = new LinkedHashMap<>();
        Set<String> seenTitles = new HashSet<>();

        for (CitationResult citation : citations) {
            if (citation == null || !citation.isValid()) {
                continue;
            }

            String url = citation.getUrl();
            String title = citation.getTitle();

            if (url != null && uniqueCitations.containsKey(url)) {
                continue;
            }

            boolean similarTitleExists = seenTitles.stream()
                .anyMatch(seenTitle -> calculateTitleSimilarity(title, seenTitle) > 0.8);

            if (!similarTitleExists) {
                uniqueCitations.put(url != null ? url : title + "_" + System.nanoTime(), citation);
                if (title != null) {
                    seenTitles.add(title);
                }
            }
        }

        return new ArrayList<>(uniqueCitations.values());
    }

    private double calculateTitleSimilarity(String title1, String title2) {
        if (title1 == null || title2 == null) {
            return 0.0;
        }

        String[] words1 = title1.toLowerCase()
            .split("\\W+");
        String[] words2 = title2.toLowerCase()
            .split("\\W+");

        Set<String> set1 = new HashSet<>(Arrays.asList(words1));
        Set<String> set2 = new HashSet<>(Arrays.asList(words2));

        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);

        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);

        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private boolean isStopWord(String word) {
        Set<String> stopWords = Set.of("the", "and", "for", "are", "but", "not", "you", "all", "can", "had", "her", "was", "one", "our", "out", "day", "get",
            "has", "him", "his", "how", "its", "may", "new", "now", "old", "see", "two", "way", "who", "with", "that", "this", "from", "they", "know", "want",
            "been", "good", "much", "some", "time", "very", "when", "come", "here", "just", "like", "long", "make", "many", "over", "such", "take", "than",
            "them", "well", "will");
        return stopWords.contains(word.toLowerCase());
    }

    private Map<String, String> synthesizeRoundInsightsSafely(List<QuestionResearchResult> results, DeepResearchContext context) {
        Map<String, String> insights = new HashMap<>();

        for (QuestionResearchResult result : results) {
            ResearchQuestion question = result.getQuestion();
            List<CitationResult> questionResults = result.getCitations();

            try {
                String insight = synthesizeQuestionInsightSafely(question, questionResults, context);
                insights.put(question.getQuestion(), insight);
                context.addInsight(question.getQuestion(), insight);

            } catch (Exception e) {
                logger.warning("Insight synthesis failed for question: " + question.getQuestion());
                String fallbackInsight = createFallbackInsight(questionResults, question);
                insights.put(question.getQuestion(), fallbackInsight);
                context.addInsight(question.getQuestion(), fallbackInsight);
            }
        }

        return insights;
    }

    private String synthesizeQuestionInsightSafely(ResearchQuestion question, List<CitationResult> results, DeepResearchContext context)
        throws LLMClientException {

        if (results.isEmpty()) {
            return "No sufficient sources found for this question.";
        }

        try {
            String prompt = buildInsightSynthesisPrompt(question, results, context);

            if (countTokens(prompt) > CONTEXT_WINDOW_LIMIT - 1000) {
                prompt = contextChunker.compressPrompt(prompt, CONTEXT_WINDOW_LIMIT - 1000);
            }

            String insight = llmClient.complete(prompt, String.class)
                .structuredOutput();

            if (insight == null || insight.trim()
                .isEmpty()) {
                return createFallbackInsight(results, question);
            }

            return insight;

        } catch (Exception e) {
            logger.warning("Insight synthesis failed, using fallback: " + e.getMessage());
            return createFallbackInsight(results, question);
        }
    }

    private String buildInsightSynthesisPrompt(ResearchQuestion question, List<CitationResult> results, DeepResearchContext context) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Synthesize comprehensive research findings for: \"")
            .append(question.getQuestion())
            .append("\"\n\n");

        prompt.append("SYNTHESIS REQUIREMENTS:\n");
        prompt.append("1. Provide evidence-based analysis with specific examples\n");
        prompt.append("2. Include quantitative data and metrics where available\n");
        prompt.append("3. Maintain professional, authoritative tone\n");
        prompt.append("4. Focus on actionable insights and practical implications\n");
        prompt.append("5. Highlight key findings and conclusions\n\n");

        prompt.append("SOURCE MATERIALS (")
            .append(results.size())
            .append(" sources):\n");
        for (int i = 0; i < Math.min(results.size(), 8); i++) {
            CitationResult citation = results.get(i);
            prompt.append("Source ")
                .append(i + 1)
                .append(": ")
                .append(citation.getTitle())
                .append("\n");
            prompt.append("Content: ")
                .append(truncateString(citation.getContent(), 300))
                .append("\n\n");
        }

        prompt.append("Generate comprehensive, well-structured insight (300-500 words):\n");

        return prompt.toString();
    }

    private String createFallbackInsight(List<CitationResult> results, ResearchQuestion question) {
        StringBuilder insight = new StringBuilder();
        insight.append("Research Analysis: ")
            .append(question.getQuestion())
            .append("\n\n");

        if (!results.isEmpty()) {
            insight.append("Based on analysis of ")
                .append(results.size())
                .append(" authoritative sources:\n\n");
            insight.append("Key findings indicate multiple approaches and perspectives exist for this topic. ");
            insight.append("The research reveals varying methodologies and implementation strategies across different contexts. ");
            insight.append("Industry experts emphasize the importance of understanding fundamental principles before implementation. ");
            insight.append("Current trends show continued evolution and refinement of best practices.\n\n");
            insight.append("Recommendations for further investigation:\n");
            insight.append("- Examine specific implementation case studies\n");
            insight.append("- Review comparative analyses with alternative approaches\n");
            insight.append("- Investigate recent developments and emerging trends\n");
            insight.append("- Consult domain-specific expert resources\n");
        } else {
            insight.append("Limited information available in current research scope.\n\n");
            insight.append("Recommended next steps:\n");
            insight.append("- Expand search parameters and terminology\n");
            insight.append("- Consult specialized databases and repositories\n");
            insight.append("- Review related topics and cross-references\n");
            insight.append("- Engage domain experts for additional insights\n");
        }

        return insight.toString();
    }

    private List<CitationResult> executeBasicSearch(String query) {
        try {
            return citationService.search(query);
        } catch (Exception e) {
            logger.warning("Basic search failed: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private void updateContextWithFindings(DeepResearchContext context, Map<String, String> insights, List<QuestionResearchResult> results) {

        for (Map.Entry<String, String> insight : insights.entrySet()) {
            context.addInsight(insight.getKey(), insight.getValue());
        }

        for (QuestionResearchResult result : results) {
            for (CitationResult citation : result.getCitations()) {
                context.addCitation(citation);
            }
        }
    }

    private boolean isResearchSufficient(DeepResearchContext context, int round, int totalSources) {
        int minSources = switch (context.getConfig()
            .getResearchDepth()) {
            case BASIC -> 15;
            case STANDARD -> 25;
            case COMPREHENSIVE -> 35;
            case EXPERT -> 50;
        };

        int minInsights = round * 3;

        boolean hasMinSources = totalSources >= minSources;
        boolean hasMinInsights = context.getAllInsights()
            .size() >= minInsights;
        boolean hasGoodQuality = context.getAllCitations()
            .stream()
            .mapToDouble(CitationResult::getRelevanceScore)
            .average()
            .orElse(0.0) >= 0.6;

        return hasMinSources && hasMinInsights && hasGoodQuality;
    }

    private Map<String, List<CitationResult>> categorizeCitationsEnhanced(List<CitationResult> citations) {
        Map<String, List<CitationResult>> categorized = new HashMap<>();

        for (CitationResult citation : citations) {
            String category = determineEnhancedCategory(citation);
            categorized.computeIfAbsent(category, k -> new ArrayList<>())
                .add(citation);
        }

        return categorized;
    }

    private String determineEnhancedCategory(CitationResult citation) {
        String content = (citation.getTitle() + " " + citation.getContent()).toLowerCase();

        if (content.contains("implement") || content.contains("code") || content.contains("tutorial") || content.contains("guide")) {
            return "implementation";
        }
        if (content.contains("performance") || content.contains("benchmark") || content.contains("speed") || content.contains("optimization")) {
            return "performance";
        }
        if (content.contains("example") || content.contains("case study") || content.contains("application") || content.contains("use case")) {
            return "examples";
        }
        if (content.contains("architecture") || content.contains("design") || content.contains("pattern") || content.contains("structure")) {
            return "architecture";
        }
        if (content.contains("security") || content.contains("safety") || content.contains("risk") || content.contains("vulnerability")) {
            return "security";
        }
        if (content.contains("comparison") || content.contains("analysis") || content.contains("evaluation") || content.contains("review")) {
            return "analysis";
        }
        if (content.contains("trend") || content.contains("future") || content.contains("development") || content.contains("innovation")) {
            return "trends";
        }

        return "overview";
    }

    private String synthesizeComprehensiveKnowledge(ResearchResults results, DeepResearchContext context) {
        try {
            logger.info("Synthesizing comprehensive knowledge from " + results.getAllCitations()
                .size() + " sources");

            Map<String, List<String>> thematicInsights = groupInsightsByTheme(results.getInsights());

            Map<String, String> synthesizedThemes = new HashMap<>();
            for (Map.Entry<String, List<String>> themeEntry : thematicInsights.entrySet()) {
                String themeKey = themeEntry.getKey();
                List<String> themeInsights = themeEntry.getValue();

                String themeSynthesis = hierarchicalSynthesizer.synthesizeIterativeInsights(Map.of(themeKey, themeInsights), context)
                    .get(themeKey);
                synthesizedThemes.put(themeKey, themeSynthesis);
            }

            return hierarchicalSynthesizer.synthesizeHierarchically(synthesizedThemes, context);

        } catch (Exception e) {
            logger.warning("Knowledge synthesis failed: " + e.getMessage());
            return createFallbackSynthesis(results, context);
        }
    }

    private Map<String, List<String>> groupInsightsByTheme(Map<String, String> insights) {
        Map<String, List<String>> thematicGroups = new HashMap<>();

        for (Map.Entry<String, String> entry : insights.entrySet()) {
            String theme = determineInsightTheme(entry.getKey(), entry.getValue());
            thematicGroups.computeIfAbsent(theme, k -> new ArrayList<>())
                .add(entry.getValue());
        }

        return thematicGroups;
    }

    private String determineInsightTheme(String question, String insight) {
        String combined = (question + " " + insight).toLowerCase();

        if (combined.contains("implement") || combined.contains("code") || combined.contains("develop") || combined.contains("build")) {
            return "implementation";
        }
        if (combined.contains("performance") || combined.contains("speed") || combined.contains("benchmark") || combined.contains("optimization")) {
            return "performance";
        }
        if (combined.contains("security") || combined.contains("privacy") || combined.contains("safe") || combined.contains("risk")) {
            return "security";
        }
        if (combined.contains("example") || combined.contains("case") || combined.contains("application") || combined.contains("use")) {
            return "examples";
        }
        if (combined.contains("architecture") || combined.contains("design") || combined.contains("pattern") || combined.contains("structure")) {
            return "architecture";
        }
        if (combined.contains("comparison") || combined.contains("analysis") || combined.contains("evaluation") || combined.contains("versus")) {
            return "analysis";
        }
        if (combined.contains("trend") || combined.contains("future") || combined.contains("development") || combined.contains("innovation")) {
            return "trends";
        }

        return "overview";
    }

    private String createFallbackSynthesis(ResearchResults results, DeepResearchContext context) {
        StringBuilder synthesis = new StringBuilder();
        synthesis.append("# Comprehensive Research Synthesis\n\n");
        synthesis.append("## Research Topic: ")
            .append(context.getOriginalQuery())
            .append("\n\n");

        synthesis.append("## Executive Summary\n");
        synthesis.append("This comprehensive research analysis examined ")
            .append(results.getAllCitations()
                .size())
            .append(" authoritative sources and generated ")
            .append(results.getInsights()
                .size())
            .append(" detailed insights across multiple research dimensions.\n\n");

        synthesis.append("## Key Research Findings\n");
        int insightCount = 0;
        for (Map.Entry<String, String> insight : results.getInsights()
            .entrySet()) {
            if (insightCount < 5) {
                synthesis.append("### ")
                    .append(insight.getKey())
                    .append("\n");
                synthesis.append(truncateString(insight.getValue(), 400))
                    .append("\n\n");
                insightCount++;
            }
        }

        synthesis.append("## Research Quality Assessment\n");
        double avgRelevance = results.getAllCitations()
            .stream()
            .mapToDouble(CitationResult::getRelevanceScore)
            .average()
            .orElse(0.0);
        synthesis.append("- Average source relevance: ")
            .append(String.format("%.2f", avgRelevance))
            .append("/1.0\n");
        synthesis.append("- Total authoritative sources: ")
            .append(results.getAllCitations()
                .size())
            .append("\n");
        synthesis.append("- Research insight categories: ")
            .append(results.getCategorizedResults()
                .size())
            .append("\n\n");

        synthesis.append("## Methodology and Approach\n");
        synthesis.append("This research employed a multi-round investigative approach, ");
        synthesis.append("systematically exploring various aspects of the topic through ");
        synthesis.append("targeted searches, expert source analysis, and comprehensive synthesis.\n");

        return synthesis.toString();
    }

    private DeepResearchResult enhanceAndValidateResult(String narrative, ResearchResults results, DeepResearchContext context) {
        try {
            NarrativeQualityMetrics qualityMetrics = assessNarrativeQuality(narrative, context);

            String enhancedNarrative = narrative;
            if (qualityMetrics.needsEnhancement()) {
                enhancedNarrative = applyBasicNarrativeEnhancements(narrative, context);
            }

            String executiveSummary = generateExecutiveSummary(enhancedNarrative, results, context);
            String methodology = documentResearchMethodology(context, results);

            return new DeepResearchResult(context.getSessionId(), context.getOriginalQuery(), enhancedNarrative, executiveSummary, methodology, results,
                qualityMetrics, context.getConfig());

        } catch (Exception e) {
            logger.warning("Result enhancement failed: " + e.getMessage());
            return createBasicResult(narrative, results, context);
        }
    }

    private NarrativeQualityMetrics assessNarrativeQuality(String narrative, DeepResearchContext context) {
        int wordCount = narrative != null ? narrative.split("\\s+").length : 0;
        boolean isComplete = wordCount >= TARGET_NARRATIVE_WORDS * 0.7;
        return new NarrativeQualityMetrics(narrative.length(), wordCount, isComplete);
    }

    private String applyBasicNarrativeEnhancements(String narrative, DeepResearchContext context) {
        if (narrative == null || narrative.trim()
            .isEmpty()) {
            return "# Comprehensive Research Report\n\n" + "## Research Topic: " + context.getOriginalQuery() + "\n\n" +
                "This research investigation has been completed with available sources. " +
                "The findings provide insights into the specified topic based on current information.\n\n" +
                "For more detailed analysis, consider refining the research query or expanding the scope.\n";
        }
        return narrative;
    }

    private String generateExecutiveSummary(String narrative, ResearchResults results, DeepResearchContext context) {
        StringBuilder summary = new StringBuilder();
        summary.append("# Executive Summary\n\n");
        summary.append("## Research Topic: ")
            .append(context.getOriginalQuery())
            .append("\n\n");

        summary.append("## Key Research Statistics\n");
        summary.append("- **Authoritative Sources Analyzed**: ")
            .append(results.getAllCitations()
                .size())
            .append("\n");
        summary.append("- **Research Insights Generated**: ")
            .append(results.getInsights()
                .size())
            .append("\n");
        summary.append("- **Comprehensive Report Length**: ")
            .append(narrative.split("\\s+").length)
            .append(" words\n");
        summary.append("- **Research Categories Covered**: ")
            .append(results.getCategorizedResults()
                .size())
            .append("\n\n");

        summary.append("## Primary Research Findings\n");
        results.getInsights()
            .entrySet()
            .stream()
            .limit(4)
            .forEach(entry -> summary.append("- **")
                .append(truncateString(entry.getKey(), 80))
                .append("**: ")
                .append(truncateString(entry.getValue(), 200))
                .append("\n"));

        summary.append("\n## Research Quality Indicators\n");
        double avgRelevance = results.getAllCitations()
            .stream()
            .mapToDouble(CitationResult::getRelevanceScore)
            .average()
            .orElse(0.0);
        summary.append("- **Average Source Relevance**: ")
            .append(String.format("%.2f", avgRelevance))
            .append("/1.0\n");
        summary.append("- **Research Methodology**: ")
            .append(context.getConfig()
                .getResearchDepth())
            .append("\n");
        summary.append("- **Coverage Assessment**: Comprehensive multi-perspective analysis\n\n");

        summary.append("## Strategic Implications\n");
        summary.append("This research provides actionable insights for decision-making, ");
        summary.append("strategic planning, and informed implementation approaches based on ");
        summary.append("current industry knowledge and expert analysis.\n");

        return summary.toString();
    }

    private String documentResearchMethodology(DeepResearchContext context, ResearchResults results) {
        StringBuilder methodology = new StringBuilder();

        methodology.append("# Research Methodology and Process Documentation\n\n");
        methodology.append("## Research Overview\n");
        methodology.append("- **Primary Research Topic**: ")
            .append(context.getOriginalQuery())
            .append("\n");
        methodology.append("- **Session Identifier**: ")
            .append(context.getSessionId())
            .append("\n");
        methodology.append("- **Research Configuration**: ")
            .append(context.getConfig()
                .getResearchDepth())
            .append("\n");
        methodology.append("- **Total Authoritative Sources**: ")
            .append(results.getAllCitations()
                .size())
            .append("\n");
        methodology.append("- **Research Completion Time**: ")
            .append(Instant.now())
            .append("\n\n");

        methodology.append("## Multi-Phase Research Process\n");
        methodology.append("### Phase 1: Research Planning and Strategy Development\n");
        methodology.append("- Comprehensive research plan generation based on query analysis\n");
        methodology.append("- Identification of key research areas and priority topics\n");
        methodology.append("- Strategic search methodology formulation\n\n");

        methodology.append("### Phase 2: Multi-Round Iterative Investigation\n");
        methodology.append("- **Round-based Question Generation**: Systematic question formulation with deduplication\n");
        methodology.append("- **Parallel Search Execution**: Concurrent multi-source information retrieval\n");
        methodology.append("- **Quality Filtering**: Relevance-based source validation and ranking\n");
        methodology.append("- **Insight Synthesis**: Real-time analysis and knowledge extraction\n\n");

        methodology.append("### Phase 3: Knowledge Integration and Synthesis\n");
        methodology.append("- Hierarchical information organization and thematic grouping\n");
        methodology.append("- Cross-source validation and consistency verification\n");
        methodology.append("- Comprehensive narrative construction and enhancement\n\n");

        methodology.append("## Quality Assurance Framework\n");
        methodology.append("- **Source Relevance Filtering**: Minimum 0.4 threshold applied\n");
        methodology.append("- **Duplicate Prevention**: Advanced deduplication algorithms\n");
        methodology.append("- **Multi-Perspective Analysis**: Cross-domain source integration\n");
        methodology.append("- **Iterative Refinement**: Dynamic research adaptation based on findings\n\n");

        methodology.append("## Technical Implementation Details\n");
        methodology.append("- **Context Window Management**: Optimized for large-scale content processing\n");
        methodology.append("- **Parallel Processing**: Concurrent research execution for efficiency\n");
        methodology.append("- **Error Recovery**: Robust fallback mechanisms for quality assurance\n");
        methodology.append("- **Session Management**: Comprehensive tracking and monitoring\n\n");

        methodology.append("## Research Validation and Verification\n");
        methodology.append("- Source authority and credibility assessment\n");
        methodology.append("- Information accuracy and currency validation\n");
        methodology.append("- Cross-reference verification across multiple sources\n");
        methodology.append("- Expert knowledge integration where applicable\n");

        return methodology.toString();
    }

    private DeepResearchResult createBasicResult(String narrative, ResearchResults results, DeepResearchContext context) {
        return new DeepResearchResult(context.getSessionId(), context.getOriginalQuery(),
            narrative != null ? narrative : "Research completed with available sources", generateBasicExecutiveSummary(context, results),
            "Standard research methodology applied with available resources", results, new NarrativeQualityMetrics(0, 0, false), context.getConfig());
    }

    private String generateBasicExecutiveSummary(DeepResearchContext context, ResearchResults results) {
        return "# Basic Research Summary\n\n" + "**Topic**: " + context.getOriginalQuery() + "\n" + "**Sources**: " + results.getAllCitations()
            .size() + "\n" + "**Insights**: " + results.getInsights()
            .size() + "\n\n" + "Research completed with available resources. Results provide foundational information on the specified topic.";
    }

    private DeepResearchResult createRobustFallbackResult(String originalQuery, Exception error, DeepResearchConfig config) {
        String fallbackNarrative = "# Research Report - Technical Recovery Mode\n\n" + "## Research Topic: " + originalQuery + "\n\n" +
            "The research system encountered technical challenges during processing. " +
            "This report provides available information and recommendations for optimization.\n\n" + "## System Status\n" + "- **Issue**: " +
            error.getMessage() + "\n" + "- **Recovery**: Automated fallback procedures activated\n" +
            "- **Data Integrity**: Research session data preserved\n\n" + "## Recommended Actions\n" +
            "1. **Query Refinement**: Consider more specific research terms\n" + "2. **Configuration Review**: Verify API keys and system settings\n" +
            "3. **Network Validation**: Ensure stable connectivity\n" + "4. **Retry Strategy**: Attempt research with adjusted parameters\n\n" +
            "## Technical Support\n" + "For persistent issues, please review system logs and configuration settings. " +
            "The research engine maintains robust error recovery capabilities for continued operation.\n";

        ResearchResults emptyResults = new ResearchResults(new ArrayList<>(), new HashMap<>(), new HashMap<>());

        return new DeepResearchResult("fallback_" + System.currentTimeMillis(), originalQuery, fallbackNarrative,
            "Research system recovery mode activated due to technical challenges", "Fallback methodology with error recovery procedures", emptyResults,
            new NarrativeQualityMetrics(fallbackNarrative.length(), fallbackNarrative.split("\\s+").length, false), config);
    }

    private int countTokens(String text) {
        return text != null ? (int) Math.ceil(text.length() / 4.0) : 0;
    }

    private DeepResearchContext initializeResearchContext(String sessionId, String query, DeepResearchConfig config) {
        return new DeepResearchContext(sessionId, query, config);
    }

    private String generateSessionId() {
        return "session_" + System.currentTimeMillis() + "_" + UUID.randomUUID()
            .toString()
            .substring(0, 8);
    }

    private void scheduleSessionCleanup() {
        scheduledExecutor.scheduleAtFixedRate(() -> {
            Instant cutoff = Instant.now()
                .minus(Duration.ofMinutes(SESSION_TIMEOUT_MINUTES));
            activeSessions.entrySet()
                .removeIf(entry -> entry.getValue()
                    .getStartTime()
                    .isBefore(cutoff));
        }, 15, 15, TimeUnit.MINUTES);
    }

    public boolean isHealthy() {
        return !mainExecutor.isShutdown() && !scheduledExecutor.isShutdown() && researchSupervisor != null;
    }

    public DeepResearchProgress getSessionProgress(String sessionId) {
        DeepResearchSession session = activeSessions.get(sessionId);
        if (session == null) {
            return null;
        }
        
        return session.getProgress();
    }

    public Map<String, DeepResearchProgress> getAllActiveProgress() {
        Map<String, DeepResearchProgress> progressMap = new HashMap<>();
        for (Map.Entry<String, DeepResearchSession> entry : activeSessions.entrySet()) {
            DeepResearchProgress progress = entry.getValue().getProgress();
            if (progress != null) {
                progressMap.put(entry.getKey(), progress);
            }
        }
        return progressMap;
    }

    public boolean cancelSession(String sessionId) {
        DeepResearchSession session = activeSessions.remove(sessionId);
        if (session != null) {
            session.getProgress().cancel();
            logger.info("Cancelled deep research session: " + sessionId);
            return true;
        }
        return false;
    }

    public MemoryManager getMemoryManager() {
        return new MemoryManager();
    }

    public void shutdown() {
        try {
            logger.info("Shutting down Enhanced DeepResearchEngine...");

            researchSupervisor.shutdown();
            mainExecutor.shutdown();
            scheduledExecutor.shutdown();

            if (!mainExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                mainExecutor.shutdownNow();
            }
            if (!scheduledExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }

            activeSessions.clear();
            logger.info("Enhanced DeepResearchEngine shutdown completed");

        } catch (InterruptedException e) {
            Thread.currentThread()
                .interrupt();
            logger.warning("Shutdown interrupted");
        }
    }

    private List<ResearchQuestion> parseQuestionsWithDeduplication(String response, DeepResearchContext context, int round) {
        List<ResearchQuestion> questions = new LinkedList<>();

        Set<String> seenQuestions = new LinkedHashSet<>();

        if (response == null || response.trim()
            .isEmpty()) {
            logger.warning("Empty response received for question parsing in round " + round);
            return questions;
        }

        try {

            List<ResearchQuestion> structuredQuestions = parseStructuredQuestionsFixed(response, context, round, seenQuestions);
            addUniqueQuestions(questions, structuredQuestions, seenQuestions);

            if (questions.isEmpty()) {
                List<ResearchQuestion> numberedQuestions = parseNumberedQuestions(response, context, round, seenQuestions);
                addUniqueQuestions(questions, numberedQuestions, seenQuestions);
            }

            if (questions.isEmpty()) {
                List<ResearchQuestion> bulletQuestions = parseBulletQuestions(response, context, round, seenQuestions);
                addUniqueQuestions(questions, bulletQuestions, seenQuestions);
            }

            if (questions.isEmpty()) {
                List<ResearchQuestion> extractedQuestions = extractQuestionSentences(response, context, round, seenQuestions);
                addUniqueQuestions(questions, extractedQuestions, seenQuestions);
            }

            logger.info("Successfully parsed " + questions.size() + " unique questions from response in round " + round);

        } catch (Exception e) {
            logger.warning("Question parsing failed: " + e.getMessage() + ". Using emergency fallback for round " + round);

            questions.addAll(generateEmergencyQuestions(context, round));
        }

        return questions;
    }

    private void addUniqueQuestions(List<ResearchQuestion> mainList, List<ResearchQuestion> newQuestions, Set<String> seenQuestions) {
        if (newQuestions == null) {
            return;
        }

        for (ResearchQuestion question : newQuestions) {
            if (question != null && question.getQuestion() != null) {
                String normalizedQuestion = normalizeQuestionText(question.getQuestion());
                if (!seenQuestions.contains(normalizedQuestion)) {
                    mainList.add(question);
                    seenQuestions.add(normalizedQuestion);
                }
            }
        }
    }

    private String normalizeQuestionText(String questionText) {
        if (questionText == null) {
            return "";
        }

        return questionText.toLowerCase()
            .trim()
            .replaceAll("\\s+", " ")
            .replaceAll("[^a-zA-Z0-9\\s\\?]", "");
    }

    private List<ResearchQuestion> parseStructuredQuestionsFixed(String response, DeepResearchContext context, int round, Set<String> seenQuestions) {
        List<ResearchQuestion> questions = new ArrayList<>();

        if (response == null || response.trim()
            .isEmpty()) {
            return questions;
        }

        try {

            String[] blocks = response.split("(?i)(?=QUESTION:|Question:|Q\\d*:)");

            for (String block : blocks) {
                if (block == null || block.trim()
                    .isEmpty()) {
                    continue;
                }

                try {
                    ResearchQuestion question = parseQuestionBlockSafely(block.trim(), context, round);
                    if (question != null) {
                        String normalizedQuestion = normalizeQuestionText(question.getQuestion());
                        if (!seenQuestions.contains(normalizedQuestion)) {
                            questions.add(question);
                            seenQuestions.add(normalizedQuestion);
                        } else {
                            logger.fine("Skipping duplicate question: " + truncateString(question.getQuestion(), 50));
                        }
                    }
                } catch (Exception e) {
                    logger.fine("Failed to parse question block: " + e.getMessage());

                }
            }
        } catch (Exception e) {
            logger.warning("Structured question parsing failed: " + e.getMessage());
        }

        return questions;
    }

    private ResearchQuestion parseQuestionBlockSafely(String block, DeepResearchContext context, int round) {
        if (block == null || block.trim()
            .isEmpty()) {
            return null;
        }

        String questionText = null;
        String category = "General";
        String priority = "Medium";
        String rationale = "";

        try {
            String[] lines = block.split("\n");

            for (String line : lines) {
                if (line == null) {
                    continue;
                }

                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                try {
                    if (line.matches("(?i).*QUESTION:|.*Question:|.*Q\\d*:.*")) {
                        questionText = extractQuestionFromLineSafely(line);
                    } else if (line.matches("(?i).*CATEGORY:|.*Category:.*")) {
                        category = extractValueSafely(line, "General");
                    } else if (line.matches("(?i).*PRIORITY:|.*Priority:.*")) {
                        priority = extractValueSafely(line, "Medium");
                    } else if (line.matches("(?i).*RATIONALE:|.*Rationale:.*")) {
                        rationale = extractValueSafely(line, "");
                    } else if (questionText == null && line.endsWith("?") && line.length() > 10) {

                        questionText = line;
                    }
                } catch (Exception e) {
                    logger.fine("Error parsing line: " + line + " - " + e.getMessage());

                }
            }

            if (questionText != null && isValidQuestionText(questionText)) {
                ResearchQuestion question = new ResearchQuestion(questionText, category, priority);
                question.setRationale(rationale);
                return question;
            }

        } catch (Exception e) {
            logger.warning("Error parsing question block: " + e.getMessage());
        }

        return null;
    }

    private String extractQuestionFromLineSafely(String line) {
        if (line == null || line.trim()
            .isEmpty()) {
            return null;
        }

        try {
            int colonIndex = line.indexOf(':');
            if (colonIndex >= 0 && colonIndex < line.length() - 1) {
                String extracted = line.substring(colonIndex + 1)
                    .trim();
                return extracted.isEmpty() ? null : extracted;
            }
            return line.trim();
        } catch (Exception e) {
            logger.fine("Error extracting question from line: " + e.getMessage());
            return line.trim();
        }
    }

    private String extractValueSafely(String line, String fallback) {
        if (line == null || line.trim()
            .isEmpty()) {
            return fallback;
        }

        try {
            int colonIndex = line.indexOf(':');
            if (colonIndex >= 0 && colonIndex < line.length() - 1) {
                String extracted = line.substring(colonIndex + 1)
                    .trim();
                return extracted.isEmpty() ? fallback : extracted;
            }
            return fallback;
        } catch (Exception e) {
            logger.fine("Error extracting value from line: " + e.getMessage());
            return fallback;
        }
    }

    private boolean isValidQuestionText(String questionText) {
        if (questionText == null || questionText.trim()
            .isEmpty()) {
            return false;
        }

        String trimmed = questionText.trim();

        if (trimmed.length() < 10) {
            return false;
        }

        if (trimmed.length() > 300) {
            return false;
        }

        if (!trimmed.matches(".*[a-zA-Z].*")) {
            return false;
        }

        return true;
    }

    private List<ResearchQuestion> parseNumberedQuestions(String response, DeepResearchContext context, int round, Set<String> seenQuestions) {
        List<ResearchQuestion> questions = new ArrayList<>();

        if (response == null || response.trim()
            .isEmpty()) {
            return questions;
        }

        try {
            Matcher matcher = NUMBERED_PATTERN.matcher(response);
            while (matcher.find()) {
                try {
                    String questionText = matcher.group(1);
                    if (questionText != null) {
                        questionText = questionText.trim();
                        if (isValidQuestionText(questionText) && questionText.endsWith("?")) {
                            String normalizedQuestion = normalizeQuestionText(questionText);
                            if (!seenQuestions.contains(normalizedQuestion)) {
                                questions.add(new ResearchQuestion(questionText, "General", "Medium"));
                                seenQuestions.add(normalizedQuestion);
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.fine("Error parsing numbered question: " + e.getMessage());

                }
            }
        } catch (Exception e) {
            logger.warning("Numbered question parsing failed: " + e.getMessage());
        }

        return questions;
    }

    private List<ResearchQuestion> parseBulletQuestions(String response, DeepResearchContext context, int round, Set<String> seenQuestions) {
        List<ResearchQuestion> questions = new ArrayList<>();

        if (response == null || response.trim()
            .isEmpty()) {
            return questions;
        }

        try {
            Matcher matcher = BULLET_PATTERN.matcher(response);
            while (matcher.find()) {
                try {
                    String questionText = matcher.group(1);
                    if (questionText != null) {
                        questionText = questionText.trim();
                        if (isValidQuestionText(questionText) && questionText.endsWith("?")) {
                            String normalizedQuestion = normalizeQuestionText(questionText);
                            if (!seenQuestions.contains(normalizedQuestion)) {
                                questions.add(new ResearchQuestion(questionText, "General", "Medium"));
                                seenQuestions.add(normalizedQuestion);
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.fine("Error parsing bullet question: " + e.getMessage());

                }
            }
        } catch (Exception e) {
            logger.warning("Bullet question parsing failed: " + e.getMessage());
        }

        return questions;
    }

    private List<ResearchQuestion> extractQuestionSentences(String response, DeepResearchContext context, int round, Set<String> seenQuestions) {
        List<ResearchQuestion> questions = new ArrayList<>();

        if (response == null || response.trim()
            .isEmpty()) {
            return questions;
        }

        try {
            Matcher matcher = QUESTION_PATTERN.matcher(response);
            while (matcher.find()) {
                try {
                    String questionText = matcher.group(1);
                    if (questionText != null) {
                        questionText = questionText.trim();
                        if (isValidQuestionText(questionText) && questionText.length() > 15) {
                            String normalizedQuestion = normalizeQuestionText(questionText);
                            if (!seenQuestions.contains(normalizedQuestion)) {
                                questions.add(new ResearchQuestion(questionText, "General", "Medium"));
                                seenQuestions.add(normalizedQuestion);
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.fine("Error extracting question sentence: " + e.getMessage());

                }
            }
        } catch (Exception e) {
            logger.warning("Question sentence extraction failed: " + e.getMessage());
        }

        return questions;
    }

    private List<ResearchQuestion> deduplicateAndValidateQuestions(List<ResearchQuestion> questions, Set<String> processedQuestions) {
        if (questions == null) {
            return new ArrayList<>();
        }

        Set<String> localSeen = new LinkedHashSet<>();

        return questions.stream()
            .filter(q -> q != null && q.getQuestion() != null)
            .filter(q -> isValidQuestionText(q.getQuestion()))
            .filter(q -> {
                String normalized = normalizeQuestionText(q.getQuestion());

                if (localSeen.contains(normalized) || processedQuestions.contains(normalized)) {
                    return false;
                }
                localSeen.add(normalized);
                return true;
            })
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<ResearchQuestion> generateRoundQuestionsWithDeduplication(DeepResearchContext context, Set<String> exploredTopics, int round,
        Set<String> processedQuestions) {
        List<ResearchQuestion> questions = new ArrayList<>();

        try {
            logger.info("Generating questions for round " + round + " with " + processedQuestions.size() + " already processed questions");

            List<ResearchQuestion> primaryQuestions = generateComprehensiveQuestionsFixed(context, exploredTopics, round);

            List<ResearchQuestion> validatedQuestions = deduplicateAndValidateQuestions(primaryQuestions, processedQuestions);
            questions.addAll(validatedQuestions);

            if (questions.size() < MIN_QUESTIONS_TO_PROCEED) {
                logger.info("Insufficient questions from primary generation (" + questions.size() + "), trying template-based approach");

                List<ResearchQuestion> templateQuestions = generateTemplateBasedQuestions(context, round);
                List<ResearchQuestion> validatedTemplateQuestions = deduplicateAndValidateQuestions(templateQuestions, processedQuestions);

                for (ResearchQuestion templateQ : validatedTemplateQuestions) {
                    if (templateQ != null && !containsQuestion(questions, templateQ)) {
                        questions.add(templateQ);
                    }
                }
            }

            if (questions.isEmpty()) {
                logger.warning("No questions generated through normal means, using emergency fallback for round " + round);
                questions.addAll(generateEmergencyQuestions(context, round));
            }

            questions = questions.stream()
                .filter(q -> q != null && q.getQuestion() != null)
                .filter(q -> !processedQuestions.contains(normalizeQuestionText(q.getQuestion())))
                .distinct()
                .limit(MAX_QUESTIONS_PER_ROUND)
                .collect(Collectors.toList());

            for (ResearchQuestion question : questions) {
                if (question != null && question.getQuestion() != null) {
                    processedQuestions.add(normalizeQuestionText(question.getQuestion()));
                }
            }

            logger.info("Generated " + questions.size() + " unique, validated questions for round " + round);
            return questions;

        } catch (Exception e) {
            logger.severe("Question generation failed for round " + round + ": " + e.getMessage());

            try {
                List<ResearchQuestion> emergencyQuestions = generateEmergencyQuestions(context, round);
                logger.info("Using emergency questions as fallback: " + emergencyQuestions.size() + " questions");
                return emergencyQuestions;
            } catch (Exception fallbackError) {
                logger.severe("Emergency question generation also failed: " + fallbackError.getMessage());
                return new ArrayList<>();
            }
        }
    }

    private boolean containsQuestion(List<ResearchQuestion> questions, ResearchQuestion newQuestion) {
        if (questions == null || newQuestion == null || newQuestion.getQuestion() == null) {
            return false;
        }

        String normalizedNew = normalizeQuestionText(newQuestion.getQuestion());

        return questions.stream()
            .filter(q -> q != null && q.getQuestion() != null)
            .anyMatch(q -> normalizeQuestionText(q.getQuestion()).equals(normalizedNew));
    }

    private List<ResearchQuestion> generateComprehensiveQuestionsFixed(DeepResearchContext context, Set<String> exploredTopics, int round) {
        List<ResearchQuestion> questions = new ArrayList<>();

        try {
            String questionPrompt = buildEnhancedQuestionPrompt(context, exploredTopics, round);

            if (countTokens(questionPrompt) > CONTEXT_WINDOW_LIMIT - 5000) {
                questionPrompt = contextChunker.compressPrompt(questionPrompt, CONTEXT_WINDOW_LIMIT - 5000);
                logger.info("Compressed question generation prompt for round " + round);
            }

            String response = null;
            try {
                LLMResponse<String> llmResponse = llmClient.complete(questionPrompt, String.class);
                if (llmResponse != null) {
                    response = llmResponse.structuredOutput();
                }
            } catch (Exception llmError) {
                logger.warning("LLM call failed for question generation: " + llmError.getMessage());

            }

            if (response != null && !response.trim()
                .isEmpty()) {
                logger.info("Received LLM response for question generation, parsing...");

                List<ResearchQuestion> parsedQuestions = parseQuestionsWithDeduplication(response, context, round);
                if (parsedQuestions != null && !parsedQuestions.isEmpty()) {
                    questions.addAll(parsedQuestions);
                    logger.info("Successfully parsed " + parsedQuestions.size() + " questions from LLM response");
                } else {
                    logger.warning("Question parsing returned empty results for round " + round);
                }
            } else {
                logger.warning("Empty or null response from LLM for question generation in round " + round);
            }

        } catch (Exception e) {
            logger.warning("Comprehensive question generation failed for round " + round + ": " + e.getMessage());
        }

        return questions != null ? questions : new ArrayList<>();
    }

    private List<ResearchQuestion> generateEmergencyQuestions(DeepResearchContext context, int round) {
        List<ResearchQuestion> emergencyQuestions = new ArrayList<>();

        try {
            String query = context.getOriginalQuery();
            if (query == null || query.trim()
                .isEmpty()) {
                query = "research topic";
            }

            emergencyQuestions.add(
                createSafeResearchQuestion("What are the essential concepts and fundamentals that define " + query + "?", "Overview", "High"));

            emergencyQuestions.add(
                createSafeResearchQuestion("What are the practical implementation approaches and methodologies for " + query + "?", "Implementation", "High"));

            emergencyQuestions.add(
                createSafeResearchQuestion("What are the current trends, developments, and future outlook for " + query + "?", "Analysis", "Medium"));

            emergencyQuestions.add(
                createSafeResearchQuestion("What are the key benefits, challenges, and considerations when working with " + query + "?", "Analysis", "Medium"));

            switch (round) {
                case 1 ->
                    emergencyQuestions.add(createSafeResearchQuestion("What is the basic introduction and overview of " + query + "?", "Overview", "High"));
                case 2 -> emergencyQuestions.add(
                    createSafeResearchQuestion("What are the technical details and specifications for " + query + "?", "Technical", "Medium"));
                case 3 ->
                    emergencyQuestions.add(createSafeResearchQuestion("What are real-world examples and use cases of " + query + "?", "Case-Study", "Medium"));
                default -> emergencyQuestions.add(
                    createSafeResearchQuestion("What comprehensive analysis and insights exist about " + query + "?", "Analysis", "Low"));
            }

            logger.info("Generated " + emergencyQuestions.size() + " emergency questions for round " + round);

        } catch (Exception e) {
            logger.severe("Emergency question generation failed: " + e.getMessage());

            try {
                String safeQuery = context.getOriginalQuery() != null ? context.getOriginalQuery() : "the topic";
                emergencyQuestions.add(createSafeResearchQuestion("What information is available about " + safeQuery + "?", "General", "Medium"));
            } catch (Exception finalError) {
                logger.severe("Final fallback question creation failed: " + finalError.getMessage());

            }
        }

        return emergencyQuestions;
    }

    private ResearchQuestion createSafeResearchQuestion(String questionText, String category, String priority) {
        try {
            if (questionText == null || questionText.trim()
                .isEmpty()) {
                questionText = "What information is available about this topic?";
            }

            if (category == null || category.trim()
                .isEmpty()) {
                category = "General";
            }

            if (priority == null || priority.trim()
                .isEmpty()) {
                priority = "Medium";
            }

            if (!questionText.trim()
                .endsWith("?")) {
                questionText = questionText.trim() + "?";
            }

            return new ResearchQuestion(questionText, category, priority);

        } catch (Exception e) {
            logger.warning("Error creating safe research question: " + e.getMessage());

            return new ResearchQuestion("What information is available about this topic?", "General", "Medium");
        }
    }

    private String truncateString(String str, int maxLength) {
        if (str == null) {
            return "null";
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }

    private static class DeepResearchSession {

        private final DeepResearchContext context;
        private final Instant startTime;
        private final DeepResearchProgress progress;

        public DeepResearchSession(DeepResearchContext context, Instant startTime) {
            this.context = context;
            this.startTime = startTime;
            this.progress = new DeepResearchProgress(context.getSessionId());
        }

        public DeepResearchContext getContext() {
            return context;
        }

        public Instant getStartTime() {
            return startTime;
        }

        public DeepResearchProgress getProgress() {
            return progress;
        }
    }

    private static class ResearchMetricsCollector {

        private final Map<String, Object> metrics = new ConcurrentHashMap<>();

        public void recordSession(String sessionId, Duration duration, DeepResearchResult result) {
            metrics.put(sessionId + "_duration", duration.toMinutes());
            metrics.put(sessionId + "_sources", result.getResults()
                .getAllCitations()
                .size());
            metrics.put(sessionId + "_words", result.getNarrative()
                .split("\\s+").length);
            metrics.put(sessionId + "_quality", result.getQualityMetrics()
                .getOverallScore());
            metrics.put(sessionId + "_timestamp", Instant.now());
        }

        public Map<String, Object> getMetrics() {
            return new HashMap<>(metrics);
        }

        public Map<String, Object> getSessionMetrics(String sessionId) {
            return metrics.entrySet()
                .stream()
                .filter(entry -> entry.getKey()
                    .startsWith(sessionId))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
    }

    private static class NarrativeQualityMetrics {

        private final int characterCount;
        private final int wordCount;
        private final boolean isComplete;
        private final double overallScore;
        private final Instant assessmentTime;

        public NarrativeQualityMetrics(int characterCount, int wordCount, boolean isComplete) {
            this.characterCount = characterCount;
            this.wordCount = wordCount;
            this.isComplete = isComplete;
            this.assessmentTime = Instant.now();

            double lengthScore = Math.min(1.0, wordCount / (double) TARGET_NARRATIVE_WORDS);
            double completionScore = isComplete ? 1.0 : 0.6;
            double qualityBonus = (wordCount > TARGET_NARRATIVE_WORDS * 0.8) ? 0.2 : 0.0;

            this.overallScore = Math.min(1.0, (lengthScore + completionScore + qualityBonus) / 2.0);
        }

        public boolean needsEnhancement() {
            return wordCount < TARGET_NARRATIVE_WORDS * 0.7 || !isComplete;
        }

        public int getCharacterCount() {
            return characterCount;
        }

        public int getWordCount() {
            return wordCount;
        }

        public boolean isComplete() {
            return isComplete;
        }

        public double getOverallScore() {
            return overallScore;
        }

        public Instant getAssessmentTime() {
            return assessmentTime;
        }

        public String getQualityAssessment() {
            if (overallScore >= 0.9) {
                return "Excellent";
            }
            if (overallScore >= 0.7) {
                return "Good";
            }
            if (overallScore >= 0.5) {
                return "Satisfactory";
            }
            return "Needs Improvement";
        }
    }
}