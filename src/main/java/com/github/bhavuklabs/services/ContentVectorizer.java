package com.github.bhavuklabs.services;

import com.github.bhavuklabs.vector.VectorStore;
import com.github.bhavuklabs.vector.VectorStore.SimilarityResult;
import com.github.bhavuklabs.vector.VectorStore.VectorDocument;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;


public class ContentVectorizer {
    
    private static final Logger logger = Logger.getLogger(ContentVectorizer.class.getName());
    
    private final VectorStore vectorStore;
    private final EmbeddingService embeddingService;
    private final Map<String, Set<String>> contentConnections;
    
    public ContentVectorizer(VectorStore vectorStore, EmbeddingService embeddingService) {
        this.vectorStore = vectorStore;
        this.embeddingService = embeddingService;
        this.contentConnections = new HashMap<>();
    }
    
    
    public void storeContent(String sessionId, String contentId, String content, Map<String, Object> metadata) {
        try {

            float[] embeddings = embeddingService.generateEmbeddings(content);

            Map<String, Object> enhancedMetadata = new HashMap<>(metadata);
            enhancedMetadata.put("session_id", sessionId);
            enhancedMetadata.put("content_id", contentId);
            enhancedMetadata.put("timestamp", System.currentTimeMillis());
            enhancedMetadata.put("content_length", content.length());

            String vectorId = generateVectorId(sessionId, contentId);
            vectorStore.store(vectorId, embeddings, content, enhancedMetadata);
            
            logger.info("Stored content in vector database: " + vectorId);

            updateContentConnections(vectorId, content);
            
        } catch (Exception e) {
            logger.severe("Failed to store content in vector database: " + e.getMessage());
            throw new RuntimeException("Content vectorization failed", e);
        }
    }
    
    
    public List<RelatedContent> findRelatedContent(String content, int topK, String currentSessionId) {
        try {
            float[] queryVector = embeddingService.generateEmbeddings(content);
            List<SimilarityResult> results = vectorStore.search(queryVector, topK + 10); // Get extra to filter
            
            return results.stream()
                    .filter(result -> !isSameSession(result.getDocument(), currentSessionId)) // Exclude current session
                    .filter(result -> result.getSimilarity() > 0.1f) // Much lower threshold for better connections
                    .limit(topK)
                    .map(this::convertToRelatedContent)
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            logger.warning("Failed to find related content: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    
    public List<RelatedContent> findAllRelatedContent(String content, int topK, String excludeContentId) {
        try {
            float[] queryVector = embeddingService.generateEmbeddings(content);
            List<SimilarityResult> results = vectorStore.search(queryVector, topK + 10);
            
            return results.stream()
                    .filter(result -> !isCurrentContent(result.getDocument(), excludeContentId))
                    .filter(result -> result.getSimilarity() > 0.05f) // Very low threshold for maximum connections
                    .limit(topK)
                    .map(this::convertToRelatedContent)
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            logger.warning("Failed to find all related content: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    
    public List<RelatedContent> findSessionContent(String sessionId, String currentContentId, int topK) {
        List<RelatedContent> sessionContent = new ArrayList<>();
        
        try {

            List<SimilarityResult> allResults = vectorStore.search(new float[384], Integer.MAX_VALUE);
            
            sessionContent = allResults.stream()
                    .filter(result -> isFromSession(result.getDocument(), sessionId))
                    .filter(result -> !isCurrentContent(result.getDocument(), currentContentId))
                    .limit(topK)
                    .map(this::convertToRelatedContent)
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            logger.warning("Failed to find session content: " + e.getMessage());
        }
        
        return sessionContent;
    }
    
    
    public void updateContentConnections(String contentId, String content) {

        List<RelatedContent> relatedContent = findAllRelatedContent(content, 5, contentId);
        
        Set<String> connections = contentConnections.computeIfAbsent(contentId, k -> new HashSet<>());
        
        for (RelatedContent related : relatedContent) {
            String relatedId = related.getContentId();

            connections.add(relatedId);
            contentConnections.computeIfAbsent(relatedId, k -> new HashSet<>()).add(contentId);
        }
    }
    
    
    public Map<String, Set<String>> getContentConnections(String sessionId) {
        return contentConnections.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(sessionId))
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue
                ));
    }
    
    
    public List<String> suggestContentGaps(String sessionId, List<String> targetConcepts) {
        List<String> gaps = new ArrayList<>();
        
        for (String concept : targetConcepts) {
            List<RelatedContent> existing = findRelatedContent(concept, 3, sessionId);
            if (existing.isEmpty()) {
                gaps.add(concept);
            }
        }
        
        return gaps;
    }
    
    
    public ContentAnalysis analyzeContent(String sessionId) {
        Map<String, Set<String>> connections = getContentConnections(sessionId);
        
        int totalContent = connections.size();
        int totalConnections = connections.values().stream()
                .mapToInt(Set::size)
                .sum();
                
        double avgConnections = totalContent > 0 ? (double) totalConnections / totalContent : 0;

        String mostConnected = connections.entrySet().stream()
                .max(Comparator.comparingInt(entry -> entry.getValue().size()))
                .map(Map.Entry::getKey)
                .orElse("");
                
        return new ContentAnalysis(totalContent, totalConnections, avgConnections, mostConnected);
    }

    
    private String generateVectorId(String sessionId, String contentId) {
        return sessionId + ":" + contentId;
    }
    
    private String extractSessionId(String vectorId) {
        return vectorId.substring(0, vectorId.indexOf(':'));
    }
    
    private boolean isSameSession(VectorDocument doc, String sessionId) {
        Object docSessionId = doc.getMetadata().get("session_id");
        return sessionId.equals(docSessionId);
    }
    
    private boolean isFromSession(VectorDocument doc, String sessionId) {
        Object docSessionId = doc.getMetadata().get("session_id");
        return sessionId.equals(docSessionId);
    }
    
    private boolean isCurrentContent(VectorDocument doc, String contentId) {
        Object docContentId = doc.getMetadata().get("content_id");
        return contentId.equals(docContentId);
    }
    
    private RelatedContent convertToRelatedContent(SimilarityResult result) {
        VectorDocument doc = result.getDocument();
        Map<String, Object> metadata = doc.getMetadata();

        String topic = (String) metadata.getOrDefault("topic", "unknown_topic");
        String subtopic = (String) metadata.getOrDefault("subtopic", "unknown_subtopic");
        String sectionType = (String) metadata.getOrDefault("section_type", "general");

        if ("unknown_topic".equals(topic)) {
            logger.warning("Missing topic metadata for document: " + doc.getId() + 
                         ". Available metadata keys: " + metadata.keySet());
        }

        String contentId = (String) metadata.getOrDefault("content_id", doc.getId());
        
        return new RelatedContent(
            doc.getId(),
            contentId,
            doc.getContent(),
            result.getSimilarity(),
            topic,
            subtopic,
            sectionType,
            metadata
        );
    }

    
    public static class RelatedContent {
        private final String vectorId;
        private final String contentId;
        private final String content;
        private final float similarity;
        private final String topic;
        private final String subtopic;
        private final String sectionType;
        private final Map<String, Object> metadata;
        
        public RelatedContent(String vectorId, String contentId, String content, float similarity,
                            String topic, String subtopic, String sectionType, Map<String, Object> metadata) {
            this.vectorId = vectorId;
            this.contentId = contentId;
            this.content = content;
            this.similarity = similarity;
            this.topic = topic;
            this.subtopic = subtopic;
            this.sectionType = sectionType;
            this.metadata = metadata;
        }

        public String getVectorId() { return vectorId; }
        public String getContentId() { return contentId; }
        public String getContent() { return content; }
        public float getSimilarity() { return similarity; }
        public String getTopic() { return topic; }
        public String getSubtopic() { return subtopic; }
        public String getSectionType() { return sectionType; }
        public Map<String, Object> getMetadata() { return metadata; }
        
        @Override
        public String toString() {
            return String.format("RelatedContent{id='%s', topic='%s', similarity=%.2f}", 
                               contentId, topic, similarity);
        }
    }
    
    public static class ContentAnalysis {
        private final int totalContent;
        private final int totalConnections;
        private final double averageConnections;
        private final String mostConnectedContent;
        
        public ContentAnalysis(int totalContent, int totalConnections, 
                             double averageConnections, String mostConnectedContent) {
            this.totalContent = totalContent;
            this.totalConnections = totalConnections;
            this.averageConnections = averageConnections;
            this.mostConnectedContent = mostConnectedContent;
        }

        public int getTotalContent() { return totalContent; }
        public int getTotalConnections() { return totalConnections; }
        public double getAverageConnections() { return averageConnections; }
        public String getMostConnectedContent() { return mostConnectedContent; }
        
        @Override
        public String toString() {
            return String.format("ContentAnalysis{content=%d, connections=%d, avg=%.1f, mostConnected='%s'}", 
                               totalContent, totalConnections, averageConnections, mostConnectedContent);
        }
    }
}
