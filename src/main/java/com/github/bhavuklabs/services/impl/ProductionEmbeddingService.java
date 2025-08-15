package com.github.bhavuklabs.services.impl;

import com.github.bhavuklabs.services.EmbeddingService;

import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;


public class ProductionEmbeddingService implements EmbeddingService {
    
    private static final Logger logger = Logger.getLogger(ProductionEmbeddingService.class.getName());
    private static final int EMBEDDING_DIMENSIONS = 384;

    private final Map<String, float[]> embeddingCache = new ConcurrentHashMap<>();

    private final Map<String, Float> technicalVocabulary;
    private final Map<String, Float> conceptWeights;
    
    public ProductionEmbeddingService() {
        this.technicalVocabulary = initializeTechnicalVocabulary();
        this.conceptWeights = initializeConceptWeights();
        logger.info("ProductionEmbeddingService initialized with " + EMBEDDING_DIMENSIONS + " dimensions");
    }
    
    @Override
    public float[] generateEmbeddings(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new float[EMBEDDING_DIMENSIONS];
        }

        String cacheKey = text.toLowerCase().trim();
        if (embeddingCache.containsKey(cacheKey)) {
            return embeddingCache.get(cacheKey).clone();
        }

        float[] embedding = computeEmbedding(text);

        embeddingCache.put(cacheKey, embedding.clone());
        
