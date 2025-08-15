package com.github.bhavuklabs.deepresearch.services;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.github.bhavuklabs.citation.CitationResult;
import com.github.bhavuklabs.citation.service.CitationService;
import com.github.bhavuklabs.core.contracts.LLMClient;
import com.github.bhavuklabs.deepresearch.engine.DeepResearchEngine;
import com.github.bhavuklabs.deepresearch.models.DeepResearchConfig;
import com.github.bhavuklabs.deepresearch.models.DeepResearchResult;
import com.github.bhavuklabs.deepresearch.models.PersonalizedMarkdownConfig;
import com.github.bhavuklabs.builders.ConnectedContentBuilder;
import com.github.bhavuklabs.services.ContentVectorizer;
import com.github.bhavuklabs.services.ContentVectorizer.RelatedContent;
import com.github.bhavuklabs.services.ContentVectorizer.ContentAnalysis;
import com.github.bhavuklabs.services.impl.SimpleEmbeddingService;
import com.github.bhavuklabs.vector.VectorStore;


public class VectorEnhancedResearchService {
    
    private static final Logger logger = Logger.getLogger(VectorEnhancedResearchService.class.getName());
    
    private final DeepResearchEngine deepResearchEngine;
    private final VectorStore vectorStore;
    private final ContentVectorizer contentVectorizer;
    private final LLMClient llmClient;

    private final Map<String, ConnectedContentBuilder> sessionBuilders;
    private final Map<String, VectorResearchSession> activeSessions;
    
    public VectorEnhancedResearchService(LLMClient llmClient, CitationService citationService) {
        this.llmClient = llmClient;
        this.deepResearchEngine = new DeepResearchEngine(llmClient, citationService);

        this.vectorStore = new VectorStore(384);
        this.contentVectorizer = new ContentVectorizer(vectorStore, new SimpleEmbeddingService());

        this.sessionBuilders = new ConcurrentHashMap<>();
        this.activeSessions = new ConcurrentHashMap<>();
        
        logger.info("VectorEnhancedResearchService initialized with semantic content connectivity");
    }
    
    
    public CompletableFuture<VectorEnhancedResearchResult> executeConnectedResearch(
            String originalQuery, 
            DeepResearchConfig researchConfig,
            PersonalizedMarkdownConfig personalizedConfig) {
        
        return CompletableFuture.supplyAsync(() -> {
            String sessionId = generateSessionId();
            Instant startTime = Instant.now();
            
            try {
                logger.info("Starting Vector-Enhanced Research for: " + originalQuery);

                VectorResearchSession session = new VectorResearchSession(sessionId, originalQuery, startTime);
                activeSessions.put(sessionId, session);

                ConnectedContentBuilder contentBuilder = new ConnectedContentBuilder(
                    llmClient, contentVectorizer, sessionId);
                sessionBuilders.put(sessionId, contentBuilder);

                DeepResearchResult standardResult = deepResearchEngine
                    .executeDeepResearch(originalQuery, researchConfig).get();

                List<String> keyTopics = extractKeyTopics(standardResult);
                logger.info("Extracted " + keyTopics.size() + " key topics for vector enhancement");

                Map<String, String> connectedContent = generateConnectedContent(
                    keyTopics, personalizedConfig, contentBuilder, session);

                Map<String, List<RelatedContent>> topicRelationships = analyzeTopicRelationships(
                    keyTopics, sessionId);

                String connectedNarrative = buildConnectedNarrative(
                    standardResult, connectedContent, topicRelationships);

                ContentAnalysis analysis = contentVectorizer.analyzeContent(sessionId);

                VectorEnhancedResearchResult enhancedResult = new VectorEnhancedResearchResult(
                    standardResult,
                    connectedContent,
                    topicRelationships,
                    connectedNarrative,
                    personalizedConfig,
                    analysis
                );
                
                Duration totalDuration = Duration.between(startTime, Instant.now());
                session.setDuration(totalDuration);
                
                logger.info("Vector-Enhanced Research completed in " + totalDuration.toMinutes() + 
                           " minutes with " + analysis.getTotalConnections() + " content connections");
                
                return enhancedResult;
                
            } catch (Exception e) {
                logger.severe("Vector-Enhanced Research failed: " + e.getMessage());
                return createFallbackResult(originalQuery, researchConfig, personalizedConfig);
            } finally {

                sessionBuilders.remove(sessionId);
                activeSessions.remove(sessionId);
            }
        });
    }
    
    
    public String generatePersonalizedContent(String sessionId, String topic, String subtopic,
                                            PersonalizedMarkdownConfig config) {
        ConnectedContentBuilder builder = sessionBuilders.get(sessionId);
        if (builder == null) {
            logger.warning("No active session found: " + sessionId + ", creating temporary builder");
            builder = new ConnectedContentBuilder(llmClient, contentVectorizer, sessionId);
        }
        
        return builder.buildConnectedContent(topic, subtopic, config);
    }
    
    
    public List<RelatedContent> findGlobalRelatedContent(String query, int maxResults) {
        return contentVectorizer.findRelatedContent(query, maxResults, "");
    }
    
    
    public List<RelatedContent> findSessionRelatedContent(String sessionId, String query, int maxResults) {
        return contentVectorizer.findSessionContent(sessionId, query, maxResults);
    }
    
    
    public ContentGapAnalysis analyzeContentGaps(String sessionId, List<String> targetConcepts) {
        List<String> gaps = contentVectorizer.suggestContentGaps(sessionId, targetConcepts);
        ContentAnalysis analysis = contentVectorizer.analyzeContent(sessionId);
        
        return new ContentGapAnalysis(gaps, targetConcepts, analysis);
    }
    
    
    public LearningPath generateLearningPath(String sessionId, String targetTopic,
                                           PersonalizedMarkdownConfig config) {
        VectorResearchSession session = activeSessions.get(sessionId);
        if (session == null) {
            logger.warning("Session not found for learning path generation: " + sessionId);
            return createDefaultLearningPath(targetTopic, config);
        }
        
        List<RelatedContent> relatedContent = contentVectorizer.findSessionContent(sessionId, targetTopic, 10);
        Map<String, Set<String>> connections = contentVectorizer.getContentConnections(sessionId);
        
        return new LearningPath(targetTopic, relatedContent, connections, config.getUserExpertiseLevel());
    }
    
    
    public ContentAnalysis getContentAnalysis(String sessionId) {
        return contentVectorizer.analyzeContent(sessionId);
    }
    
    
    public Map<String, Set<String>> getContentRelationshipMap(String sessionId) {
        return contentVectorizer.getContentConnections(sessionId);
    }

    
    private List<String> extractKeyTopics(DeepResearchResult result) {
        Set<String> topics = new HashSet<>();

        for (CitationResult citation : result.getResults().getAllCitations()) {
            String[] titleWords = citation.getTitle().toLowerCase().split("\\W+");
            for (String word : titleWords) {
                if (word.length() > 4 && !isStopWord(word)) {
                    topics.add(capitalizeFirst(word));
                }
            }
        }

        for (String insightKey : result.getResults().getInsights().keySet()) {
            String[] words = insightKey.toLowerCase().split("\\W+");
            for (String word : words) {
                if (word.length() > 4 && !isStopWord(word)) {
                    topics.add(capitalizeFirst(word));
                }
            }
        }

        return topics.stream()
                .filter(topic -> topic.length() > 1)
                .distinct()
                .limit(8)
                .collect(Collectors.toList());
    }
    
