package com.github.bhavuklabs.services.impl;

import com.github.bhavuklabs.services.EmbeddingService;

import java.util.Random;
import java.util.logging.Logger;


public class SimpleEmbeddingService implements EmbeddingService {
    
    private static final Logger logger = Logger.getLogger(SimpleEmbeddingService.class.getName());
    private static final int EMBEDDING_DIMENSIONS = 384;
    
    public SimpleEmbeddingService() {
        logger.info("SimpleEmbeddingService initialized with " + EMBEDDING_DIMENSIONS + " dimensions");
    }
    
    @Override
    public float[] generateEmbeddings(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new float[EMBEDDING_DIMENSIONS];
        }

        String normalizedText = text.toLowerCase().trim();

        float[] embedding = new float[EMBEDDING_DIMENSIONS];

        Random textRandom = new Random(normalizedText.hashCode());

        for (int i = 0; i < EMBEDDING_DIMENSIONS; i++) {
            embedding[i] = (float) textRandom.nextGaussian() * 0.1f;
        }

        addTextFeatures(embedding, normalizedText);

        normalizeVector(embedding);
        
        return embedding;
    }
    
    @Override
    public int getDimensions() {
        return EMBEDDING_DIMENSIONS;
    }
    
    
    private void addTextFeatures(float[] embedding, String text) {
        int length = text.length();
        String[] words = text.split("\\s+");
        int wordCount = words.length;

        if (length > 0) {
            embedding[0] += Math.min(length / 1000.0f, 1.0f); // Text length feature
            embedding[1] += Math.min(wordCount / 100.0f, 1.0f); // Word count feature
            embedding[2] += countUpperCase(text) / (float) length; // Uppercase ratio
            embedding[3] += countDigits(text) / (float) length; // Digit ratio
            embedding[4] += countPunctuation(text) / (float) length; // Punctuation ratio
        }

        for (int i = 0; i < Math.min(words.length, 20); i++) {
            String word = words[i];
            int wordHash = Math.abs(word.hashCode()) % 100;
            embedding[5 + i] += wordHash / 100.0f;
        }

        addKeywordFeatures(embedding, text);

        addNGramFeatures(embedding, text);
    }
    
    
    private void addKeywordFeatures(float[] embedding, String text) {
        String[] techKeywords = {
            "algorithm", "data", "structure", "function", "class", "object", "method",
            "variable", "loop", "condition", "array", "list", "map", "set", "tree",
            "graph", "network", "database", "query", "api", "service", "framework",
            "library", "module", "component", "interface", "abstract", "inheritance",
            "optimization", "performance", "cache", "memory", "storage", "retrieval",
            "search", "sort", "index", "scale", "distributed", "microservice", "pattern",
            "design", "architecture", "system", "client", "server", "web", "application"
        };
        
        for (int i = 0; i < techKeywords.length && i < 50; i++) {
            if (text.toLowerCase().contains(techKeywords[i])) {

                embedding[25 + (i % 25)] += 1.0f;

                embedding[50 + (i % 25)] += 0.8f;
                embedding[75 + (i % 25)] += 0.6f;
            }
        }

        addDomainFeatures(embedding, text);
    }
    
    
    private void addDomainFeatures(float[] embedding, String text) {
        String lowerText = text.toLowerCase();

        if (lowerText.contains("data") || lowerText.contains("structure") || 
            lowerText.contains("array") || lowerText.contains("list")) {
            for (int i = 100; i < 110; i++) {
                embedding[i] += 0.5f;
            }
        }

        if (lowerText.contains("algorithm") || lowerText.contains("sort") || 
            lowerText.contains("search") || lowerText.contains("optimization")) {
            for (int i = 110; i < 120; i++) {
                embedding[i] += 0.5f;
            }
        }

        if (lowerText.contains("database") || lowerText.contains("sql") || 
            lowerText.contains("query") || lowerText.contains("storage")) {
            for (int i = 120; i < 130; i++) {
                embedding[i] += 0.5f;
            }
        }

        if (lowerText.contains("system") || lowerText.contains("architecture") || 
            lowerText.contains("microservice") || lowerText.contains("scale")) {
            for (int i = 130; i < 140; i++) {
                embedding[i] += 0.5f;
            }
        }

        if (lowerText.contains("performance") || lowerText.contains("cache") || 
            lowerText.contains("optimization") || lowerText.contains("memory")) {
            for (int i = 140; i < 150; i++) {
                embedding[i] += 0.5f;
            }
        }
    }
    
    
    private void addNGramFeatures(float[] embedding, String text) {

        String[] words = text.split("\\s+");
        for (int i = 0; i < words.length - 1 && i < 25; i++) {
            String bigram = words[i] + " " + words[i + 1];
            int bigramHash = Math.abs(bigram.hashCode()) % 100;
            embedding[51 + i] += bigramHash / 100.0f;
        }

        for (int i = 0; i < text.length() - 2 && i < 25; i++) {
            String trigram = text.substring(i, i + 3);
            int trigramHash = Math.abs(trigram.hashCode()) % 100;
            embedding[76 + (i % 25)] += trigramHash / 1000.0f;
        }
    }
    
    
    private void normalizeVector(float[] vector) {
        float magnitude = 0.0f;
        for (float value : vector) {
            magnitude += value * value;
        }
        magnitude = (float) Math.sqrt(magnitude);
        
        if (magnitude > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= magnitude;
            }
        }
    }

    
    private int countUpperCase(String text) {
        return (int) text.chars().filter(Character::isUpperCase).count();
    }
    
    private int countDigits(String text) {
        return (int) text.chars().filter(Character::isDigit).count();
    }
    
    private int countPunctuation(String text) {
        return (int) text.chars().filter(ch -> !Character.isLetterOrDigit(ch) && !Character.isWhitespace(ch)).count();
    }
    
    @Override
    public String toString() {
        return "SimpleEmbeddingService{dimensions=" + EMBEDDING_DIMENSIONS + "}";
    }
}