        return embedding;
    }
    
    @Override
    public int getDimensions() {
        return EMBEDDING_DIMENSIONS;
    }
    
    
    private float[] computeEmbedding(String text) {
        String normalizedText = normalizeText(text);
        String[] words = tokenizeText(normalizedText);
        String[] sentences = splitIntoSentences(text);
        
        float[] embedding = new float[EMBEDDING_DIMENSIONS];

        extractBasicTextFeatures(embedding, normalizedText, words, 0, 50);
        extractTechnicalVocabularyFeatures(embedding, words, 50, 100);
        extractSemanticConceptFeatures(embedding, normalizedText, 100, 150);
        extractStructuralFeatures(embedding, text, sentences, 150, 200);
        extractNGramFeatures(embedding, words, 200, 250);
        extractSentimentAndToneFeatures(embedding, normalizedText, 250, 300);
        extractDomainSpecificFeatures(embedding, normalizedText, 300, 350);
        extractContextualFeatures(embedding, sentences, 350, 384);

        normalizeVector(embedding);
        
        return embedding;
    }
    
    
    private void extractBasicTextFeatures(float[] embedding, String text, String[] words, int start, int end) {
        int length = text.length();
        int wordCount = words.length;

        embedding[start] = Math.min(length / 1000.0f, 1.0f);
        embedding[start + 1] = Math.min(wordCount / 100.0f, 1.0f);
        embedding[start + 2] = wordCount > 0 ? (float) length / wordCount : 0; // Average word length

        embedding[start + 3] = countPattern(text, "[A-Z]") / (float) length; // Uppercase ratio
        embedding[start + 4] = countPattern(text, "\\d") / (float) length; // Digit ratio
        embedding[start + 5] = countPattern(text, "[^\\w\\s]") / (float) length; // Punctuation ratio

        long uniqueWords = java.util.Arrays.stream(words).distinct().count();
        embedding[start + 6] = wordCount > 0 ? (float) uniqueWords / wordCount : 0; // Vocabulary diversity

        embedding[start + 7] = countPattern(text, "\\{|\\}|\\[|\\]|\\(|\\)") / (float) length; // Bracket ratio
        embedding[start + 8] = countPattern(text, "=|==|!=|<=|>=") / (float) length; // Operator ratio
        embedding[start + 9] = countPattern(text, "\\w+\\(.*\\)") / (float) wordCount; // Function-like patterns

        Map<String, Integer> wordFreq = getWordFrequencies(words);
        int i = start + 10;
        for (Map.Entry<String, Integer> entry : wordFreq.entrySet()) {
            if (i >= end) break;
            embedding[i++] = Math.min(entry.getValue() / (float) wordCount, 0.1f);
        }
    }
    
    
    private void extractTechnicalVocabularyFeatures(float[] embedding, String[] words, int start, int end) {
        int featureIndex = start;
        
        for (Map.Entry<String, Float> entry : technicalVocabulary.entrySet()) {
            if (featureIndex >= end) break;
            
            String term = entry.getKey();
            float weight = entry.getValue();

            long count = java.util.Arrays.stream(words)
                    .filter(word -> word.toLowerCase().contains(term.toLowerCase()))
                    .count();
            
            embedding[featureIndex++] = Math.min((count * weight) / words.length, 1.0f);
        }
    }
    
    
    private void extractSemanticConceptFeatures(float[] embedding, String text, int start, int end) {
        int featureIndex = start;
        
        for (Map.Entry<String, Float> entry : conceptWeights.entrySet()) {
            if (featureIndex >= end) break;
            
            String concept = entry.getKey();
            float weight = entry.getValue();

            boolean hasMatch = Pattern.compile("\\b" + Pattern.quote(concept) + "\\b", 
                                             Pattern.CASE_INSENSITIVE).matcher(text).find();
            
            embedding[featureIndex++] = hasMatch ? weight : 0.0f;
        }
    }
    
    
    private void extractStructuralFeatures(float[] embedding, String text, String[] sentences, int start, int end) {

        embedding[start] = countPattern(text, "^#+\\s") / (float) sentences.length; // Heading ratio
        embedding[start + 1] = countPattern(text, "^-\\s|^\\*\\s|^\\d+\\.\\s") / (float) sentences.length; // List ratio
        embedding[start + 2] = countPattern(text, "```") / 2.0f / sentences.length; // Code block ratio
        embedding[start + 3] = countPattern(text, "\\*\\*.*\\*\\*|__.*__") / (float) sentences.length; // Bold text ratio

        float avgSentenceLength = sentences.length > 0 ? 
            (float) java.util.Arrays.stream(sentences).mapToInt(String::length).average().orElse(0) : 0;
        embedding[start + 4] = Math.min(avgSentenceLength / 100.0f, 1.0f);

        embedding[start + 5] = countPattern(text, "\\?") / (float) sentences.length; // Question ratio
        embedding[start + 6] = countPattern(text, "!") / (float) sentences.length; // Exclamation ratio

        String[] paragraphs = text.split("\n\n");
        embedding[start + 7] = Math.min(paragraphs.length / 10.0f, 1.0f); // Paragraph count

        for (int i = start + 8; i < end; i++) {
            embedding[i] = (float) Math.random() * 0.1f; // Small random values for unused dimensions
        }
    }
    
    
    private void extractNGramFeatures(float[] embedding, String[] words, int start, int end) {
        int featureIndex = start;

        for (int i = 0; i < words.length - 1 && featureIndex < end - 25; i++) {
            String bigram = words[i] + " " + words[i + 1];
            float hash = Math.abs(bigram.hashCode()) % 1000 / 1000.0f;
            embedding[featureIndex++] = hash * 0.1f;
        }

        for (int i = 0; i < words.length - 2 && featureIndex < end; i++) {
            String trigram = words[i] + " " + words[i + 1] + " " + words[i + 2];
            float hash = Math.abs(trigram.hashCode()) % 1000 / 1000.0f;
            embedding[featureIndex++] = hash * 0.05f;
        }
    }
    
    
    private void extractSentimentAndToneFeatures(float[] embedding, String text, int start, int end) {

        String[] positiveWords = {"good", "great", "excellent", "effective", "efficient", "optimal", "better", "best", "improved"};
        embedding[start] = countWordsPresence(text, positiveWords);

        String[] negativeWords = {"bad", "poor", "inefficient", "slow", "problem", "issue", "error", "fail", "worst"};
        embedding[start + 1] = countWordsPresence(text, negativeWords);

        String[] confidenceWords = {"proven", "reliable", "stable", "robust", "tested", "validated", "established"};
        embedding[start + 2] = countWordsPresence(text, confidenceWords);

        String[] uncertaintyWords = {"might", "could", "possibly", "perhaps", "maybe", "uncertain", "unclear"};
        embedding[start + 3] = countWordsPresence(text, uncertaintyWords);

        for (int i = start + 4; i < end; i++) {
            embedding[i] = analyzeToneFeature(text, i - start - 4);
        }
    }
    
    
    private void extractDomainSpecificFeatures(float[] embedding, String text, int start, int end) {

        String[] programmingTerms = {"class", "function", "method", "variable", "object", "interface", "inheritance"};
        embedding[start] = countWordsPresence(text, programmingTerms);

        String[] dataStructureTerms = {"array", "list", "map", "set", "tree", "graph", "stack", "queue"};
        embedding[start + 1] = countWordsPresence(text, dataStructureTerms);

        String[] algorithmTerms = {"algorithm", "sort", "search", "optimize", "complexity", "performance", "efficiency"};
        embedding[start + 2] = countWordsPresence(text, algorithmTerms);

        String[] databaseTerms = {"database", "table", "query", "sql", "nosql", "index", "transaction"};
        embedding[start + 3] = countWordsPresence(text, databaseTerms);

        String[] architectureTerms = {"architecture", "system", "design", "pattern", "microservice", "api", "service"};
        embedding[start + 4] = countWordsPresence(text, architectureTerms);

        for (int i = start + 5; i < end; i++) {
            embedding[i] = analyzeDomainFeature(text, i - start - 5);
        }
    }
    
    
    private void extractContextualFeatures(float[] embedding, String[] sentences, int start, int end) {

        String[] transitionWords = {"however", "therefore", "moreover", "furthermore", "additionally", "consequently"};
        float transitionScore = 0;
        for (String sentence : sentences) {
            for (String transition : transitionWords) {
                if (sentence.toLowerCase().contains(transition)) {
                    transitionScore += 1.0f;
                }
            }
        }
        embedding[start] = Math.min(transitionScore / sentences.length, 1.0f);

        embedding[start + 1] = countPattern(String.join(" ", sentences), "for example|such as|namely|specifically") / (float) sentences.length;

        embedding[start + 2] = countPattern(String.join(" ", sentences), "compared to|versus|rather than|instead of") / (float) sentences.length;

        for (int i = start + 3; i < end; i++) {
            embedding[i] = analyzeContextualFeature(sentences, i - start - 3);
        }
    }

    
    private String normalizeText(String text) {
        return text.toLowerCase()
                   .replaceAll("\\s+", " ")
                   .replaceAll("[^a-zA-Z0-9\\s\\-_.]", " ")
                   .trim();
    }
    
    private String[] tokenizeText(String text) {
        return text.split("\\s+");
    }
    
    private String[] splitIntoSentences(String text) {
        return text.split("[.!?]+");
    }
    
    private int countPattern(String text, String pattern) {
        return (int) Pattern.compile(pattern, Pattern.MULTILINE).matcher(text).results().count();
    }
    
    private float countWordsPresence(String text, String[] words) {
        String lowerText = text.toLowerCase();
        int count = 0;
        for (String word : words) {
            if (lowerText.contains(word.toLowerCase())) {
                count++;
            }
        }
        return Math.min(count / (float) words.length, 1.0f);
    }
    
    private Map<String, Integer> getWordFrequencies(String[] words) {
        Map<String, Integer> frequencies = new ConcurrentHashMap<>();
        for (String word : words) {
            if (word.length() > 3) { // Only count meaningful words
                frequencies.merge(word.toLowerCase(), 1, Integer::sum);
            }
        }
        return frequencies;
    }
    
    private float analyzeToneFeature(String text, int featureIndex) {

        String[] toneIndicators = {"formal", "informal", "technical", "academic", "practical", "theoretical"};
        if (featureIndex < toneIndicators.length) {
            return text.toLowerCase().contains(toneIndicators[featureIndex]) ? 0.5f : 0.0f;
        }
        return 0.0f;
    }
    
    private float analyzeDomainFeature(String text, int featureIndex) {

        return Math.abs(text.hashCode() + featureIndex) % 100 / 1000.0f;
    }
    
    private float analyzeContextualFeature(String[] sentences, int featureIndex) {

        if (sentences.length > featureIndex) {
            return Math.abs(sentences[featureIndex].hashCode()) % 100 / 1000.0f;
        }
        return 0.0f;
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
    
    
    private Map<String, Float> initializeTechnicalVocabulary() {
        Map<String, Float> vocab = new ConcurrentHashMap<>();

        vocab.put("algorithm", 0.9f);
        vocab.put("data", 0.8f);
        vocab.put("structure", 0.8f);
        vocab.put("function", 0.7f);
        vocab.put("class", 0.7f);
        vocab.put("object", 0.7f);
        vocab.put("method", 0.6f);
        vocab.put("variable", 0.5f);
        vocab.put("array", 0.8f);
        vocab.put("list", 0.7f);
        vocab.put("map", 0.7f);
        vocab.put("set", 0.6f);
        vocab.put("tree", 0.8f);
        vocab.put("graph", 0.8f);
        vocab.put("database", 0.9f);
        vocab.put("query", 0.7f);
        vocab.put("index", 0.6f);
        vocab.put("performance", 0.8f);
        vocab.put("optimization", 0.8f);
        vocab.put("complexity", 0.8f);
        vocab.put("architecture", 0.9f);
        vocab.put("system", 0.7f);
        vocab.put("design", 0.7f);
        vocab.put("pattern", 0.7f);
        vocab.put("framework", 0.7f);
        vocab.put("library", 0.6f);
        vocab.put("api", 0.7f);
        vocab.put("service", 0.6f);
        vocab.put("microservice", 0.8f);
        vocab.put("distributed", 0.8f);
        vocab.put("scalable", 0.7f);
        vocab.put("concurrent", 0.8f);
        vocab.put("parallel", 0.7f);
        vocab.put("async", 0.7f);
        vocab.put("synchronous", 0.6f);
        vocab.put("thread", 0.7f);
        vocab.put("process", 0.6f);
        vocab.put("memory", 0.7f);
        vocab.put("cache", 0.7f);
        vocab.put("storage", 0.6f);
        vocab.put("network", 0.7f);
        vocab.put("protocol", 0.7f);
        vocab.put("security", 0.8f);
        vocab.put("authentication", 0.7f);
        vocab.put("authorization", 0.7f);
        vocab.put("encryption", 0.7f);
        vocab.put("testing", 0.7f);
        vocab.put("debugging", 0.6f);
        vocab.put("deployment", 0.7f);
        vocab.put("monitoring", 0.6f);
        vocab.put("logging", 0.5f);
        
        return vocab;
    }
    
    
    private Map<String, Float> initializeConceptWeights() {
        Map<String, Float> concepts = new ConcurrentHashMap<>();

        concepts.put("implementation", 0.8f);
        concepts.put("interface", 0.7f);
        concepts.put("inheritance", 0.7f);
        concepts.put("polymorphism", 0.8f);
        concepts.put("encapsulation", 0.7f);
        concepts.put("abstraction", 0.8f);
        concepts.put("composition", 0.7f);
        concepts.put("dependency", 0.6f);
        concepts.put("injection", 0.7f);
        concepts.put("inversion", 0.7f);
        concepts.put("principle", 0.6f);
        concepts.put("strategy", 0.7f);
        concepts.put("factory", 0.7f);
        concepts.put("singleton", 0.6f);
        concepts.put("observer", 0.7f);
        concepts.put("decorator", 0.7f);
        concepts.put("adapter", 0.6f);
        concepts.put("facade", 0.6f);
        concepts.put("proxy", 0.6f);
        concepts.put("builder", 0.7f);
        concepts.put("prototype", 0.6f);
        concepts.put("command", 0.6f);
        concepts.put("iterator", 0.6f);
        concepts.put("template", 0.6f);
        concepts.put("visitor", 0.6f);
        concepts.put("state", 0.6f);
        concepts.put("bridge", 0.6f);
        concepts.put("composite", 0.6f);
        concepts.put("flyweight", 0.5f);
        concepts.put("mediator", 0.5f);
        concepts.put("memento", 0.5f);
        concepts.put("chain", 0.6f);
        concepts.put("responsibility", 0.6f);
        concepts.put("interpreter", 0.5f);

        concepts.put("reactive", 0.8f);
        concepts.put("functional", 0.8f);
        concepts.put("imperative", 0.6f);
        concepts.put("declarative", 0.7f);
        concepts.put("immutable", 0.7f);
        concepts.put("mutable", 0.5f);
        concepts.put("closure", 0.7f);
        concepts.put("lambda", 0.7f);
        concepts.put("stream", 0.7f);
        concepts.put("pipeline", 0.7f);
        concepts.put("transformation", 0.6f);
        concepts.put("aggregation", 0.6f);
        concepts.put("reduction", 0.6f);
        concepts.put("mapping", 0.6f);
        concepts.put("filtering", 0.6f);
        concepts.put("partitioning", 0.6f);
        concepts.put("grouping", 0.6f);
        concepts.put("sorting", 0.7f);
        concepts.put("searching", 0.7f);
        concepts.put("indexing", 0.6f);
        concepts.put("hashing", 0.7f);
        concepts.put("compression", 0.6f);
        concepts.put("serialization", 0.6f);
        concepts.put("deserialization", 0.6f);
        concepts.put("marshalling", 0.5f);
        concepts.put("unmarshalling", 0.5f);
        
        return concepts;
    }
    
    public void clearCache() {
        embeddingCache.clear();
        logger.info("Embedding cache cleared");
    }
    
    public int getCacheSize() {
        return embeddingCache.size();
    }
}
