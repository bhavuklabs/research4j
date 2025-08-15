package com.github.bhavuklabs.examples;

import com.github.bhavuklabs.deepresearch.engine.VectorEnhancedDeepResearchEngine;
import com.github.bhavuklabs.deepresearch.engine.VectorEnhancedDeepResearchEngine.VectorEnhancedResearchResult;
import com.github.bhavuklabs.deepresearch.models.DeepResearchConfig;
import com.github.bhavuklabs.deepresearch.models.PersonalizedMarkdownConfig;
import com.github.bhavuklabs.citation.service.CitationService;
import com.github.bhavuklabs.citation.config.CitationConfig;
import com.github.bhavuklabs.citation.enums.CitationSource;
import com.github.bhavuklabs.client.impl.ProductionLLMClient;
import com.github.bhavuklabs.core.enums.ModelType;

import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.concurrent.CompletableFuture;


public class QuickVectorTest {
    
    public static void main(String[] args) {
        System.out.println("=== Quick Vector-Enhanced Research Engine Test ===\n");
        
        try {

            System.out.println("1. Initializing VectorEnhancedDeepResearchEngine...");
            
            ProductionLLMClient llmClient = new ProductionLLMClient(ModelType.OPENAI);
            CitationConfig citationConfig = new CitationConfig(CitationSource.TAVILY, "test-key");
            CitationService citationService = new CitationService(citationConfig);
            
            VectorEnhancedDeepResearchEngine engine = new VectorEnhancedDeepResearchEngine(llmClient, citationService);
            System.out.println("✓ Engine initialized successfully!\n");

            System.out.println("2. Creating research configurations...");
            
            DeepResearchConfig researchConfig = DeepResearchConfig.builder()
                    .maxSources(10)
                    .enableQualityFiltering(true)
                    .maxRounds(2)
                    .minRelevanceScore(0.6)
                    .build();
            
            PersonalizedMarkdownConfig markdownConfig = PersonalizedMarkdownConfig.builder()
                    .maxTopicsPerSection(5)
                    .maxSubtopicsPerTopic(6)
                    .targetWordsPerSubtopic(200)
                    .userExpertiseLevel("intermediate")
                    .userInterests(Set.of("programming", "architecture"))
                    .domainKnowledge(Map.of("programming", 6, "architecture", 5))
                    .preferredLearningStyle(List.of("practical", "visual"))
                    .includeExamples(true)
                    .includeCodeBlocks(true)
                    .build();
            
            System.out.println("✓ Configurations created!\n");

            System.out.println("3. Executing vector-enhanced research...");
            String query = "microservices architecture best practices";
            System.out.println("Query: " + query);
            
            CompletableFuture<VectorEnhancedResearchResult> future = 
                engine.executeConnectedDeepResearch(query, researchConfig, markdownConfig);

            VectorEnhancedResearchResult result = future.get();
            
            System.out.println("✓ Research completed successfully!\n");

            System.out.println("4. Results Summary:");
            System.out.println("Session ID: " + result.getSessionId());
            System.out.println("Original Query: " + result.getOriginalQuery());
            System.out.println("Narrative Length: " + result.getNarrative().length() + " characters");
            System.out.println("Connected Narrative Length: " + result.getConnectedNarrative().length() + " characters");
            System.out.println("Connected Content Sections: " + result.getConnectedContent().size());
            System.out.println("Topic Relationships: " + result.getTopicRelationships().size());

            if (result.getContentAnalysis() != null) {
                System.out.println("Average Connectivity: " + 
                    String.format("%.3f", result.getContentAnalysis().getAverageConnectivity()));
                System.out.println("Key Insights: " + result.getContentAnalysis().getKeyInsights().size());
            }

            if (!result.getConnectedContent().isEmpty()) {
                String firstKey = result.getConnectedContent().keySet().iterator().next();
                String content = result.getConnectedContent().get(firstKey);
                System.out.println("\nSample Connected Content ('" + firstKey + "'):");
                System.out.println(content.substring(0, Math.min(content.length(), 300)) + "...");
            }
            
            System.out.println("\n=== Test Passed Successfully! ===");
            
        } catch (Exception e) {
            System.err.println("❌ Test Failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
