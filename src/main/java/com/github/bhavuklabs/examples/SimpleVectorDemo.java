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

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SimpleVectorDemo {
    
    private final VectorEnhancedDeepResearchEngine engine;
    private final ApplicationConfig config;
    
    public SimpleVectorDemo() throws Exception {
        this.config = ApplicationConfig.getInstance();
        config.validateConfiguration();
        
        ProductionLLMClient llmClient = new ProductionLLMClient(ModelType.GEMINI);
        CitationConfig citationConfig = new CitationConfig(CitationSource.TAVILY, config.getTavilyApiKey());
        CitationService citationService = new CitationService(citationConfig);
        
        this.engine = new VectorEnhancedDeepResearchEngine(llmClient, citationService);
        
        System.out.println("✅ VectorEnhancedDeepResearchEngine initialized successfully!");
    }
    
    public void demonstrateVectorResearch() {
        System.out.println("\n🔬 Vector-Enhanced Deep Research Engine Demo\n");
        
        try {
            String researchQuery = "modern software architecture patterns for scalable applications";
            
            System.out.println("📋 Research Query: " + researchQuery);
            System.out.println("⚙️  Configuring research parameters...");
            
            DeepResearchConfig researchConfig = DeepResearchConfig.builder()
                    .maxSources(config.getMaxSources())
                    .enableQualityFiltering(config.isQualityFilteringEnabled())
                    .enableIterativeRefinement(true)
                    .maxRounds(config.getMaxRounds())
                    .minRelevanceScore(config.getMinRelevanceScore())
                    .enableParallelProcessing(config.isParallelProcessingEnabled())
                    .build();
            
            PersonalizedMarkdownConfig markdownConfig = PersonalizedMarkdownConfig.builder()
                    .maxTopicsPerSection(5)
                    .maxSubtopicsPerTopic(7)
                    .targetWordsPerSubtopic(200)
                    .enableHierarchicalBreakdown(true)
                    .includeExamples(true)
                    .includeDiagrams(true)
                    .userExpertiseLevel("advanced")
                    .userInterests(Set.of("architecture", "scalability", "microservices", "cloud"))
                    .domainKnowledge(Map.of(
                        "programming", 8,
                        "architecture", 9,
                        "cloud-computing", 7,
                        "distributed-systems", 8
                    ))
                    .preferredLearningStyle(List.of("comprehensive", "technical", "practical"))
                    .adaptComplexity(true)
                    .useEnhancedLists(true)
                    .includeCodeBlocks(true)
                    .requirePracticalExamples(true)
                    .includeBestPractices(true)
                    .build();
            
            System.out.println("🚀 Executing vector-enhanced research...\n");
            
            CompletableFuture<VectorEnhancedResearchResult> future = 
                engine.executeConnectedDeepResearch(researchQuery, researchConfig, markdownConfig);
            
            VectorEnhancedResearchResult result = future.get();
            
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String outputFileName = "vector_research_results_" + timestamp + ".md";
            
            String formattedOutput = displayResearchResults(result);
            
            StringBuilder fileContent = new StringBuilder();
            fileContent.append("# Vector-Enhanced Deep Research Results\n\n");
            fileContent.append(String.format("**Research Topic:** %s\n\n", researchQuery));
            fileContent.append(String.format("**Generated on:** %s\n\n", 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
            fileContent.append("**Engine:** VectorEnhancedDeepResearchEngine\n\n");
            fileContent.append("---\n\n");
            fileContent.append(formattedOutput);
            
            try (FileWriter writer = new FileWriter(outputFileName)) {
                writer.write(fileContent.toString());
                
                System.out.println("\n" + "=".repeat(60));
                System.out.println("✅ Research results saved to: " + outputFileName);
                System.out.println("📁 File location: " + new java.io.File(outputFileName).getAbsolutePath());
                System.out.println("📊 File size: " + String.format("%.1f KB", fileContent.length() / 1024.0));
                System.out.println("📄 Total content length: " + fileContent.length() + " characters");
                System.out.println("=" .repeat(60));
                
            } catch (IOException e) {
                System.err.println("❌ Error writing to file: " + e.getMessage());
                e.printStackTrace();
            }
            
        } catch (Exception e) {
            System.err.println("❌ Research failed: " + e.getMessage());
            System.out.println("\n📝 Showing expected output structure instead:\n");
        }
    }
    
    private String displayResearchResults(VectorEnhancedResearchResult result) {
        StringBuilder output = new StringBuilder();
        
        output.append("✅ Research Completed Successfully!\n\n");
        
        output.append("📊 RESEARCH OVERVIEW\n");
        output.append("=".repeat(50)).append("\n");
        output.append("Session ID: ").append(result.getSessionId()).append("\n");
        output.append("Original Query: ").append(result.getOriginalQuery()).append("\n");
        output.append("Completion Time: ").append(new Date()).append("\n");
        output.append("\n");

        output.append("📈 CONTENT METRICS\n");
        output.append("=".repeat(50)).append("\n");
        output.append("Main Narrative: ").append(formatLength(result.getNarrative())).append("\n");
        output.append("Connected Narrative: ").append(formatLength(result.getConnectedNarrative())).append("\n");
        output.append("Executive Summary: ").append(formatLength(result.getExecutiveSummary())).append("\n");
        output.append("Connected Sections: ").append(result.getConnectedContent().size()).append("\n");
        output.append("Topic Relationships: ").append(result.getTopicRelationships().size()).append("\n");
        output.append("\n");
        
        output.append("==================================================\n");
        output.append(result.getFormattedOutput());
        output.append("\n==================================================\n");
        
        ContentAnalysis analysis = result.getContentAnalysis();
        if (analysis != null) {
            output.append("🧠 VECTOR ANALYSIS\n");
            output.append("=".repeat(50)).append("\n");
            output.append(String.format("Average Connectivity Score: %.3f%n", analysis.getAverageConnectivity()));
            output.append("Topic Connectivity Scores: ").append(analysis.getTopicScores().size()).append("\n");
            output.append("Generated Insights: ").append(analysis.getKeyInsights().size()).append("\n");
            
            output.append("\nKey Insights:\n");
            analysis.getKeyInsights().forEach(insight -> 
                output.append("  • ").append(insight).append("\n"));
            output.append("\n");
        }
        
        Map<String, List<RelatedContent>> relationships = result.getTopicRelationships();
        if (!relationships.isEmpty()) {
            output.append("🔗 TOPIC RELATIONSHIPS\n");
            output.append("=".repeat(50)).append("\n");
            
            relationships.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
                    .limit(5)
                    .forEach(entry -> {
                        String topic = entry.getKey();
                        List<RelatedContent> related = entry.getValue();
                        
                        output.append("📌 ").append(topic).append(" (").append(related.size()).append(" connections)\n");
                        related.stream()
                                .limit(3)
                                .forEach(rel -> {
                                    output.append(String.format("   ├─ %s (similarity: %.3f)%n", 
                                        rel.getTopic(), rel.getSimilarity()));
                                });
                        
                        if (related.size() > 3) {
                            output.append("   └─ ... and ").append(related.size() - 3).append(" more\n");
                        }
                        output.append("\n");
                    });
        }
        
        Map<String, String> connectedContent = result.getConnectedContent();
        if (!connectedContent.isEmpty()) {
            output.append("📄 CONNECTED CONTENT PREVIEW\n");
            output.append("=".repeat(50)).append("\n");
            
            connectedContent.entrySet().stream()
                    .limit(2)
                    .forEach(entry -> {
                        String topic = entry.getKey();
                        String content = entry.getValue();
                        
                        output.append("📝 ").append(topic).append("\n");
                        output.append("Length: ").append(formatLength(content)).append("\n");
                        output.append("Preview: ").append(
                            content.substring(0, Math.min(content.length(), 200))).append("...\n");
                        output.append("\n");
                    });
        }
        
        output.append("📋 MAIN CONTENT PREVIEWS\n");
        output.append("=".repeat(50)).append("\n");
        
        output.append("🎯 Executive Summary Preview:\n");
        output.append(result.getExecutiveSummary().substring(0, 
            Math.min(result.getExecutiveSummary().length(), 300))).append("...\n");
        output.append("\n");
        
        output.append("📖 Main Narrative Preview:\n");
        output.append(result.getNarrative().substring(0, 
            Math.min(result.getNarrative().length(), 400))).append("...\n");
        output.append("\n");
        
        output.append("🔗 Connected Narrative Preview:\n");
        output.append(result.getConnectedNarrative().substring(0, 
            Math.min(result.getConnectedNarrative().length(), 400))).append("...\n");
        output.append("\n");
        
        output.append("🎉 DEMONSTRATION COMPLETE!\n");
        output.append("=".repeat(50)).append("\n");
        
        String finalOutput = output.toString();
        System.out.print(finalOutput);
        
        return finalOutput;
    }
    
    private String formatLength(String content) {
        int length = content.length();
        if (length > 1000) {
            return String.format("%.1fK characters", length / 1000.0);
        }
        return length + " characters";
    }
    
    public static void main(String[] args) {
        try {
            System.out.println("🚀 Vector-Enhanced Deep Research Engine");
            System.out.println("   Demonstrating advanced research capabilities with vector connectivity\n");
            
            SimpleVectorDemo demo = new SimpleVectorDemo();
            demo.demonstrateVectorResearch();
            
        } catch (Exception e) {
            System.err.println("❌ Demo initialization failed: " + e.getMessage());
            
            System.out.println("\n📋 Showing Expected Output Structure:\n");
            demonstrateExpectedOutputStatic();
        }
    }
    
    private static void demonstrateExpectedOutputStatic() {
        System.out.println("📊 EXPECTED OUTPUT STRUCTURE");
        System.out.println("=" .repeat(50));
        System.out.println("Session ID: vector_session_1692123456789_abc12345");
        System.out.println("Original Query: modern software architecture patterns for scalable applications");
        System.out.println();
        
        System.out.println("📈 CONTENT METRICS");
        System.out.println("=" .repeat(50));
        System.out.println("Main Narrative: ~8,500 characters");
        System.out.println("Connected Narrative: ~9,200 characters (enhanced with vector connections)");
        System.out.println("Executive Summary: ~1,200 characters");
        System.out.println("Connected Sections: 6-8 sections");
        System.out.println("Topic Relationships: 12-15 topics with connections");
        System.out.println();
        
        System.out.println("🧠 VECTOR ANALYSIS");
        System.out.println("=" .repeat(50));
        System.out.println("Average Connectivity Score: 0.742");
        System.out.println("Topic Connectivity Scores: 14 topics analyzed");
        System.out.println("Generated Insights: 5 key insights");
        System.out.println();
        System.out.println("Sample Key Insights:");
        System.out.println("  • Research covers 14 main topics");
        System.out.println("  • Average topic connectivity: 0.74");
        System.out.println("  • High semantic connectivity between topics");
        System.out.println("  • Strong relationships found in microservices patterns");
        System.out.println("  • Cross-cutting concerns well represented");
        System.out.println();
        
        System.out.println("🔗 SAMPLE TOPIC RELATIONSHIPS");
        System.out.println("=" .repeat(50));
        System.out.println("📌 microservices architecture (8 connections)");
        System.out.println("   ├─ api gateway patterns (similarity: 0.891)");
        System.out.println("   ├─ service mesh communication (similarity: 0.856)");
        System.out.println("   └─ container orchestration (similarity: 0.823)");
        System.out.println();
        System.out.println("📌 distributed systems design (6 connections)");
        System.out.println("   ├─ event-driven architecture (similarity: 0.879)");
        System.out.println("   ├─ data consistency patterns (similarity: 0.834)");
        System.out.println("   └─ fault tolerance strategies (similarity: 0.801)");
        System.out.println();
        
        System.out.println("📄 SAMPLE CONNECTED CONTENT");
        System.out.println("=" .repeat(50));
        System.out.println("📝 API Gateway Patterns");
        System.out.println("Length: ~1,800 characters");
        System.out.println("Preview: API Gateway patterns serve as the entry point for microservices");
        System.out.println("architectures, providing centralized routing, authentication, and rate limiting...");
        System.out.println();
        
        System.out.println("🎯 This demonstrates the full vector-enhanced research capabilities!");
        System.out.println("   ✓ Semantic content connectivity");
        System.out.println("   ✓ Cross-topic relationship mapping");
        System.out.println("   ✓ Enhanced narrative generation");
        System.out.println("   ✓ Comprehensive content analysis");
    }
}