    private Map<String, String> generateConnectedContent(List<String> keyTopics,
                                                        PersonalizedMarkdownConfig config,
                                                        ConnectedContentBuilder builder,
                                                        VectorResearchSession session) {
        Map<String, String> connectedContent = new HashMap<>();
        
        for (String topic : keyTopics) {
            try {
                String content = builder.buildConnectedContent(topic, "overview", config);
                connectedContent.put(topic, content);
                session.addGeneratedTopic(topic);
                
                logger.fine("Generated connected content for topic: " + topic);
            } catch (Exception e) {
                logger.warning("Failed to generate content for topic: " + topic + " - " + e.getMessage());
            }
        }
        
        return connectedContent;
    }
    
    private Map<String, List<RelatedContent>> analyzeTopicRelationships(List<String> keyTopics, String sessionId) {
        Map<String, List<RelatedContent>> relationships = new HashMap<>();
        
        for (String topic : keyTopics) {
            List<RelatedContent> related = contentVectorizer.findRelatedContent(topic, 5, sessionId);
            if (!related.isEmpty()) {
                relationships.put(topic, related);
            }
        }
        
        return relationships;
    }
    
    private String buildConnectedNarrative(DeepResearchResult standardResult,
                                         Map<String, String> connectedContent,
                                         Map<String, List<RelatedContent>> relationships) {
        StringBuilder enhanced = new StringBuilder();
        
        enhanced.append("# Connected Research Analysis\n\n");
        enhanced.append("## Executive Summary\n");
        enhanced.append("This comprehensive research analysis incorporates semantic content relationships ")
                .append("and cross-topic connections to provide interconnected insights and learning pathways.\n\n");
        
        enhanced.append("### Research Statistics\n");
        enhanced.append("- **Original Query**: ").append(standardResult.getOriginalQuery()).append("\n");
        enhanced.append("- **Sources Analyzed**: ").append(standardResult.getResults().getAllCitations().size()).append("\n");
        enhanced.append("- **Connected Topics**: ").append(connectedContent.size()).append("\n");
        enhanced.append("- **Relationship Networks**: ").append(relationships.size()).append("\n\n");
        
        enhanced.append("---\n\n");
        enhanced.append(standardResult.getNarrative());
        
        if (!connectedContent.isEmpty()) {
            enhanced.append("\n\n## Connected Topic Analysis\n\n");
            enhanced.append("The following sections provide personalized, interconnected analysis of key topics ")
                    .append("with cross-references and related concepts.\n\n");
            
            for (Map.Entry<String, String> entry : connectedContent.entrySet()) {
                enhanced.append("### ").append(entry.getKey()).append("\n");
                enhanced.append(entry.getValue()).append("\n\n");
            }
        }
        
        if (!relationships.isEmpty()) {
            enhanced.append("\n## Semantic Relationship Network\n\n");
            enhanced.append("The following network shows how topics connect through semantic similarity:\n\n");
            
            for (Map.Entry<String, List<RelatedContent>> entry : relationships.entrySet()) {
                String topic = entry.getKey();
                List<RelatedContent> related = entry.getValue();
                
                if (!related.isEmpty()) {
                    enhanced.append("**").append(topic).append("** → ");
                    enhanced.append(related.stream()
                            .map(rel -> rel.getTopic() + " (" + String.format("%.2f", rel.getSimilarity()) + ")")
                            .collect(Collectors.joining(", ")))
                            .append("\n\n");
                }
            }
        }
        
        enhanced.append("## Learning Path Recommendations\n\n");
        enhanced.append("Based on content connectivity analysis, we recommend exploring topics in the following order ");
        enhanced.append("to maximize understanding and build upon interconnected concepts.\n");
        
        return enhanced.toString();
    }
    
