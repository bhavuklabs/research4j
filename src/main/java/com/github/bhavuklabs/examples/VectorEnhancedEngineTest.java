package com.github.bhavuklabs.examples;

import com.github.bhavuklabs.deepresearch.engine.VectorEnhancedDeepResearchEngine;
import com.github.bhavuklabs.deepresearch.engine.VectorEnhancedDeepResearchEngine.VectorEnhancedResearchResult;
import com.github.bhavuklabs.deepresearch.engine.VectorEnhancedDeepResearchEngine.ContentAnalysis;
import com.github.bhavuklabs.deepresearch.models.DeepResearchConfig;
import com.github.bhavuklabs.deepresearch.models.PersonalizedMarkdownConfig;
import com.github.bhavuklabs.citation.service.CitationService;
import com.github.bhavuklabs.citation.config.CitationConfig;
import com.github.bhavuklabs.citation.enums.CitationSource;
import com.github.bhavuklabs.client.impl.ProductionLLMClient;
import com.github.bhavuklabs.core.enums.ModelType;
import com.github.bhavuklabs.services.ContentVectorizer.RelatedContent;
import com.github.bhavuklabs.config.ApplicationConfig;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class VectorEnhancedEngineTest {
    
    private static final Logger logger = Logger.getLogger(VectorEnhancedEngineTest.class.getName());
    
    private final VectorEnhancedDeepResearchEngine engine;
    private final DeepResearchConfig researchConfig;
    private final PersonalizedMarkdownConfig markdownConfig;
    
    public VectorEnhancedEngineTest() throws Exception {
        ApplicationConfig config = ApplicationConfig.getInstance();
        ProductionLLMClient llmClient = new ProductionLLMClient(ModelType.GEMINI);
        CitationConfig citationConfig = new CitationConfig(CitationSource.TAVILY, config.getTavilyApiKey());
        CitationService citationService = new CitationService(citationConfig);
        
        this.engine = new VectorEnhancedDeepResearchEngine(llmClient, citationService);
        this.researchConfig = createResearchConfig();
        this.markdownConfig = createMarkdownConfig();
        
        logger.info("VectorEnhancedEngineTest initialized successfully");
    }
    
    public void runComprehensiveTest() {
        System.out.println("=== Vector-Enhanced Deep Research Engine Test ===\n");
        
        try {

            testBasicVectorResearch();

            testRelatedTopicsResearch();

            testContentConnectivity();

            testCrossSessionRelationships();

            testPerformanceMetrics();
            
            System.out.println("=== All Tests Completed Successfully ===");
            
        } catch (Exception e) {
            logger.severe("Test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void testBasicVectorResearch() throws Exception {
        System.out.println("Test 1: Basic Vector-Enhanced Research\n");
        
        String query = "microservices architecture patterns and best practices";
        
        System.out.println("Query: " + query);
        System.out.println("Executing vector-enhanced research...");
        
        CompletableFuture<VectorEnhancedResearchResult> future = 
            engine.executeConnectedDeepResearch(query, researchConfig, markdownConfig);
        
        VectorEnhancedResearchResult result = future.get();

        System.out.println("✓ Research completed successfully");
        System.out.println("Session ID: " + result.getSessionId());
        System.out.println("Original Query: " + result.getOriginalQuery());
        System.out.println("Narrative length: " + result.getNarrative().length() + " characters");
        System.out.println("Connected narrative length: " + result.getConnectedNarrative().length() + " characters");

        Map<String, String> connectedContent = result.getConnectedContent();
        System.out.println("Connected content sections: " + connectedContent.size());
        
        Map<String, List<RelatedContent>> relationships = result.getTopicRelationships();
        System.out.println("Topic relationships: " + relationships.size());
        
        ContentAnalysis analysis = result.getContentAnalysis();
        System.out.println("Content analysis - Average connectivity: " + 
                         String.format("%.3f", analysis.getAverageConnectivity()));
        System.out.println("Key insights: " + analysis.getKeyInsights().size());

        if (!connectedContent.isEmpty()) {
            String firstTopic = connectedContent.keySet().iterator().next();
            String content = connectedContent.get(firstTopic);
            System.out.println("\nSample connected content for '" + firstTopic + "':");
            System.out.println(content.substring(0, Math.min(content.length(), 500)) + "...");
        }
        
        System.out.println("\n" + "=".repeat(60) + "\n");
    }
    
    private void testRelatedTopicsResearch() throws Exception {
        System.out.println("Test 2: Multiple Related Topics Research\n");
        
        String[] relatedQueries = {
            "distributed systems design principles",
            "microservices communication patterns", 
            "container orchestration strategies",
            "API gateway implementation"
        };
        
        for (String query : relatedQueries) {
            System.out.println("Processing: " + query);
            
            CompletableFuture<VectorEnhancedResearchResult> future = 
                engine.executeConnectedDeepResearch(query, researchConfig, markdownConfig);
            
            VectorEnhancedResearchResult result = future.get();
            
            System.out.println("  ✓ Session: " + result.getSessionId());
            System.out.println("  ✓ Connected topics: " + result.getTopicRelationships().size());
            System.out.println("  ✓ Content sections: " + result.getConnectedContent().size());
            
            ContentAnalysis analysis = result.getContentAnalysis();
            System.out.println("  ✓ Avg connectivity: " + 
                             String.format("%.3f", analysis.getAverageConnectivity()));
            System.out.println();
        }
        
        System.out.println("=".repeat(60) + "\n");
    }
    
    private void testContentConnectivity() throws Exception {
        System.out.println("Test 3: Content Connectivity Analysis\n");
        
        String query = "event-driven architecture and messaging patterns";
        
        CompletableFuture<VectorEnhancedResearchResult> future = 
            engine.executeConnectedDeepResearch(query, researchConfig, markdownConfig);
        
        VectorEnhancedResearchResult result = future.get();

        Map<String, List<RelatedContent>> relationships = result.getTopicRelationships();
        
        System.out.println("Detailed Connectivity Analysis:");
        System.out.println("Total topics with relationships: " + relationships.size());
        
        relationships.forEach((topic, relatedContent) -> {
            System.out.println("\nTopic: " + topic);
            System.out.println("  Related content pieces: " + relatedContent.size());
            
            relatedContent.forEach(related -> {
                System.out.printf("    ├─ %s (similarity: %.3f)%n", 
                                related.getTopic(), related.getSimilarity());
                System.out.println("       " + related.getContent().substring(0, 
                                 Math.min(related.getContent().length(), 100)) + "...");
            });
        });

        ContentAnalysis analysis = result.getContentAnalysis();
        Map<String, Double> topicScores = analysis.getTopicScores();
        
        System.out.println("\nTopic Connectivity Scores:");
        topicScores.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .forEach(entry -> {
                    System.out.printf("  %s: %.3f%n", entry.getKey(), entry.getValue());
                });
        
        System.out.println("\nKey Insights:");
        analysis.getKeyInsights().forEach(insight -> {
            System.out.println("  • " + insight);
        });
        
        System.out.println("\n" + "=".repeat(60) + "\n");
    }
    
    private void testCrossSessionRelationships() throws Exception {
        System.out.println("Test 4: Cross-Session Relationships\n");

        String[] sessionQueries = {
            "machine learning model deployment",
            "CI/CD pipeline automation",
            "infrastructure as code practices"
        };
        
        VectorEnhancedResearchResult[] results = new VectorEnhancedResearchResult[sessionQueries.length];
        
        for (int i = 0; i < sessionQueries.length; i++) {
            System.out.println("Session " + (i + 1) + ": " + sessionQueries[i]);
            
            CompletableFuture<VectorEnhancedResearchResult> future = 
                engine.executeConnectedDeepResearch(sessionQueries[i], researchConfig, markdownConfig);
            
            results[i] = future.get();
            System.out.println("  ✓ Completed session: " + results[i].getSessionId());
        }

        System.out.println("\nCross-Session Analysis:");
        for (int i = 0; i < results.length; i++) {
            VectorEnhancedResearchResult result = results[i];
            System.out.println("\nSession " + (i + 1) + " (" + result.getSessionId() + "):");
            System.out.println("  Topics: " + result.getTopicRelationships().keySet());
            System.out.println("  Connectivity: " + 
                             String.format("%.3f", result.getContentAnalysis().getAverageConnectivity()));
        }
        
        System.out.println("\n" + "=".repeat(60) + "\n");
    }
    
    private void testPerformanceMetrics() throws Exception {
        System.out.println("Test 5: Performance and Scalability\n");
        
        String query = "cloud-native application architecture";
        
        long startTime = System.currentTimeMillis();
        
        CompletableFuture<VectorEnhancedResearchResult> future = 
            engine.executeConnectedDeepResearch(query, researchConfig, markdownConfig);
        
        VectorEnhancedResearchResult result = future.get();
        
        long endTime = System.currentTimeMillis();
        long executionTime = endTime - startTime;
        
        System.out.println("Performance Metrics:");
        System.out.println("  Execution time: " + executionTime + " ms");
        System.out.println("  Content generated: " + result.getNarrative().length() + " characters");
        System.out.println("  Connected sections: " + result.getConnectedContent().size());
        System.out.println("  Topic relationships: " + result.getTopicRelationships().size());

        double contentPerMs = (double) result.getNarrative().length() / executionTime;
        double connectionsPerMs = (double) result.getTopicRelationships().size() / executionTime;
        
        System.out.printf("  Content generation rate: %.2f chars/ms%n", contentPerMs);
        System.out.printf("  Relationship discovery rate: %.2f connections/ms%n", connectionsPerMs);
        
        ContentAnalysis analysis = result.getContentAnalysis();
        System.out.println("  Content quality score: " + 
                         String.format("%.3f", analysis.getAverageConnectivity()));

        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        System.out.println("  Memory usage: " + (usedMemory / 1024 / 1024) + " MB");
        
        System.out.println("\n" + "=".repeat(60) + "\n");
    }
    
    private DeepResearchConfig createResearchConfig() {
        return DeepResearchConfig.builder()
                .maxSources(15)
                .enableDeepDiveMode(true)
                .enableIterativeRefinement(true)
                .enableQualityFiltering(true)
                .maxRounds(3)
                .minRelevanceScore(0.7)
                .build();
    }
    
    private PersonalizedMarkdownConfig createMarkdownConfig() {
        return PersonalizedMarkdownConfig.builder()
                .maxTopicsPerSection(6)
                .maxSubtopicsPerTopic(8)
                .targetWordsPerSubtopic(300)
                .enableHierarchicalBreakdown(true)
                .includeExamples(true)
                .includeDiagrams(true)
                .enableProgressiveDisclosure(true)
                .userExpertiseLevel("intermediate")
                .userInterests(Set.of("architecture", "microservices", "distributed-systems"))
                .domainKnowledge(Map.of("programming", 7, "architecture", 8, "systems", 7))
                .preferredLearningStyle(List.of("visual", "practical", "comprehensive"))
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
    
    public static void main(String[] args) {
        try {
            VectorEnhancedEngineTest test = new VectorEnhancedEngineTest();
            test.runComprehensiveTest();
        } catch (Exception e) {
            Logger.getLogger(VectorEnhancedEngineTest.class.getName())
                  .severe("Test execution failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
