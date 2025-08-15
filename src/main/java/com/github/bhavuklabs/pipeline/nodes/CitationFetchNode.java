package com.github.bhavuklabs.pipeline.nodes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.github.bhavuklabs.citation.CitationResult;
import com.github.bhavuklabs.citation.service.CitationService;
import com.github.bhavuklabs.pipeline.graph.GraphNode;
import com.github.bhavuklabs.pipeline.models.QueryAnalysis;
import com.github.bhavuklabs.pipeline.profile.UserProfile;
import com.github.bhavuklabs.pipeline.state.ResearchAgentState;

public class CitationFetchNode implements GraphNode<ResearchAgentState> {

    private static final Logger logger = Logger.getLogger(CitationFetchNode.class.getName());

    private final CitationService citationService;
    private final ExecutorService executor;
    private final Random random;

    private static final int MIN_CITATIONS_SIMPLE = 3;
    private static final int MIN_CITATIONS_MODERATE = 8;
    private static final int MIN_CITATIONS_COMPLEX = 15;
    private static final int MAX_CITATIONS_PER_BATCH = 20;
    private static final int MAX_TOTAL_BATCHES = 5;
    private static final double QUALITY_THRESHOLD = 0.7;
    private static final double DIVERSITY_THRESHOLD = 0.6;

    public CitationFetchNode(CitationService citationService) {
        if (citationService == null) {
            throw new IllegalArgumentException("Citation service cannot be null");
        }
        this.citationService = citationService;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.random = new Random();
    }

    @Override
    public CompletableFuture<ResearchAgentState> process(ResearchAgentState state) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("Starting enhanced citation fetch for query: " + truncateString(state.getQuery(), 100));

                FetchingStrategy strategy = determineFetchingStrategy(state);
                logger.info("Using fetching strategy: " + strategy);

                List<CitationResult> allCitations = executeAdaptiveFetching(state, strategy);

                List<CitationResult> finalCitations = optimizeCitationSet(allCitations, state, strategy);

                logger.info(
                    "Citation fetch completed - gathered " + allCitations.size() + " total sources, selected " + finalCitations.size() + " optimal citations");