    private VectorEnhancedResearchResult createFallbackResult(String originalQuery,
                                                             DeepResearchConfig researchConfig,
                                                             PersonalizedMarkdownConfig personalizedConfig) {

        String fallbackNarrative = "# Research Analysis - Vector Enhancement Unavailable\n\n" +
                "**Topic**: " + originalQuery + "\n\n" +
                "Research completed with standard methodology. Vector-based content connectivity " +
                "features were not available for this session.\n\n" +
                "## Recommendations\n" +
                "- Retry research with adjusted parameters\n" +
                "- Check system configuration and resources\n" +
                "- Consider manual content organization\n";
        
        return new VectorEnhancedResearchResult(
                null, new HashMap<>(), new HashMap<>(), fallbackNarrative,
                personalizedConfig, new ContentAnalysis(0, 0, 0.0, ""));
    }
    
    private LearningPath createDefaultLearningPath(String targetTopic, PersonalizedMarkdownConfig config) {
        return new LearningPath(targetTopic, new ArrayList<>(), new HashMap<>(), 
                              config.getUserExpertiseLevel());
    }
    
    private String capitalizeFirst(String word) {
        if (word == null || word.isEmpty()) return word;
        return word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase();
    }
    
    private boolean isStopWord(String word) {
        Set<String> stopWords = Set.of("the", "and", "for", "are", "but", "not", "you", "all", 
                                     "can", "had", "her", "was", "one", "our", "out", "day", 
                                     "get", "has", "him", "his", "how", "its", "may", "new", 
                                     "now", "old", "see", "two", "way", "who", "with", "that", 
                                     "this", "from", "they", "know", "want", "been", "good", 
                                     "much", "some", "time", "very", "when", "come", "here", 
                                     "just", "like", "long", "make", "many", "over", "such", 
                                     "take", "than", "them", "well", "will", "what", "where",
                                     "which", "into", "does", "could", "would", "should");
        return stopWords.contains(word.toLowerCase());
    }
    
    private String generateSessionId() {
        return "vector_session_" + System.currentTimeMillis() + "_" + 
               UUID.randomUUID().toString().substring(0, 8);
    }
    
    
    public static class VectorEnhancedResearchResult {
        private final DeepResearchResult baseResult;
        private final Map<String, String> connectedContent;
        private final Map<String, List<RelatedContent>> topicRelationships;
        private final String connectedNarrative;
        private final PersonalizedMarkdownConfig personalizedConfig;
        private final ContentAnalysis contentAnalysis;
        
        public VectorEnhancedResearchResult(DeepResearchResult baseResult,
                                          Map<String, String> connectedContent,
                                          Map<String, List<RelatedContent>> topicRelationships,
                                          String connectedNarrative,
                                          PersonalizedMarkdownConfig personalizedConfig,
                                          ContentAnalysis contentAnalysis) {
            this.baseResult = baseResult;
            this.connectedContent = connectedContent != null ? connectedContent : new HashMap<>();
            this.topicRelationships = topicRelationships != null ? topicRelationships : new HashMap<>();
            this.connectedNarrative = connectedNarrative;
            this.personalizedConfig = personalizedConfig;
            this.contentAnalysis = contentAnalysis;
        }

