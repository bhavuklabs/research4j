package com.github.bhavuklabs.pipeline.router;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import com.github.bhavuklabs.pipeline.graph.GraphNode;
import com.github.bhavuklabs.pipeline.models.QueryAnalysis;
import com.github.bhavuklabs.pipeline.state.ResearchAgentState;

public class DynamicRouter {

    private static final Logger logger = Logger.getLogger(DynamicRouter.class.getName());
    private static final int MAX_RETRIES = 3;
    private static final int MAX_TOTAL_ITERATIONS = 15;
    private static final String RETRY_COUNT_KEY = "_retry_count";
    private static final String ITERATION_COUNT_KEY = "_iteration_count";
    private static final String VISITED_NODES_KEY = "_visited_nodes";
    private static final String INFORMATION_QUALITY_KEY = "_info_quality";

    private final Map<String, GraphNode<ResearchAgentState>> nodes = new HashMap<>();

    public DynamicRouter() {
        logger.info("Enhanced DynamicRouter initialized with adaptive traversal capabilities");
    }

    public void registerNode(String name, GraphNode<ResearchAgentState> node) {
        if (name == null || name.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("Node name cannot be null or empty");
        }
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }

        nodes.put(name, node);
        logger.info("Registered node: " + name);
    }

    public Map<String, GraphNode<ResearchAgentState>> getNodes() {
        return new HashMap<>(nodes);
    }

    @SuppressWarnings("unchecked")
    public List<String> determineNextNodes(ResearchAgentState state, String currentNode) {
        if (state == null) {
            logger.warning("State is null, returning end node");
            return List.of("end");
        }

        if (currentNode == null) {
            logger.warning("Current node is null, starting from beginning");
            initializeTraversalMetadata(state);
            return List.of("query_analysis");
        }

        int totalIterations = (Integer) state.getMetadata()
            .getOrDefault(ITERATION_COUNT_KEY, 0);
        if (totalIterations >= MAX_TOTAL_ITERATIONS) {
            logger.warning("Maximum iteration limit reached, forcing pipeline completion");
            return List.of("end");
        }

        updateTraversalMetadata(state, currentNode);

        logger.info("Determining next nodes from: " + currentNode + " (iteration: " + totalIterations + ")");

        InformationQualityAssessment quality = assessInformationQuality(state, currentNode);
        updateInformationQuality(state, currentNode, quality);

        Set<String> visitedNodes = (Set<String>) state.getMetadata()
            .getOrDefault(VISITED_NODES_KEY, new HashSet<String>());

        return switch (currentNode) {
            case "start" -> {
                logger.info("Starting adaptive pipeline with query analysis");
                yield List.of("query_analysis");
            }

            case "query_analysis" -> {
                QueryAnalysis analysis = getQueryAnalysis(state);
                if (analysis != null && analysis.complexityScore >= 7) {
                    logger.info("High complexity query detected, may require multiple citation rounds");
                }
                yield List.of("citation_fetch");
            }

            case "citation_fetch" -> {

                List<String> nextNodes = determineCitationNextSteps(state, quality, visitedNodes);
                if (!nextNodes.isEmpty()) {
                    yield nextNodes;
                }
                yield List.of("reasoning_selection");
            }

            case "reasoning_selection" -> {
                if (state.getSelectedReasoning() != null) {

                    if (shouldGatherMoreInformation(state, quality)) {
                        logger.info("Insufficient information detected, revisiting citation fetch");
                        yield List.of("citation_fetch");
                    }
                    yield List.of("reasoning_execution");
                } else {
                    logger.warning("No reasoning selected, ending pipeline");
                    yield List.of("end");
                }
            }

            case "reasoning_execution" -> {
                if (state.getFinalResponse() != null) {

                    if (shouldImproveResponse(state, quality, visitedNodes)) {
                        logger.info("Response quality below threshold, attempting improvement");
                        yield determineImprovementStrategy(state, quality, visitedNodes);
                    }
                    logger.info("Research pipeline completed successfully");
                    yield List.of("end");
                } else {
                    logger.warning("No response generated, attempting recovery");
                    if (visitedNodes.contains("reasoning_selection") && getRetryCount(state, "reasoning_selection") < MAX_RETRIES) {
                        yield List.of("reasoning_selection");
                    }
                    yield List.of("end");
                }
            }

            case "end" -> {
                logger.info("Pipeline execution completed");
                yield List.of();
            }

            default -> {
                logger.warning("Unknown node: " + currentNode + ", ending pipeline");
                yield List.of("end");
            }
        };
    }

    @SuppressWarnings("unchecked")
    private void initializeTraversalMetadata(ResearchAgentState state) {
        state.getMetadata()
            .put(ITERATION_COUNT_KEY, 0);
        state.getMetadata()
            .put(VISITED_NODES_KEY, new HashSet<String>());
        state.getMetadata()
            .put(INFORMATION_QUALITY_KEY, new HashMap<String, InformationQualityAssessment>());
    }

    @SuppressWarnings("unchecked")
    private void updateTraversalMetadata(ResearchAgentState state, String currentNode) {

        int iterations = (Integer) state.getMetadata()
            .getOrDefault(ITERATION_COUNT_KEY, 0);
        state.getMetadata()
            .put(ITERATION_COUNT_KEY, iterations + 1);

        Set<String> visitedNodes = (Set<String>) state.getMetadata()
            .getOrDefault(VISITED_NODES_KEY, new HashSet<String>());
        visitedNodes.add(currentNode);
        state.getMetadata()
            .put(VISITED_NODES_KEY, visitedNodes);
    }

    private List<String> determineCitationNextSteps(ResearchAgentState state, InformationQualityAssessment quality, Set<String> visitedNodes) {
        boolean hasEnoughCitations = state.getCitations() != null && state.getCitations()
            .size() >= 3;
        boolean citationQualityGood = quality.averageRelevanceScore >= 0.6;
        boolean complexQuery = isComplexQuery(state);

        if (complexQuery && (!hasEnoughCitations || !citationQualityGood)) {
            int citationRetries = getRetryCount(state, "citation_fetch");
            if (citationRetries < MAX_RETRIES) {
                logger.info("Complex query needs more citations (current: " + (state.getCitations() != null ? state.getCitations()
                    .size() : 0) + ", quality: " + quality.averageRelevanceScore + ")");
                return List.of("citation_fetch");
            }
        }

        if ((state.getCitations() == null || state.getCitations()
            .size() < 2) && getRetryCount(state, "citation_fetch") < 2) {
            logger.info("Insufficient citations found, retrying with broader search");
            return List.of("citation_fetch");
        }

        return List.of();
    }

    private boolean shouldGatherMoreInformation(ResearchAgentState state, InformationQualityAssessment quality) {

        if (isComplexQuery(state) && quality.overallQuality < 0.7) {
            return getRetryCount(state, "citation_fetch") < MAX_RETRIES;
        }

        QueryAnalysis analysis = getQueryAnalysis(state);
        if (analysis != null && "research".equals(analysis.intent) && (state.getCitations() == null || state.getCitations()
            .size() < 3)) {
            return getRetryCount(state, "citation_fetch") < 2;
        }

        return false;
    }

    private boolean shouldImproveResponse(ResearchAgentState state, InformationQualityAssessment quality, Set<String> visitedNodes) {

        if (getRetryCount(state, "reasoning_execution") >= 2) {
            return false;
        }

        if (quality.overallQuality < 0.6 && !visitedNodes.contains("citation_fetch_improvement")) {
            return true;
        }

        if (isComplexQuery(state) && (state.getCitations() == null || state.getCitations()
            .size() < 5)) {
            return getRetryCount(state, "citation_fetch") < 2;
        }

        return false;
    }

    private List<String> determineImprovementStrategy(ResearchAgentState state, InformationQualityAssessment quality, Set<String> visitedNodes) {

        if (quality.averageRelevanceScore < 0.6) {

            state.getMetadata()
                .put("citation_improvement_round", true);
            return List.of("citation_fetch");
        }

        if (quality.averageRelevanceScore >= 0.6) {
            return List.of("reasoning_selection");
        }

        return List.of("reasoning_selection");
    }

    private InformationQualityAssessment assessInformationQuality(ResearchAgentState state, String currentNode) {
        InformationQualityAssessment assessment = new InformationQualityAssessment();

        if (state.getCitations() != null && !state.getCitations()
            .isEmpty()) {

            double totalRelevance = state.getCitations()
                .stream()
                .mapToDouble(citation -> citation.getRelevanceScore())
                .sum();
            assessment.averageRelevanceScore = totalRelevance / state.getCitations()
                .size();

            long uniqueDomains = state.getCitations()
                .stream()
                .map(citation -> citation.getDomain())
                .distinct()
                .count();
            assessment.sourceDiversity = (double) uniqueDomains / state.getCitations()
                .size();

            int totalContentLength = state.getCitations()
                .stream()
                .mapToInt(citation -> citation.getContent() != null ? citation.getContent()
                    .length() : 0)
                .sum();
            assessment.contentRichness = Math.min(1.0, totalContentLength / 10000.0);

            assessment.overallQuality = (assessment.averageRelevanceScore * 0.5) + (assessment.sourceDiversity * 0.3) + (assessment.contentRichness * 0.2);
        } else {

            assessment.averageRelevanceScore = 0.0;
            assessment.sourceDiversity = 0.0;
            assessment.contentRichness = 0.0;
            assessment.overallQuality = 0.0;
        }

        assessment.citationCount = state.getCitations() != null ? state.getCitations()
            .size() : 0;
        assessment.nodeContext = currentNode;

        return assessment;
    }

    @SuppressWarnings("unchecked")
    private void updateInformationQuality(ResearchAgentState state, String nodeName, InformationQualityAssessment quality) {
        Map<String, InformationQualityAssessment> qualityMap = (Map<String, InformationQualityAssessment>) state.getMetadata()
            .getOrDefault(INFORMATION_QUALITY_KEY, new HashMap<>());
        qualityMap.put(nodeName, quality);
        state.getMetadata()
            .put(INFORMATION_QUALITY_KEY, qualityMap);
    }

    private boolean isComplexQuery(ResearchAgentState state) {
        QueryAnalysis analysis = getQueryAnalysis(state);
        if (analysis != null) {
            return analysis.complexityScore >= 6;
        }

        String query = state.getQuery()
            .toLowerCase();
        return query.length() > 100 || query.contains("comprehensive") || query.contains("detailed") || query.contains("compare") || query.contains("analyze");
    }

    private QueryAnalysis getQueryAnalysis(ResearchAgentState state) {
        try {
            Object analysis = state.getMetadata()
                .get("query_analysis");
            if (analysis instanceof QueryAnalysis) {
                return (QueryAnalysis) analysis;
            }
        } catch (Exception e) {
            logger.warning("Error retrieving query analysis: " + e.getMessage());
        }
        
        // Return a default QueryAnalysis instead of null
        return new QueryAnalysis() {
            @Override
            public String getAnalysisType() { return "default"; }
            
            @Override
            public Map<String, Object> getAnalysisData() { 
                return Map.of("type", "fallback", "complexity", "medium"); 
            }
        };
    }

    public void incrementRetryCount(ResearchAgentState state, String nodeName) {
        if (state != null && nodeName != null) {
            String retryKey = nodeName + RETRY_COUNT_KEY;
            int currentCount = (Integer) state.getMetadata()
                .getOrDefault(retryKey, 0);
            state.getMetadata()
                .put(retryKey, currentCount + 1);
            logger.info("Incremented retry count for node '" + nodeName + "' to " + (currentCount + 1));
        }
    }

    public boolean shouldRetry(ResearchAgentState state, String nodeName, Exception error) {
        if (state == null || nodeName == null || error == null) {
            return false;
        }

        String retryKey = nodeName + RETRY_COUNT_KEY;
        int currentRetries = (Integer) state.getMetadata()
            .getOrDefault(retryKey, 0);

        if (currentRetries >= MAX_RETRIES) {
            logger.warning("Max retries reached for node: " + nodeName + " (" + currentRetries + "/" + MAX_RETRIES + ")");
            return false;
        }

        int totalIterations = (Integer) state.getMetadata()
            .getOrDefault(ITERATION_COUNT_KEY, 0);
        if (totalIterations >= MAX_TOTAL_ITERATIONS) {
            logger.warning("Maximum total iterations reached, stopping retries for node: " + nodeName);
            return false;
        }

        boolean shouldRetry = isRetryableError(error);

        if (shouldRetry) {

            state.getMetadata()
                .put(retryKey, currentRetries + 1);
            logger.info("Retrying node: " + nodeName + " due to " + error.getClass()
                .getSimpleName() + " (attempt " + (currentRetries + 1) + "/" + MAX_RETRIES + ")");
        } else {
            logger.info("Non-retryable error for node: " + nodeName + " - " + error.getClass()
                .getSimpleName());
        }

        return shouldRetry;
    }

    private boolean isRetryableError(Exception error) {
        if (error instanceof java.net.SocketTimeoutException || error instanceof java.net.ConnectException || error instanceof java.io.IOException) {
            return true;
        }

        if (error.getMessage() != null) {
            String message = error.getMessage()
                .toLowerCase();
            if (message.contains("timeout") || message.contains("connection reset") || message.contains("service unavailable") ||
                message.contains("rate limit")) {
                return true;
            }
        }

        return false;
    }

    public boolean isValidTransition(String fromNode, String toNode) {
        if (fromNode == null || toNode == null) {
            return false;
        }

        Map<String, List<String>> validTransitions = Map.of("start", List.of("query_analysis"), "query_analysis", List.of("citation_fetch"), "citation_fetch",
            List.of("reasoning_selection", "citation_fetch"), "reasoning_selection", List.of("reasoning_execution", "citation_fetch"), "reasoning_execution",
            List.of("end", "reasoning_selection", "citation_fetch"), "end", List.of());

        return validTransitions.getOrDefault(fromNode, List.of())
            .contains(toNode);
    }

    public int getRetryCount(ResearchAgentState state, String nodeName) {
        if (state == null || nodeName == null) {
            return 0;
        }

        String retryKey = nodeName + RETRY_COUNT_KEY;
        return (Integer) state.getMetadata()
            .getOrDefault(retryKey, 0);
    }

    public void resetRetryCount(ResearchAgentState state, String nodeName) {
        if (state != null && nodeName != null) {
            String retryKey = nodeName + RETRY_COUNT_KEY;
            state.getMetadata()
                .remove(retryKey);
            logger.info("Reset retry count for node: " + nodeName);
        }
    }

    public void validateRouting(ResearchAgentState state) {
        if (state == null) {
            throw new IllegalStateException("State cannot be null for routing validation");
        }

        List<String> requiredNodes = List.of("query_analysis", "citation_fetch", "reasoning_selection", "reasoning_execution");

        for (String requiredNode : requiredNodes) {
            if (!nodes.containsKey(requiredNode)) {
                throw new IllegalStateException("Required node not registered: " + requiredNode);
            }
        }

        logger.info("Routing validation passed for session: " + state.getSessionId());
    }

    public static class InformationQualityAssessment {

        public double averageRelevanceScore = 0.0;
        public double sourceDiversity = 0.0;
        public double contentRichness = 0.0;
        public double overallQuality = 0.0;
        public int citationCount = 0;
        public String nodeContext = "";

        @Override
        public String toString() {
            return String.format("Quality[overall=%.2f, relevance=%.2f, diversity=%.2f, richness=%.2f, count=%d]", overallQuality, averageRelevanceScore,
                sourceDiversity, contentRichness, citationCount);
        }
    }
}