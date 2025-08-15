package com.github.bhavuklabs.examples;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import com.github.bhavuklabs.citation.service.CitationService;
import com.github.bhavuklabs.core.contracts.LLMClient;
import com.github.bhavuklabs.core.payloads.LLMResponse;
import com.github.bhavuklabs.deepresearch.models.DeepResearchConfig;
import com.github.bhavuklabs.deepresearch.models.PersonalizedMarkdownConfig;
import com.github.bhavuklabs.deepresearch.services.VectorEnhancedResearchService;
import com.github.bhavuklabs.deepresearch.services.VectorEnhancedResearchService.VectorEnhancedResearchResult;
import com.github.bhavuklabs.deepresearch.services.VectorEnhancedResearchService.ContentGapAnalysis;
import com.github.bhavuklabs.deepresearch.services.VectorEnhancedResearchService.LearningPath;
import com.github.bhavuklabs.services.ContentVectorizer.ContentAnalysis;


public class IntegratedVectorResearchExample {
    
    private static final Logger logger = Logger.getLogger(IntegratedVectorResearchExample.class.getName());
    
    private final VectorEnhancedResearchService researchService;
    
    public IntegratedVectorResearchExample() {

        MockLLMClient llmClient = new MockLLMClient();
        CitationService citationService = createMockCitationService();
        
        this.researchService = new VectorEnhancedResearchService(llmClient, citationService);
        
        logger.info("IntegratedVectorResearchExample initialized with vector-enhanced capabilities");
    }
    
