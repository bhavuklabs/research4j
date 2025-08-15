package com.github.bhavuklabs.deepresearch.engine;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import com.github.bhavuklabs.citation.service.CitationService;
import com.github.bhavuklabs.core.contracts.LLMClient;
import com.github.bhavuklabs.deepresearch.models.DeepResearchConfig;
import com.github.bhavuklabs.deepresearch.models.DeepResearchResult;
import com.github.bhavuklabs.deepresearch.models.ResearchResults;
import com.github.bhavuklabs.deepresearch.models.PersonalizedMarkdownConfig;
import com.github.bhavuklabs.builders.ConnectedContentBuilder;
import com.github.bhavuklabs.services.ContentVectorizer;
import com.github.bhavuklabs.services.ContentVectorizer.RelatedContent;
import com.github.bhavuklabs.services.impl.ProductionEmbeddingService;
import com.github.bhavuklabs.vector.VectorStore;


public class VectorEnhancedDeepResearchEngine extends DeepResearchEngine {
    
    private static final Logger logger = Logger.getLogger(VectorEnhancedDeepResearchEngine.class.getName());

    private final VectorStore vectorStore;
    private final ContentVectorizer contentVectorizer;
    private final Map<String, ConnectedContentBuilder> sessionBuilders;
    private final Map<String, VectorResearchSession> vectorSessions;
    private final LLMClient llmClient;
    
    public VectorEnhancedDeepResearchEngine(LLMClient llmClient, CitationService citationService) {
        super(llmClient, citationService);

        this.llmClient = llmClient;

        this.vectorStore = new VectorStore(384); // 384-dimension vectors
        this.contentVectorizer = new ContentVectorizer(vectorStore, new ProductionEmbeddingService());
        this.sessionBuilders = new ConcurrentHashMap<>();
        this.vectorSessions = new ConcurrentHashMap<>();
        
        logger.info("Initialized VectorEnhancedDeepResearchEngine with 384-dimension vector store");
    }
    
    
    public CompletableFuture<VectorEnhancedResearchResult> executeConnectedDeepResearch(
            String originalQuery, 
            DeepResearchConfig config,
            PersonalizedMarkdownConfig markdownConfig) {
        
        String sessionId = generateSessionId();
        VectorResearchSession session = new VectorResearchSession(sessionId, originalQuery);
        vectorSessions.put(sessionId, session);
        
        return CompletableFuture.supplyAsync(() -> {
            try {

                DeepResearchResult baseResult = super.executeDeepResearch(originalQuery, config)
                    .get(); // Block for simplicity - in production, chain properly

                VectorEnhancedResearchResult enhancedResult = generateConnectedContent(
                    baseResult, session, markdownConfig);
                
                logger.info("Completed vector-enhanced research for session: " + sessionId);
                return enhancedResult;
                
            } catch (Exception e) {
                logger.severe("Error in vector-enhanced research: " + e.getMessage());
                return createFallbackResult(sessionId, originalQuery, config, markdownConfig);
            }
        });
    }
    
    
    private VectorEnhancedResearchResult generateConnectedContent(
            DeepResearchResult baseResult,
            VectorResearchSession session,
            PersonalizedMarkdownConfig markdownConfig) {
        
        try {

            ConnectedContentBuilder builder = new ConnectedContentBuilder(
                getLLMClient(), contentVectorizer, session.getSessionId());
            sessionBuilders.put(session.getSessionId(), builder);

            storeResearchInVectorStore(baseResult, session);

            Map<String, List<RelatedContent>> topicRelationships = 
                analyzeTopicRelationships(baseResult, session);

            String connectedNarrative = builder.buildConnectedContent(
                "research_narrative", baseResult.getNarrative(), markdownConfig);

            Map<String, String> connectedContent = new HashMap<>();
            for (String topic : extractTopicsFromResult(baseResult)) {
                String connectedSection = builder.buildConnectedContent(
                    topic, "research_section", markdownConfig);
                connectedContent.put(topic, connectedSection);
            }

            ContentAnalysis analysis = performContentAnalysis(baseResult, topicRelationships);
            
            return new VectorEnhancedResearchResult(
                baseResult,
                connectedContent,
                topicRelationships,
                connectedNarrative,
                markdownConfig,
                analysis
            );
            
        } catch (Exception e) {
            logger.warning("Error generating connected content: " + e.getMessage());

            return new VectorEnhancedResearchResult(
                baseResult,
                new HashMap<>(),
                new HashMap<>(),
                baseResult.getNarrative(),
                markdownConfig,
                new ContentAnalysis(new HashMap<>(), 0.0, new ArrayList<>())
            );
        }
    }
    