        public DeepResearchResult getBaseResult() { return baseResult; }
        public Map<String, String> getConnectedContent() { return connectedContent; }
        public Map<String, List<RelatedContent>> getTopicRelationships() { return topicRelationships; }
        public String getConnectedNarrative() { return connectedNarrative; }
        public PersonalizedMarkdownConfig getPersonalizedConfig() { return personalizedConfig; }
        public ContentAnalysis getContentAnalysis() { return contentAnalysis; }

        public String getOriginalQuery() { 
            return baseResult != null ? baseResult.getOriginalQuery() : "Unknown Query"; 
        }
        public String getSessionId() { 
            return baseResult != null ? baseResult.getSessionId() : "unknown_session"; 
        }
        
        public String getNarrative() {
            return connectedNarrative != null ? connectedNarrative : 
                   (baseResult != null ? baseResult.getNarrative() : "No narrative available");
        }
    }
    
    
    public static class ContentGapAnalysis {
        private final List<String> identifiedGaps;
        private final List<String> targetConcepts;
        private final ContentAnalysis currentAnalysis;
        private final List<String> recommendations;
        
        public ContentGapAnalysis(List<String> gaps, List<String> targets, ContentAnalysis analysis) {
            this.identifiedGaps = gaps != null ? gaps : new ArrayList<>();
            this.targetConcepts = targets != null ? targets : new ArrayList<>();
            this.currentAnalysis = analysis;
            this.recommendations = generateRecommendations();
        }
        
        private List<String> generateRecommendations() {
            List<String> recs = new ArrayList<>();
            
            if (!identifiedGaps.isEmpty()) {
                recs.add("Focus on developing content for: " + String.join(", ", identifiedGaps));
            }
            
            if (currentAnalysis.getAverageConnections() < 2.0) {
                recs.add("Increase cross-referencing between existing topics");
            }
            
            if (currentAnalysis.getTotalContent() < targetConcepts.size()) {
                recs.add("Expand content coverage to include all target concepts");
            }
            
            return recs;
        }

        public List<String> getIdentifiedGaps() { return identifiedGaps; }
        public List<String> getTargetConcepts() { return targetConcepts; }
        public ContentAnalysis getCurrentAnalysis() { return currentAnalysis; }
        public List<String> getRecommendations() { return recommendations; }
    }
    
    
    public static class LearningPath {
        private final String targetTopic;
        private final List<RelatedContent> relatedContent;
        private final Map<String, Set<String>> connections;
        private final String expertiseLevel;
        private final List<String> recommendedOrder;
        
        public LearningPath(String targetTopic, List<RelatedContent> relatedContent,
                          Map<String, Set<String>> connections, String expertiseLevel) {
            this.targetTopic = targetTopic;
            this.relatedContent = relatedContent != null ? relatedContent : new ArrayList<>();
            this.connections = connections != null ? connections : new HashMap<>();
            this.expertiseLevel = expertiseLevel != null ? expertiseLevel : "intermediate";
            this.recommendedOrder = generateRecommendedOrder();
        }
        
        private List<String> generateRecommendedOrder() {
            if (relatedContent.isEmpty()) {
                return Arrays.asList(targetTopic);
            }
            
            List<String> order = new ArrayList<>();

            relatedContent.stream()
                    .sorted((a, b) -> {
                        int aConnections = connections.getOrDefault(a.getContentId(), Set.of()).size();
                        int bConnections = connections.getOrDefault(b.getContentId(), Set.of()).size();


                        if ("beginner".equals(expertiseLevel)) {
                            return Integer.compare(aConnections, bConnections);
                        } else {
                            return Integer.compare(bConnections, aConnections);
                        }
                    })
                    .forEach(content -> order.add(content.getTopic()));
            
            return order;
        }

        public String getTargetTopic() { return targetTopic; }
        public List<RelatedContent> getRelatedContent() { return relatedContent; }
        public Map<String, Set<String>> getConnections() { return connections; }
        public String getExpertiseLevel() { return expertiseLevel; }
        public List<String> getRecommendedOrder() { return recommendedOrder; }
    }
    
    
    private static class VectorResearchSession {
        private final Set<String> generatedTopics;
        
        public VectorResearchSession(String sessionId, String originalQuery, Instant startTime) {
            this.generatedTopics = new HashSet<>();
        }
        
        public void addGeneratedTopic(String topic) {
            generatedTopics.add(topic);
        }
        
        public void setDuration(Duration duration) {

        }
    }
}