    public static void main(String[] args) {
        IntegratedVectorResearchExample example = new IntegratedVectorResearchExample();
        example.runComprehensiveDemo();
    }
    
    
    public void runComprehensiveDemo() {
        System.out.println("=== Integrated Vector-Enhanced Deep Research System Demo ===\\n");
        
        try {

            demonstrateFullResearchWorkflow();

            demonstratePersonalizedContentGeneration();

            demonstrateContentConnectivity();

            demonstrateLearningPathGeneration();

            demonstrateContentGapAnalysis();
            
            System.out.println("\\n=== All Demonstrations Completed Successfully ===");
            
        } catch (Exception e) {
            logger.severe("Demo failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    
    private void demonstrateFullResearchWorkflow() {
        System.out.println("## Demo 1: Complete Vector-Enhanced Research Workflow\\n");
        
        String researchQuery = "Machine Learning Model Deployment Strategies";

        DeepResearchConfig researchConfig = DeepResearchConfig.builder()
                .researchDepth(DeepResearchConfig.ResearchDepth.COMPREHENSIVE)
                .maxSources(20)
                .outputFormat(com.github.bhavuklabs.core.enums.OutputFormat.MARKDOWN)
                .build();

        PersonalizedMarkdownConfig personalizedConfig = PersonalizedMarkdownConfig.forIntermediate(
                Set.of("machine learning", "deployment", "cloud computing"),
                Map.of("machine learning", 7, "devops", 5, "cloud", 6)
        );
        
        try {
            CompletableFuture<VectorEnhancedResearchResult> futureResult = researchService
                    .executeConnectedResearch(researchQuery, researchConfig, personalizedConfig);
            
            VectorEnhancedResearchResult result = futureResult.get();
            
            System.out.println("### Research Results Summary:");
            System.out.println("- **Query**: " + result.getOriginalQuery());
            System.out.println("- **Session**: " + result.getSessionId());
            System.out.println("- **Connected Topics**: " + result.getConnectedContent().size());
            System.out.println("- **Topic Relationships**: " + result.getTopicRelationships().size());
            
            if (result.getContentAnalysis() != null) {
                ContentAnalysis analysis = result.getContentAnalysis();
                System.out.println("- **Content Connections**: " + analysis.getTotalConnections());
                System.out.println("- **Average Connections**: " + String.format("%.1f", analysis.getAverageConnections()));
            }
            
            System.out.println("\\n### Sample Connected Content:");
            result.getConnectedContent().entrySet().stream()
                    .limit(2)
                    .forEach(entry -> {
                        System.out.println("**" + entry.getKey() + "**:");
                        String preview = entry.getValue().length() > 200 ? 
                                entry.getValue().substring(0, 200) + "..." : entry.getValue();
                        System.out.println(preview + "\\n");
                    });
            
        } catch (Exception e) {
            System.out.println("Research workflow failed: " + e.getMessage());
        }
        
        System.out.println("---\\n");
    }
    
    
    private void demonstratePersonalizedContentGeneration() {
        System.out.println("## Demo 2: Personalized Content Generation\\n");
        
        String sessionId = "demo_session_" + System.currentTimeMillis();
        String topic = "Kubernetes Container Orchestration";
        String subtopic = "Pod Management";

        String[] expertiseLevels = {"beginner", "intermediate", "advanced"};
        
        for (String level : expertiseLevels) {
            System.out.println("### " + level.substring(0, 1).toUpperCase() + level.substring(1) + " Level Content:");
            
            PersonalizedMarkdownConfig config = createConfigForLevel(level);
            
            try {
                String content = researchService.generatePersonalizedContent(sessionId, topic, subtopic, config);

                String[] lines = content.split("\\n");
                String[] words = content.split("\\s+");
                
                System.out.println("- **Length**: " + words.length + " words, " + lines.length + " lines");
                System.out.println("- **Structure**: " + countHeadings(content) + " headings, " + countLists(content) + " lists");

                String preview = content.length() > 300 ? content.substring(0, 300) + "..." : content;
                System.out.println("- **Preview**: " + preview.replace("\\n", " ") + "\\n");
                
            } catch (Exception e) {
                System.out.println("Content generation failed for " + level + ": " + e.getMessage() + "\\n");
            }
        }
        
        System.out.println("---\\n");
    }
    
    
    private void demonstrateContentConnectivity() {
        System.out.println("## Demo 3: Content Connectivity Analysis\\n");
        
        String sessionId = "connectivity_session_" + System.currentTimeMillis();

        PersonalizedMarkdownConfig config = PersonalizedMarkdownConfig.forIntermediate(
                Set.of("technology", "software"),
                Map.of("programming", 6, "architecture", 5)
        );
        
        String[] topics = {"Microservices", "API Design", "Database Scaling", "Load Balancing"};
        
        System.out.println("### Generating connected content for analysis...");
        for (String topic : topics) {
            try {
                researchService.generatePersonalizedContent(sessionId, topic, "fundamentals", config);
                System.out.println("✓ Generated content for: " + topic);
            } catch (Exception e) {
                System.out.println("✗ Failed to generate content for: " + topic);
            }
        }

        System.out.println("\\n### Content Connectivity Analysis:");
        try {
            ContentAnalysis analysis = researchService.getContentAnalysis(sessionId);
            
            System.out.println("- **Total Content Pieces**: " + analysis.getTotalContent());
            System.out.println("- **Total Connections**: " + analysis.getTotalConnections());
            System.out.println("- **Average Connections per Content**: " + String.format("%.1f", analysis.getAverageConnections()));
            System.out.println("- **Most Connected Content**: " + analysis.getMostConnectedContent());

            System.out.println("\\n### Content Relationship Map:");
            Map<String, Set<String>> relationships = researchService.getContentRelationshipMap(sessionId);
            relationships.entrySet().stream()
                    .limit(3)
                    .forEach(entry -> {
                        String contentId = entry.getKey();
                        Set<String> connections = entry.getValue();
                        System.out.println("- **" + contentId + "** → " + connections.size() + " connections");
                    });
            
        } catch (Exception e) {
            System.out.println("Connectivity analysis failed: " + e.getMessage());
        }
        
        System.out.println("\\n---\\n");
    }
    
    
    private void demonstrateLearningPathGeneration() {
        System.out.println("## Demo 4: Learning Path Generation\\n");
        
        String sessionId = "learning_session_" + System.currentTimeMillis();
        String targetTopic = "Cloud Native Development";

        String[] levels = {"beginner", "advanced"};
        
        for (String level : levels) {
            System.out.println("### Learning Path for " + level.substring(0, 1).toUpperCase() + level.substring(1) + " Level:");
            
            PersonalizedMarkdownConfig config = createConfigForLevel(level);
            
            try {
                LearningPath path = researchService.generateLearningPath(sessionId, targetTopic, config);
                
                System.out.println("- **Target Topic**: " + path.getTargetTopic());
                System.out.println("- **Expertise Level**: " + path.getExpertiseLevel());
                System.out.println("- **Related Content**: " + path.getRelatedContent().size() + " items");
                System.out.println("- **Recommended Order**: " + String.join(" → ", path.getRecommendedOrder()));

                if (!path.getConnections().isEmpty()) {
                    System.out.println("- **Topic Connections**: " + path.getConnections().size() + " mapped");
                }
                
            } catch (Exception e) {
                System.out.println("Learning path generation failed for " + level + ": " + e.getMessage());
            }
            
            System.out.println();
        }
        
        System.out.println("---\\n");
    }
    
    
    private void demonstrateContentGapAnalysis() {
        System.out.println("## Demo 5: Content Gap Analysis\\n");
        
        String sessionId = "gap_analysis_session_" + System.currentTimeMillis();

        List<String> targetConcepts = Arrays.asList(
                "containerization", "orchestration", "service mesh", "observability",
                "security", "networking", "storage", "deployment", "monitoring", "logging"
        );
        
        System.out.println("### Target Concepts for Analysis:");
        System.out.println(String.join(", ", targetConcepts) + "\\n");
        
        try {
            ContentGapAnalysis gapAnalysis = researchService.analyzeContentGaps(sessionId, targetConcepts);
            
            System.out.println("### Gap Analysis Results:");
            System.out.println("- **Target Concepts**: " + gapAnalysis.getTargetConcepts().size());
            System.out.println("- **Identified Gaps**: " + gapAnalysis.getIdentifiedGaps().size());
            
            if (!gapAnalysis.getIdentifiedGaps().isEmpty()) {
                System.out.println("- **Missing Coverage**: " + String.join(", ", gapAnalysis.getIdentifiedGaps()));
            } else {
                System.out.println("- **Coverage Status**: All target concepts covered");
            }
            
            System.out.println("\\n### Recommendations:");
            gapAnalysis.getRecommendations().forEach(rec -> 
                    System.out.println("- " + rec));
            
            if (gapAnalysis.getCurrentAnalysis() != null) {
                ContentAnalysis current = gapAnalysis.getCurrentAnalysis();
                System.out.println("\\n### Current Content State:");
                System.out.println("- **Total Content**: " + current.getTotalContent());
                System.out.println("- **Connectivity Score**: " + String.format("%.1f", current.getAverageConnections()));
            }
            
        } catch (Exception e) {
            System.out.println("Gap analysis failed: " + e.getMessage());
        }
        
        System.out.println("\\n---\\n");
    }

    
    private CitationService createMockCitationService() {

        try {
            return new CitationService(null, null); // Will work with null config for demo
        } catch (Exception e) {
            logger.warning("Could not create CitationService, research may have limited functionality");
            return null;
        }
    }
    
    private PersonalizedMarkdownConfig createConfigForLevel(String level) {
        Set<String> interests = Set.of("technology", "software development", "best practices");
        Map<String, Integer> knowledge = Map.of("programming", 5, "architecture", 4, "devops", 3);
        
        return switch (level) {
            case "beginner" -> PersonalizedMarkdownConfig.forBeginner(interests);
            case "intermediate" -> PersonalizedMarkdownConfig.forIntermediate(interests, knowledge);
            case "advanced" -> PersonalizedMarkdownConfig.forAdvanced(interests, knowledge);
            default -> PersonalizedMarkdownConfig.forIntermediate(interests, knowledge);
        };
    }
    
    private int countHeadings(String content) {
        return (int) content.lines().filter(line -> line.trim().startsWith("#")).count();
    }
    
    private int countLists(String content) {
        return (int) content.lines().filter(line -> line.trim().matches("^[\\-\\*\\+]\\s+.*")).count();
    }

    
    private static class MockLLMClient implements LLMClient {
        @Override
        public <T> LLMResponse<T> complete(String prompt, Class<T> responseType) {
            String mockResponse = generateMockContent(prompt);
            @SuppressWarnings("unchecked")
            T typedResponse = (T) mockResponse;
            return new LLMResponse<>(mockResponse, typedResponse);
        }
        
        private String generateMockContent(String prompt) {

            if (prompt.toLowerCase().contains("kubernetes")) {
                return "# Kubernetes Container Orchestration\\n\\n" +
                       "## Overview\\nKubernetes is a container orchestration platform that automates deployment, scaling, and management.\\n\\n" +
                       "## Key Features\\n- **Pod Management**: Smallest deployable units\\n- **Service Discovery**: Automatic network configuration\\n" +
                       "- **Load Balancing**: Traffic distribution across pods\\n\\n" +
                       "## Best Practices\\n- Use resource limits and requests\\n- Implement health checks\\n- Follow security guidelines";
            }
            
            if (prompt.toLowerCase().contains("microservices")) {
                return "# Microservices Architecture\\n\\n" +
                       "## Fundamentals\\nMicroservices break down applications into smaller, independent services.\\n\\n" +
                       "## Benefits\\n- **Scalability**: Independent scaling\\n- **Technology Diversity**: Different tech stacks\\n" +
                       "- **Team Autonomy**: Separate development teams\\n\\n" +
                       "## Challenges\\n- Distributed system complexity\\n- Network communication overhead\\n- Data consistency issues";
            }
            
            return "# Research Analysis\\n\\n## Summary\\nComprehensive analysis of the requested topic with detailed insights and practical recommendations.\\n\\n" +
                   "## Key Points\\n- Industry best practices and standards\\n- Technical implementation details\\n- Real-world applications and case studies\\n\\n" +
                   "## Recommendations\\n- Follow established methodologies\\n- Consider scalability requirements\\n- Implement monitoring and observability";
        }
    }
}