                return state.withCitations(finalCitations);

            } catch (Exception e) {
                logger.severe("Error in enhanced citation fetch: " + e.getMessage());
                return state.withError(e);
            }
        }, executor);
    }

    private FetchingStrategy determineFetchingStrategy(ResearchAgentState state) {
        FetchingStrategy strategy = new FetchingStrategy();

        QueryAnalysis analysis = getQueryAnalysis(state);
        UserProfile profile = state.getUserProfile();
        boolean isImprovementRound = state.getMetadata()
            .containsKey("citation_improvement_round");

        if (analysis != null) {
            switch (analysis.complexityScore) {
                case 1, 2, 3 -> {
                    strategy.targetCitations = MIN_CITATIONS_SIMPLE;
                    strategy.maxBatches = 2;
                    strategy.diversificationLevel = DiversificationLevel.BASIC;
                }
                case 4, 5, 6 -> {
                    strategy.targetCitations = MIN_CITATIONS_MODERATE;
                    strategy.maxBatches = 3;
                    strategy.diversificationLevel = DiversificationLevel.MODERATE;
                }
                default -> {
                    strategy.targetCitations = MIN_CITATIONS_COMPLEX;
                    strategy.maxBatches = MAX_TOTAL_BATCHES;
                    strategy.diversificationLevel = DiversificationLevel.COMPREHENSIVE;
                }
            }

            switch (analysis.intent) {
                case "comparison" -> {
                    strategy.targetCitations = Math.max(strategy.targetCitations, 12);
                    strategy.diversificationLevel = DiversificationLevel.COMPREHENSIVE;
                    strategy.requireDomainDiversity = true;
                }
                case "research" -> {
                    strategy.targetCitations = Math.max(strategy.targetCitations, 10);
                    strategy.prioritizeAuthoritative = true;
                }
                case "analysis" -> {
                    strategy.targetCitations = Math.max(strategy.targetCitations, 8);
                    strategy.requireMultiplePerspectives = true;
                }
            }
        }

        if (profile != null) {
            if ("expert".equals(profile.getExpertiseLevel())) {
                strategy.targetCitations = Math.max(strategy.targetCitations, 12);
                strategy.prioritizeAuthoritative = true;
            }

            if (profile.hasPreference("comprehensive") || profile.hasPreference("detailed")) {
                strategy.targetCitations = Math.max(strategy.targetCitations, 15);
                strategy.maxBatches = MAX_TOTAL_BATCHES;
            }
        }

        if (isImprovementRound) {
            strategy.targetCitations = Math.max(strategy.targetCitations + 5, 20);
            strategy.diversificationLevel = DiversificationLevel.COMPREHENSIVE;
            strategy.useAlternativeQueries = true;
            logger.info("Improvement round detected - increasing target citations to " + strategy.targetCitations);
        }

        return strategy;
    }

    private List<CitationResult> executeAdaptiveFetching(ResearchAgentState state, FetchingStrategy strategy) throws Exception {
        List<CitationResult> allCitations = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();

        List<String> queryVariations = generateQueryVariations(state, strategy);
        logger.info("Generated " + queryVariations.size() + " query variations for comprehensive search");

        int batchCount = 0;
        boolean shouldContinue = true;

        while (shouldContinue && batchCount < strategy.maxBatches) {
            batchCount++;
            logger.info("Executing citation batch " + batchCount + "/" + strategy.maxBatches);

            List<String> batchQueries = selectBatchQueries(queryVariations, batchCount, strategy);

            List<CompletableFuture<List<CitationResult>>> batchFutures = batchQueries.stream()
                .map(query -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return citationService.search(query);
                    } catch (Exception e) {
                        logger.warning("Failed to fetch citations for query: " + query + " - " + e.getMessage());
                        return List.<CitationResult> of();
                    }
                }, executor))
                .collect(Collectors.toList());

            for (CompletableFuture<List<CitationResult>> future : batchFutures) {
                try {
                    List<CitationResult> batchResults = future.get(30, TimeUnit.SECONDS);

                    for (CitationResult citation : batchResults) {
                        if (citation != null && citation.isValid() && !seenUrls.contains(citation.getUrl())) {
                            seenUrls.add(citation.getUrl());
                            allCitations.add(citation);
                        }
                    }
                } catch (Exception e) {
                    logger.warning("Batch future failed: " + e.getMessage());
                }
            }

            CitationQualityAssessment assessment = assessCitationQuality(allCitations, state, strategy);
            shouldContinue = shouldContinueFetching(assessment, strategy, batchCount);

            logger.info("Batch " + batchCount + " completed - Total citations: " + allCitations.size() + ", Quality score: " +
                String.format("%.2f", assessment.overallQuality) + ", Continue: " + shouldContinue);
        }

        return allCitations;
    }

    private List<String> generateQueryVariations(ResearchAgentState state, FetchingStrategy strategy) {
        List<String> variations = new ArrayList<>();
        String originalQuery = state.getQuery();

        variations.add(originalQuery);

        QueryAnalysis analysis = getQueryAnalysis(state);

        switch (strategy.diversificationLevel) {
            case COMPREHENSIVE -> {
                variations.addAll(generateComprehensiveVariations(originalQuery, analysis));
            }
            case MODERATE -> {
                variations.addAll(generateModerateVariations(originalQuery, analysis));
            }
            case BASIC -> {
                variations.addAll(generateBasicVariations(originalQuery, analysis));
            }
        }

        if (strategy.requireDomainDiversity) {
            variations.addAll(generateDomainSpecificVariations(originalQuery, state.getUserProfile()));
        }

        if (strategy.useAlternativeQueries) {
            variations.addAll(generateAlternativeQueryFormats(originalQuery, analysis));
        }

        return variations.stream()
            .distinct()
            .filter(query -> query != null && !query.trim()
                .isEmpty())
            .limit(20)
            .collect(Collectors.toList());
    }

    private List<String> generateComprehensiveVariations(String originalQuery, QueryAnalysis analysis) {
        List<String> variations = new ArrayList<>();

        variations.add(originalQuery + " comprehensive guide");
        variations.add(originalQuery + " detailed analysis");
        variations.add(originalQuery + " best practices");
        variations.add(originalQuery + " latest research");
        variations.add(originalQuery + " expert opinion");

        variations.add("What is " + originalQuery + "?");
        variations.add("How does " + originalQuery + " work?");
        variations.add("Why is " + originalQuery + " important?");

        variations.add(originalQuery + " comparison");
        variations.add(originalQuery + " alternatives");
        variations.add(originalQuery + " pros and cons");

        variations.add(originalQuery + " research papers");
        variations.add(originalQuery + " academic study");
        variations.add(originalQuery + " scientific evidence");

        return variations;
    }

    private List<String> generateModerateVariations(String originalQuery, QueryAnalysis analysis) {
        List<String> variations = new ArrayList<>();

        variations.add(originalQuery + " overview");
        variations.add(originalQuery + " explanation");
        variations.add(originalQuery + " examples");
        variations.add("understanding " + originalQuery);
        variations.add(originalQuery + " guide");

        return variations;
    }

    private List<String> generateBasicVariations(String originalQuery, QueryAnalysis analysis) {
        List<String> variations = new ArrayList<>();

        variations.add(originalQuery + " definition");
        variations.add("what is " + originalQuery);
        variations.add(originalQuery + " basics");

        return variations;
    }

    private List<String> generateDomainSpecificVariations(String originalQuery, UserProfile profile) {
        List<String> variations = new ArrayList<>();

        if (profile != null && profile.getDomain() != null) {
            String domain = profile.getDomain();
            variations.add(originalQuery + " in " + domain);
            variations.add(domain + " " + originalQuery);
            variations.add(originalQuery + " " + domain + " perspective");
        }

        variations.add(originalQuery + " business application");
        variations.add(originalQuery + " technical implementation");
        variations.add(originalQuery + " academic research");

        return variations;
    }

    private List<String> generateAlternativeQueryFormats(String originalQuery, QueryAnalysis analysis) {
        List<String> variations = new ArrayList<>();

        variations.add("explain " + originalQuery);
        variations.add("discuss " + originalQuery);
        variations.add("analyze " + originalQuery);
        variations.add("examine " + originalQuery);

        variations.add(originalQuery + " recent developments");
        variations.add(originalQuery + " current trends");
        variations.add(originalQuery + " future outlook");

        return variations;
    }

    private List<String> selectBatchQueries(List<String> allVariations, int batchNumber, FetchingStrategy strategy) {
        List<String> selectedQueries = new ArrayList<>();

        if (batchNumber == 1) {
            selectedQueries.add(allVariations.get(0));

            List<String> remaining = new ArrayList<>(allVariations.subList(1, allVariations.size()));
            Collections.shuffle(remaining, random);
            selectedQueries.addAll(remaining.subList(0, Math.min(2, remaining.size())));
        } else {

            List<String> availableQueries = new ArrayList<>(allVariations);
            Collections.shuffle(availableQueries, random);

            int queriesPerBatch = Math.min(3, availableQueries.size());
            selectedQueries.addAll(availableQueries.subList(0, queriesPerBatch));
        }

        return selectedQueries;
    }

    private CitationQualityAssessment assessCitationQuality(List<CitationResult> citations, ResearchAgentState state, FetchingStrategy strategy) {
        CitationQualityAssessment assessment = new CitationQualityAssessment();

        if (citations.isEmpty()) {
            return assessment;
        }

        double totalRelevance = citations.stream()
            .mapToDouble(CitationResult::getRelevanceScore)
            .sum();
        assessment.averageRelevance = totalRelevance / citations.size();

        Set<String> uniqueDomains = citations.stream()
            .map(CitationResult::getDomain)
            .collect(Collectors.toSet());
        assessment.domainDiversity = (double) uniqueDomains.size() / Math.max(citations.size(), 1);

        int totalContentLength = citations.stream()
            .mapToInt(citation -> citation.getContent() != null ? citation.getContent()
                .length() : 0)
            .sum();
        assessment.contentRichness = Math.min(1.0, totalContentLength / (citations.size() * 2000.0));

        long highQualityCitations = citations.stream()
            .mapToLong(citation -> citation.getRelevanceScore() >= 0.7 ? 1 : 0)
            .sum();
        assessment.highQualityRatio = (double) highQualityCitations / citations.size();

        assessment.overallQuality =
            (assessment.averageRelevance * 0.4) + (assessment.domainDiversity * 0.2) + (assessment.contentRichness * 0.2) + (assessment.highQualityRatio * 0.2);

        assessment.totalCount = citations.size();
        assessment.meetsTargetCount = citations.size() >= strategy.targetCitations;

        return assessment;
    }

    private boolean shouldContinueFetching(CitationQualityAssessment assessment, FetchingStrategy strategy, int currentBatch) {

        if (currentBatch >= strategy.maxBatches) {
            return false;
        }

        if (assessment.totalCount < strategy.targetCitations) {
            return true;
        }

        if (assessment.overallQuality < QUALITY_THRESHOLD && assessment.totalCount > 0) {
            return true;
        }

        if (strategy.requireDomainDiversity && assessment.domainDiversity < DIVERSITY_THRESHOLD) {
            return true;
        }

        if (strategy.prioritizeAuthoritative && assessment.highQualityRatio < 0.6) {
            return true;
        }

        return false;
    }

    private List<CitationResult> optimizeCitationSet(List<CitationResult> allCitations, ResearchAgentState state, FetchingStrategy strategy) {
        if (allCitations.isEmpty()) {
            logger.warning("No citations found to optimize");
            return allCitations;
        }

        logger.info("Optimizing citation set from " + allCitations.size() + " total sources");

        List<CitationResult> filtered = applyIntelligentFiltering(allCitations, state);

        List<CitationResult> randomized = applyRelevanceWeightedRandomization(filtered, strategy);

        if (strategy.requireDomainDiversity) {
            randomized = ensureDomainDiversity(randomized, strategy.targetCitations);
        }

        List<CitationResult> finalSet = selectFinalCitations(randomized, strategy);

        logger.info("Citation optimization completed - final set size: " + finalSet.size());
        return finalSet;
    }

    private List<CitationResult> applyIntelligentFiltering(List<CitationResult> citations, ResearchAgentState state) {
        return citations.stream()
            .filter(citation -> {

                if (citation.getRelevanceScore() < 0.3) {
                    return false;
                }

                if (citation.getContent() == null || citation.getContent()
                    .length() < 100) {
                    return false;
                }

                return true;
            })
            .collect(Collectors.toList());
    }

    private List<CitationResult> applyRelevanceWeightedRandomization(List<CitationResult> citations, FetchingStrategy strategy) {
        if (citations.size() <= strategy.targetCitations) {
            return new ArrayList<>(citations);
        }

        List<WeightedCitation> weightedCitations = citations.stream()
            .map(citation -> {

                double weight = Math.pow(citation.getRelevanceScore(), 2.0) + 0.1;
                return new WeightedCitation(citation, weight);
            })
            .collect(Collectors.toList());

        List<CitationResult> selected = new ArrayList<>();
        List<WeightedCitation> remaining = new ArrayList<>(weightedCitations);

        while (selected.size() < strategy.targetCitations && !remaining.isEmpty()) {
            double totalWeight = remaining.stream()
                .mapToDouble(wc -> wc.weight)
                .sum();
            double randomValue = random.nextDouble() * totalWeight;

            double currentWeight = 0;
            WeightedCitation selectedCitation = null;

            for (WeightedCitation wc : remaining) {
                currentWeight += wc.weight;
                if (currentWeight >= randomValue) {
                    selectedCitation = wc;
                    break;
                }
            }

            if (selectedCitation != null) {
                selected.add(selectedCitation.citation);
                remaining.remove(selectedCitation);
            } else {

                WeightedCitation fallback = remaining.get(random.nextInt(remaining.size()));
                selected.add(fallback.citation);
                remaining.remove(fallback);
            }
        }

        logger.info("Applied relevance-weighted randomization - selected " + selected.size() + " citations");
        return selected;
    }

    private List<CitationResult> ensureDomainDiversity(List<CitationResult> citations, int targetCount) {
        Map<String, List<CitationResult>> citationsByDomain = citations.stream()
            .collect(Collectors.groupingBy(CitationResult::getDomain));

        List<CitationResult> diversified = new ArrayList<>();
        List<String> domains = new ArrayList<>(citationsByDomain.keySet());

        int maxPerDomain = Math.max(1, targetCount / domains.size());

        for (String domain : domains) {
            List<CitationResult> domainCitations = citationsByDomain.get(domain);
            int toTake = Math.min(maxPerDomain, domainCitations.size());
            diversified.addAll(domainCitations.subList(0, toTake));

            if (diversified.size() >= targetCount) {
                break;
            }
        }

        while (diversified.size() < targetCount && diversified.size() < citations.size()) {
            for (CitationResult citation : citations) {
                if (!diversified.contains(citation)) {
                    diversified.add(citation);
                    if (diversified.size() >= targetCount) {
                        break;
                    }
                }
            }
            break;
        }

        return diversified;
    }

    private List<CitationResult> selectFinalCitations(List<CitationResult> citations, FetchingStrategy strategy) {

        return citations.stream()
            .sorted((a, b) -> {

                int relevanceCompare = Double.compare(b.getRelevanceScore(), a.getRelevanceScore());
                if (Math.abs(a.getRelevanceScore() - b.getRelevanceScore()) < 0.1) {

                    return random.nextBoolean() ? 1 : -1;
                }
                return relevanceCompare;
            })
            .limit(strategy.targetCitations)
            .collect(Collectors.toList());
    }

    private List<CitationResult> filterCitationsForUser(List<CitationResult> citations, UserProfile profile) {
        if (citations == null || citations.isEmpty() || profile == null) {
            return citations != null ? citations : List.of();
        }

        return citations.stream()
            .filter(citation -> citation != null && citation.isValid())
            .sorted((a, b) -> {
                int scoreA = calculateUserRelevanceScore(a, profile);
                int scoreB = calculateUserRelevanceScore(b, profile);
                return Integer.compare(scoreB, scoreA);
            })
            .collect(Collectors.toList());
    }

    private int calculateUserRelevanceScore(CitationResult citation, UserProfile profile) {
        if (citation.getContent() == null || citation.getTitle() == null) {
            return (int) (citation.getRelevanceScore() * 50);
        }

        double score = citation.getRelevanceScore() * 100;
        String content = (citation.getContent() + " " + citation.getTitle()).toLowerCase();

        if (profile.getDomain() != null && content.contains(profile.getDomain()
            .toLowerCase())) {
            score += 25;
        }

        if (profile.getTopicInterests() != null) {
            for (Map.Entry<String, Integer> topic : profile.getTopicInterests()
                .entrySet()) {
                if (content.contains(topic.getKey()
                    .toLowerCase())) {
                    score += topic.getValue() * 2;
                }
            }
        }

        if (content.contains("implementation") || content.contains("example") || content.contains("code")) {
            score += 20;
        }

        if (content.contains("tutorial") || content.contains("guide") || content.contains("how-to")) {
            score += 15;
        }

        if (profile.getExpertiseLevel() != null) {
            switch (profile.getExpertiseLevel()) {
                case "expert" -> {
                    if (content.contains("advanced") || content.contains("architecture") || content.contains("design pattern")) {
                        score += 20;
                    }
                }
                case "beginner" -> {
                    if (content.contains("introduction") || content.contains("basics") || content.contains("getting started")) {
                        score += 20;
                    }
                }
                case "intermediate" -> {
                    if (content.contains("practical") || content.contains("example") || content.contains("step-by-step")) {
                        score += 15;
                    }
                }
            }
        }

        return (int) Math.min(100, Math.max(10, score));
    }

    private QueryAnalysis getQueryAnalysis(ResearchAgentState state) {
        try {
            Object analysis = state.getMetadata()
                .get("query_analysis");
            return analysis instanceof QueryAnalysis ? (QueryAnalysis) analysis : createDefaultQueryAnalysis();
        } catch (Exception e) {
            logger.warning("Error retrieving query analysis: " + e.getMessage());
            return createDefaultQueryAnalysis();
        }
    }

    private QueryAnalysis createDefaultQueryAnalysis() {
        return new QueryAnalysis() {
            public final boolean requiresCitations = true;
            public final String complexityLevel = "medium";
            
            @Override
            public String getAnalysisType() { return "default"; }
            
            @Override
            public Map<String, Object> getAnalysisData() { 
                return Map.of(
                    "requiresCitations", requiresCitations,
                    "complexityLevel", complexityLevel,
                    "type", "fallback"
                ); 
            }
        };
    }

    private String truncateString(String str, int maxLength) {
        if (str == null) {
            return "null";
        }
        return str.length() <= maxLength ? str : str.substring(0, maxLength) + "...";
    }

    @Override
    public String getName() {
        return "citation_fetch";
    }

    @Override
    public boolean shouldExecute(ResearchAgentState state) {
        try {
            QueryAnalysis analysis = getQueryAnalysis(state);

            if (analysis == null) {
                logger.info("No query analysis found, defaulting to fetch citations");
                return true;
            }

            boolean shouldFetch = analysis.requiresCitations;
            logger.info("Query analysis indicates citations required: " + shouldFetch);
            return shouldFetch;

        } catch (Exception e) {
            logger.warning("Error checking if citations should be fetched: " + e.getMessage());
            return true;
        }
    }

    public void shutdown() {
        try {
            executor.shutdown();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread()
                .interrupt();
        }
    }

    private static class FetchingStrategy {

        int targetCitations = MIN_CITATIONS_MODERATE;
        int maxBatches = 3;
        DiversificationLevel diversificationLevel = DiversificationLevel.MODERATE;
        boolean requireDomainDiversity = false;
        boolean prioritizeAuthoritative = false;
        boolean requireMultiplePerspectives = false;
        boolean useAlternativeQueries = false;

        @Override
        public String toString() {
            return String.format("FetchingStrategy[target=%d, batches=%d, level=%s]", targetCitations, maxBatches, diversificationLevel);
        }
    }

    private enum DiversificationLevel {
        BASIC,
        MODERATE,
        COMPREHENSIVE
    }

    private static class CitationQualityAssessment {

        double averageRelevance = 0.0;
        double domainDiversity = 0.0;
        double contentRichness = 0.0;
        double highQualityRatio = 0.0;
        double overallQuality = 0.0;
        int totalCount = 0;
        boolean meetsTargetCount = false;
    }

    private static class WeightedCitation {

        final CitationResult citation;
        final double weight;

        WeightedCitation(CitationResult citation, double weight) {
            this.citation = citation;
            this.weight = weight;
        }
    }
}