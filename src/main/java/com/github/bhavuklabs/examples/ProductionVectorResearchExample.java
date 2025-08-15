package com.github.bhavuklabs.examples;

import com.github.bhavuklabs.builders.ConnectedContentBuilder;
import com.github.bhavuklabs.services.ContentVectorizer;
import com.github.bhavuklabs.services.EmbeddingService;
import com.github.bhavuklabs.services.impl.ProductionEmbeddingService;
import com.github.bhavuklabs.client.impl.ProductionLLMClient;
import com.github.bhavuklabs.vector.VectorStore;
import com.github.bhavuklabs.deepresearch.models.PersonalizedMarkdownConfig;
import com.github.bhavuklabs.core.enums.ModelType;

import java.util.*;
import java.util.logging.Logger;


public class ProductionVectorResearchExample {
    
    private static final Logger logger = Logger.getLogger(ProductionVectorResearchExample.class.getName());
    
    private final String sessionId;
    private final VectorStore vectorStore;
    private final EmbeddingService embeddingService;
    private final ContentVectorizer vectorizer;
    private final ProductionLLMClient llmClient;
    private final ConnectedContentBuilder contentBuilder;
    
    public ProductionVectorResearchExample() {
        this.sessionId = "production_session_" + System.currentTimeMillis();
        this.vectorStore = new VectorStore(384);
        this.embeddingService = new ProductionEmbeddingService();
        this.vectorizer = new ContentVectorizer(vectorStore, embeddingService);
        this.llmClient = new ProductionLLMClient(ModelType.OPENAI);
        this.contentBuilder = new ConnectedContentBuilder(llmClient, vectorizer, sessionId);
        
        logger.info("ProductionVectorResearchExample initialized with session: " + sessionId);
    }
    
    public void demonstrateConnectedResearch() {
        System.out.println("=== Production Vector-Enhanced Research System ===\n");

        PersonalizedMarkdownConfig beginnerConfig = createBeginnerConfig();
        PersonalizedMarkdownConfig intermediateConfig = createIntermediateConfig();
        PersonalizedMarkdownConfig advancedConfig = createAdvancedConfig();

        generateConnectedContent(beginnerConfig, intermediateConfig, advancedConfig);

        analyzeContentConnectivity();

        demonstrateSemanticDiscovery();

        demonstrateLearningPaths();
        
        System.out.println("\n=== Production Demo Complete ===");
    }
    
    private void generateConnectedContent(PersonalizedMarkdownConfig beginnerConfig, 
                                        PersonalizedMarkdownConfig intermediateConfig,
                                        PersonalizedMarkdownConfig advancedConfig) {
        
        System.out.println("Generating connected content across multiple expertise levels...\n");

        String[] beginnerTopics = {
            "Programming Fundamentals", "Variables and Data Types",
            "Control Structures", "Functions and Methods"
        };
        
        for (String topic : beginnerTopics) {
            generateAndDisplayContent(topic, "Basic Concepts", beginnerConfig, "Beginner");
        }

        String[] intermediateTopics = {
            "Object-Oriented Programming", "Data Structures Implementation",
            "Algorithm Design", "Database Integration"
        };
        
        for (String topic : intermediateTopics) {
            generateAndDisplayContent(topic, "Practical Application", intermediateConfig, "Intermediate");
        }

        String[] advancedTopics = {
            "System Architecture Design", "Performance Optimization",
            "Distributed Computing", "Advanced Algorithms"
        };
        
        for (String topic : advancedTopics) {
            generateAndDisplayContent(topic, "Expert Implementation", advancedConfig, "Advanced");
        }
    }
    
    private void generateAndDisplayContent(String topic, String subtopic, 
                                         PersonalizedMarkdownConfig config, String level) {
        try {
            System.out.println("Generating " + level + " content for: " + topic + " - " + subtopic);
            
            String content = contentBuilder.buildConnectedContent(topic, subtopic, config);
            
            System.out.println("Generated " + content.length() + " characters");
            System.out.println("Sample: " + content.substring(0, Math.min(content.length(), 20000)) + "...\n");
            
        } catch (Exception e) {
            logger.warning("Failed to generate content for " + topic + ": " + e.getMessage());
            System.out.println("Error generating content for " + topic + ": " + e.getMessage() + "\n");
        }
    }
    
    private void analyzeContentConnectivity() {
        System.out.println("=== Content Connectivity Analysis ===");
        
        ContentVectorizer.ContentAnalysis analysis = contentBuilder.getContentAnalysis();
        System.out.println("Total Content Pieces: " + analysis.getTotalContent());
        System.out.println("Total Connections: " + analysis.getTotalConnections());
        System.out.printf("Average Connections per Content: %.1f%n", analysis.getAverageConnections());
        System.out.println("Most Connected Content: " + analysis.getMostConnectedContent());

        Map<String, Set<String>> connections = vectorizer.getContentConnections(sessionId);
        System.out.println("\nDetailed Connection Map:");
        
        connections.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
                .forEach(entry -> {
                    String contentId = entry.getKey().replace(sessionId + ":", "");
                    Set<String> connectedIds = entry.getValue();
                    System.out.println("  " + contentId + " -> " + connectedIds.size() + " connections");
                    
                    connectedIds.stream()
                            .limit(3)
                            .forEach(connectedId -> {
                                String shortId = connectedId.replace(sessionId + ":", "");
                                System.out.println("    ├─ " + shortId);
                            });
                    
                    if (connectedIds.size() > 3) {
                        System.out.println("    └─ ... and " + (connectedIds.size() - 3) + " more");
                    }
                });
        
        System.out.println();
    }
    