    private void storeResearchInVectorStore(DeepResearchResult result, VectorResearchSession session) {
        try {

            contentVectorizer.storeContent(
                session.getSessionId(),
                session.getSessionId() + "_narrative",
                result.getNarrative(),
                Map.of("type", "narrative", 
                       "sessionId", session.getSessionId(),
                       "topic", session.getTopic(),
                       "subtopic", "narrative",
                       "section_type", "main_content")
            );

            contentVectorizer.storeContent(
                session.getSessionId(),
                session.getSessionId() + "_summary",
                result.getExecutiveSummary(),
                Map.of("type", "summary", 
                       "sessionId", session.getSessionId(),
                       "topic", session.getTopic(),
                       "subtopic", "summary",
                       "section_type", "executive_summary")
            );

            result.getAllCitations().forEach(citation -> {
                String citationId = session.getSessionId() + "_citation_" + citation.getUrl().hashCode();
                contentVectorizer.storeContent(
                    session.getSessionId(),
                    citationId,
                    citation.getContent(),
                    Map.of("type", "citation", 
                           "sessionId", session.getSessionId(),
                           "topic", session.getTopic(),
                           "subtopic", "citation",
                           "section_type", "reference_material",
                           "url", citation.getUrl())
                );
            });
            
        } catch (Exception e) {
            logger.warning("Error storing research in vector store: " + e.getMessage());
        }
    }
    
    private Map<String, List<RelatedContent>> analyzeTopicRelationships(
            DeepResearchResult result, VectorResearchSession session) {
        
        Map<String, List<RelatedContent>> relationships = new HashMap<>();
        
        try {
            List<String> topics = extractTopicsFromResult(result);
            
            for (String topic : topics) {
                session.addGeneratedTopic(topic);

                List<RelatedContent> relatedContent = contentVectorizer.findRelatedContent(
                    topic, 5, session.getSessionId()
                );
                
                relationships.put(topic, relatedContent);
            }
            
        } catch (Exception e) {
            logger.warning("Error analyzing topic relationships: " + e.getMessage());
        }
        
        return relationships;
    }
    
    private List<String> extractTopicsFromResult(DeepResearchResult result) {
        Set<String> topics = new HashSet<>();

        topics.addAll(extractTopicsFromText(result.getNarrative()));

        topics.addAll(extractTopicsFromText(result.getExecutiveSummary()));

        result.getAllCitations().forEach(citation -> {
            topics.addAll(extractTopicsFromText(citation.getContent()));
        });
        
        return new ArrayList<>(topics);
    }
    
