package com.github.bhavuklabs.vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class VectorStore {
    
    private static final Logger logger = Logger.getLogger(VectorStore.class.getName());
    
    private final Map<String, VectorDocument> documents;
    private final int dimensions;
    
    public VectorStore() {
        this(384);
    }
    
    public VectorStore(int dimensions) {
        this.documents = new ConcurrentHashMap<>();
        this.dimensions = dimensions;
        logger.info("VectorStore initialized with " + dimensions + " dimensions");
    }
    
    public void store(String id, float[] vector, String content, Map<String, Object> metadata) {
        if (vector.length != dimensions) {
            throw new IllegalArgumentException("Vector dimension mismatch. Expected: " + dimensions + ", got: " + vector.length);
        }
        
        VectorDocument doc = new VectorDocument(id, vector, content, metadata);
        documents.put(id, doc);
        logger.fine("Stored document: " + id);
    }
    
    public VectorDocument retrieve(String id) {
        return documents.get(id);
    }
    
    public List<SimilarityResult> search(float[] queryVector, int topK) {
        if (queryVector.length != dimensions) {
            throw new IllegalArgumentException("Query vector dimension mismatch. Expected: " + dimensions + ", got: " + queryVector.length);
        }
        
        return documents.values().stream()
                .map(doc -> new SimilarityResult(doc, cosineSimilarity(queryVector, doc.getVector())))
                .sorted((a, b) -> Float.compare(b.getSimilarity(), a.getSimilarity()))
                .limit(topK)
                .collect(Collectors.toList());
    }
    
    public boolean delete(String id) {
        return documents.remove(id) != null;
    }
    
    public int size() {
        return documents.size();
    }
    
    public void clear() {
        documents.clear();
        logger.info("VectorStore cleared");
    }
    
    private float cosineSimilarity(float[] vec1, float[] vec2) {
        float dotProduct = 0.0f;
        float norm1 = 0.0f;
        float norm2 = 0.0f;
        
        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }
        
        if (norm1 == 0.0f || norm2 == 0.0f) {
            return 0.0f;
        }
        
        return dotProduct / (float)(Math.sqrt(norm1) * Math.sqrt(norm2));
    }
    
    public static class VectorDocument {
        private final String id;
        private final float[] vector;
        private final String content;
        private final Map<String, Object> metadata;
        
        public VectorDocument(String id, float[] vector, String content, Map<String, Object> metadata) {
            this.id = id;
            this.vector = vector.clone();
            this.content = content;
            this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
        }
        
        public String getId() { return id; }
        public float[] getVector() { return vector.clone(); }
        public String getContent() { return content; }
        public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }
    }
    
    public static class SimilarityResult {
        private final VectorDocument document;
        private final float similarity;
        
        public SimilarityResult(VectorDocument document, float similarity) {
            this.document = document;
            this.similarity = similarity;
        }
        
        public VectorDocument getDocument() { return document; }
        public float getSimilarity() { return similarity; }
    }
}