    private void demonstrateSemanticDiscovery() {
        System.out.println("=== Semantic Content Discovery ===");
        
        String[] searchQueries = {
            "programming concepts and fundamentals",
            "data structure implementation techniques", 
            "algorithm design and optimization",
            "system architecture patterns",
            "performance tuning strategies"
        };
        
        for (String query : searchQueries) {
            System.out.println("Search Query: '" + query + "'");
            
            List<ContentVectorizer.RelatedContent> results = vectorizer.findRelatedContent(query, 3, sessionId);
            
            if (results.isEmpty()) {
                System.out.println("  No related content found\n");
            } else {
                for (ContentVectorizer.RelatedContent result : results) {
                    System.out.printf("  ├─ %s (similarity: %.3f)%n", 
                                    result.getTopic(), result.getSimilarity());
                    System.out.println("     " + result.getContent().substring(0, 
                                     Math.min(result.getContent().length(), 100)) + "...");
                }
                System.out.println();
            }
        }
    }
    
    private void demonstrateLearningPaths() {
        System.out.println("=== Learning Path Generation ===");

        List<String> targetConcepts = Arrays.asList(
            "machine learning fundamentals",
            "microservices architecture", 
            "container orchestration",
            "test-driven development",
            "continuous integration",
            "security best practices"
        );
        
        List<String> gaps = vectorizer.suggestContentGaps(sessionId, targetConcepts);
        System.out.println("Content Gaps Analysis:");
        System.out.println("  Missing coverage for:");
        for (String gap : gaps) {
            System.out.println("    ├─ " + gap);
        }

        Map<String, String> sessionContent = contentBuilder.getSessionContent();
        System.out.println("\nGenerated Session Content:");
        sessionContent.forEach((id, content) -> {
            String shortId = id.replace("_", " ").replace("Session Content", "");
            System.out.println("  ├─ " + shortId + " (" + content.length() + " chars)");
        });
        
        System.out.println();
    }

    
    private PersonalizedMarkdownConfig createBeginnerConfig() {
        return PersonalizedMarkdownConfig.builder()
                .maxTopicsPerSection(3)
                .maxSubtopicsPerTopic(4)
                .targetWordsPerSubtopic(150)
                .enableHierarchicalBreakdown(true)
                .includeExamples(true)
                .includeDiagrams(false)
                .enableProgressiveDisclosure(true)
                .userExpertiseLevel("beginner")
                .userInterests(Set.of("programming", "basics", "fundamentals"))
                .domainKnowledge(Map.of("programming", 2, "algorithms", 1, "systems", 1))
                .preferredLearningStyle(List.of("textual", "example-driven"))
                .adaptComplexity(true)
                .useEnhancedLists(true)
                .includeCodeBlocks(true)
                .enableHorizontalRules(true)
                .maxHeadingDepth(4)
                .autoGenerateAnchors(true)
                .requirePracticalExamples(true)
                .build();
    }
    
    private PersonalizedMarkdownConfig createIntermediateConfig() {
        return PersonalizedMarkdownConfig.builder()
                .maxTopicsPerSection(5)
                .maxSubtopicsPerTopic(6)
                .targetWordsPerSubtopic(250)
                .enableHierarchicalBreakdown(true)
                .includeExamples(true)
                .includeDiagrams(true)
                .enableProgressiveDisclosure(true)
                .userExpertiseLevel("intermediate")
                .userInterests(Set.of("programming", "architecture", "algorithms", "databases"))
                .domainKnowledge(Map.of("programming", 6, "algorithms", 4, "systems", 5, "databases", 4))
                .preferredLearningStyle(List.of("visual", "example-driven", "practical"))
                .adaptComplexity(true)
                .useEnhancedLists(true)
                .includeCodeBlocks(true)
                .enableHorizontalRules(true)
                .maxHeadingDepth(5)
                .autoGenerateAnchors(true)
                .requirePracticalExamples(true)
                .includeBestPractices(true)
                .build();
    }
    
    private PersonalizedMarkdownConfig createAdvancedConfig() {
        return PersonalizedMarkdownConfig.builder()
                .maxTopicsPerSection(8)
                .maxSubtopicsPerTopic(10)
                .targetWordsPerSubtopic(400)
                .enableHierarchicalBreakdown(true)
                .includeExamples(true)
                .includeDiagrams(true)
                .enableProgressiveDisclosure(false)
                .userExpertiseLevel("advanced")
                .userInterests(Set.of("architecture", "performance", "scalability", "distributed-systems"))
                .domainKnowledge(Map.of("programming", 9, "algorithms", 8, "systems", 9, "databases", 7, "architecture", 8))
                .preferredLearningStyle(List.of("theoretical", "comprehensive", "technical"))
                .adaptComplexity(true)
                .useEnhancedLists(true)
                .includeCodeBlocks(true)
                .enableHorizontalRules(true)
                .maxHeadingDepth(6)
                .autoGenerateAnchors(true)
                .requirePracticalExamples(true)
                .includeBestPractices(true)
                .build();
    }
    
    public static void main(String[] args) {
        try {
            ProductionVectorResearchExample example = new ProductionVectorResearchExample();
            example.demonstrateConnectedResearch();
        } catch (Exception e) {
            Logger.getLogger(ProductionVectorResearchExample.class.getName())
                  .severe("Demo failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