    private List<String> extractTopicsFromText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }

        Set<String> topics = new HashSet<>();
        String[] sentences = text.split("[.!?]+");
        
        for (String sentence : sentences) {
            String[] words = sentence.toLowerCase().trim().split("\\s+");

            for (int i = 0; i < words.length - 1; i++) {
                String phrase = words[i] + " " + words[i + 1];
                if (isValidTopic(phrase)) {
                    topics.add(phrase);
                }
            }

            for (String word : words) {
                if (word.length() > 5 && isValidTopic(word) && !isStopWord(word)) {
                    topics.add(word);
                }
            }
        }
        
        return new ArrayList<>(topics).subList(0, Math.min(topics.size(), 10));
    }
    
    private boolean isValidTopic(String phrase) {
        if (phrase == null || phrase.trim().length() < 3) return false;

        String clean = phrase.toLowerCase().trim();
        return !clean.matches(".*\\b(the|and|or|but|in|on|at|to|for|of|with|by)\\b.*") &&
               !clean.matches(".*\\d.*") && // No numbers
               clean.matches("[a-zA-Z\\s]+"); // Only letters and spaces
    }
    
    private ContentAnalysis performContentAnalysis(
            DeepResearchResult result, 
            Map<String, List<RelatedContent>> relationships) {
        
        Map<String, Double> topicScores = new HashMap<>();
        double avgConnectivity = 0.0;
        List<String> keyInsights = new ArrayList<>();
        
        try {

            for (Map.Entry<String, List<RelatedContent>> entry : relationships.entrySet()) {
                String topic = entry.getKey();
                List<RelatedContent> related = entry.getValue();
                
                double score = related.stream()
                    .mapToDouble(RelatedContent::getSimilarity)
                    .average()
                    .orElse(0.0);
                
                topicScores.put(topic, score);
            }

            avgConnectivity = topicScores.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

            keyInsights.add("Research covers " + relationships.size() + " main topics");
            keyInsights.add("Average topic connectivity: " + String.format("%.2f", avgConnectivity));
            
            if (avgConnectivity > 0.7) {
                keyInsights.add("High semantic connectivity between topics");
            } else if (avgConnectivity > 0.4) {
                keyInsights.add("Moderate semantic connectivity between topics");
            } else {
                keyInsights.add("Topics show limited semantic connectivity");
            }
            
        } catch (Exception e) {
            logger.warning("Error in content analysis: " + e.getMessage());
        }
        
        return new ContentAnalysis(topicScores, avgConnectivity, keyInsights);
    }
    
    private VectorEnhancedResearchResult createFallbackResult(
            String sessionId, String originalQuery, 
            DeepResearchConfig config, PersonalizedMarkdownConfig markdownConfig) {

        String narrative = "Research analysis for: " + originalQuery;
        String executiveSummary = "Unable to complete full vector-enhanced research due to system error.";
        String methodology = "Standard deep research methodology";

        ResearchResults emptyResults = new ResearchResults(new ArrayList<>(), new HashMap<>());
        
        DeepResearchResult baseResult = new DeepResearchResult(sessionId, originalQuery, narrative, executiveSummary, 
                                methodology, emptyResults, new HashMap<>(), config);
        
        return new VectorEnhancedResearchResult(
            baseResult,
            new HashMap<>(),
            new HashMap<>(),
            narrative,
            markdownConfig,
            new ContentAnalysis(new HashMap<>(), 0.0, new ArrayList<>())
        );
    }
    
    private boolean isStopWord(String word) {
        Set<String> stopWords = Set.of(
            "the", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "by",
            "this", "that", "these", "those", "is", "are", "was", "were", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "could", "should"
        );
        return stopWords.contains(word.toLowerCase());
    }
    
    private String generateSessionId() {
        return "vector_session_" + System.currentTimeMillis() + "_" + 
               UUID.randomUUID().toString().substring(0, 8);
    }

    private LLMClient getLLMClient() {
        return this.llmClient;
    }
    
    
    private static class VectorResearchSession {
        private final String sessionId;
        private final String topic;
        private final Set<String> generatedTopics;
        
        public VectorResearchSession(String sessionId, String originalQuery) {
            this.sessionId = sessionId;
            this.topic = originalQuery; // Use original query as the main topic
            this.generatedTopics = new HashSet<>();
        }
        
        public String getSessionId() { return sessionId; }
        public String getTopic() { return topic; }
        
        public void addGeneratedTopic(String topic) {
            generatedTopics.add(topic);
        }
    }
    
    
    public static class VectorEnhancedResearchResult extends DeepResearchResult {
        private final Map<String, String> connectedContent;
        private final Map<String, List<RelatedContent>> topicRelationships;
        private final String connectedNarrative;
        private final PersonalizedMarkdownConfig personalizedConfig;
        private ContentAnalysis contentAnalysis;
        
        public VectorEnhancedResearchResult(DeepResearchResult base,
                                          Map<String, String> connectedContent,
                                          Map<String, List<RelatedContent>> topicRelationships,
                                          String connectedNarrative,
                                          PersonalizedMarkdownConfig personalizedConfig,
                                          ContentAnalysis contentAnalysis) {
            super(base.getSessionId(), base.getOriginalQuery(), base.getNarrative(),
                  base.getExecutiveSummary(), base.getMethodology(), base.getResults(),
                  new HashMap<>(), base.getConfig());
            
            this.connectedContent = new HashMap<>(connectedContent);
            this.topicRelationships = new HashMap<>(topicRelationships);
            this.connectedNarrative = connectedNarrative;
            this.personalizedConfig = personalizedConfig;
            this.contentAnalysis = contentAnalysis;
        }

        public Map<String, String> getConnectedContent() { return new HashMap<>(connectedContent); }
        public Map<String, List<RelatedContent>> getTopicRelationships() { return new HashMap<>(topicRelationships); }
        public String getConnectedNarrative() { return connectedNarrative; }
        public PersonalizedMarkdownConfig getPersonalizedConfig() { return personalizedConfig; }
        public ContentAnalysis getContentAnalysis() { return contentAnalysis; }
        
        public void setContentAnalysis(ContentAnalysis analysis) {
            this.contentAnalysis = analysis;
        }
        
        @Override
        public String toString() {
            return "VectorEnhancedResearchResult{" +
                   "sessionId='" + getSessionId() + '\'' +
                   ", connectedTopics=" + topicRelationships.size() +
                   ", avgConnectivity=" + (contentAnalysis != null ? contentAnalysis.getAverageConnectivity() : "N/A") +
                   '}';
        }
    }
    
    
    public static class ContentAnalysis {
        private final Map<String, Double> topicScores;
        private final double averageConnectivity;
        private final List<String> keyInsights;
        
        public ContentAnalysis(Map<String, Double> topicScores, 
                             double averageConnectivity,
                             List<String> keyInsights) {
            this.topicScores = new HashMap<>(topicScores);
            this.averageConnectivity = averageConnectivity;
            this.keyInsights = new ArrayList<>(keyInsights);
        }
        
        public Map<String, Double> getTopicScores() { return new HashMap<>(topicScores); }
        public double getAverageConnectivity() { return averageConnectivity; }
        public List<String> getKeyInsights() { return new ArrayList<>(keyInsights); }
        
        @Override
        public String toString() {
            return "ContentAnalysis{" +
                   "topics=" + topicScores.size() +
                   ", avgConnectivity=" + String.format("%.3f", averageConnectivity) +
                   ", insights=" + keyInsights.size() +
                   '}';
        }
    }
}
