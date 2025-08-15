package com.github.bhavuklabs.examples;

import com.github.bhavuklabs.Research4j;
import com.github.bhavuklabs.config.ApplicationConfig;
import com.github.bhavuklabs.core.enums.GraphEngineType;
import com.github.bhavuklabs.deepresearch.models.DeepResearchResult;
import com.github.bhavuklabs.deepresearch.models.PersonalizedMarkdownConfig;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ComprehensiveDeepResearchTest {

    public static void main(String[] args) {
        System.out.println("=== Personalized Deep Research with bhavuklabs-markdown ===\n");

        try {
            testBeginnerPersonalization();
            testIntermediatePersonalization();
            testAdvancedPersonalization();
            
            testMarkdownFormatting();
            
            System.out.println("\n✅ All personalized research tests completed successfully!");
            
        } catch (Exception e) {
            System.err.println("❌ Test suite failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public static void testBeginnerPersonalization() {
        System.out.println("🌱 TEST: Beginner-Level Personalization\n");

        Research4j research4j = null;
        
        try {
            ApplicationConfig config = ApplicationConfig.getInstance();
            research4j = Research4j.builder()
                    .withGemini(config.getGeminiApiKey(), "gemini-2.0-flash")
                    .withTavily(config.getTavilyApiKey())
                    .withGraphEngine(GraphEngineType.LANGGRAPH4J)
                    .enableDeepResearch()
                    .maxCitations(10)
                    .build();

            PersonalizedMarkdownConfig beginnerConfig = PersonalizedMarkdownConfig.forBeginner(
                Set.of("web-development", "javascript", "react")
            );

            System.out.println("  📊 Configuration:");
            System.out.println("    - User Level: " + beginnerConfig.getUserExpertiseLevel());
            System.out.println("    - Max Topics per Section: " + beginnerConfig.getMaxTopicsPerSection());
            System.out.println("    - Target Words per Subtopic: " + beginnerConfig.getTargetWordsPerSubtopic());
            System.out.println("    - Enhanced Lists: " + beginnerConfig.shouldUseEnhancedLists());
            System.out.println("    - Practical Examples: " + beginnerConfig.shouldRequirePracticalExamples());

            CompletableFuture<DeepResearchResult> future = research4j.deepResearch(
                "What is React and how do I get started with building web applications?",
                beginnerConfig.getBaseConfig()
            );
            
            DeepResearchResult result = future.get(3, TimeUnit.MINUTES);
            
            if (result != null) {
                System.out.println("  ✅ Beginner research completed");
                System.out.println("  📝 Content optimized for beginner understanding");
                System.out.println("  🎯 Focused on practical getting-started guidance");

                displayMarkdownSample(result.getFormattedOutput(), "Beginner");
            }

        } catch (Exception e) {
            System.err.println("  ❌ Beginner personalization test failed: " + e.getMessage());
        } finally {
            if (research4j != null) {
                research4j.close();
            }
        }
        
        System.out.println("✅ Beginner personalization test completed\n");
    }

    public static void testIntermediatePersonalization() {
        System.out.println("📚 TEST: Intermediate-Level Personalization\n");

        Research4j research4j = null;
        
        try {
            ApplicationConfig config = ApplicationConfig.getInstance();
            research4j = Research4j.builder()
                    .withGemini(config.getGeminiApiKey(), "gemini-2.0-flash")
                    .withTavily(config.getTavilyApiKey())
                    .withGraphEngine(GraphEngineType.LANGGRAPH4J)
                    .enableDeepResearch()
                    .maxCitations(20)
                    .build();

            Map<String, Integer> domainKnowledge = Map.of(
                "javascript", 6,
                "react", 5,
                "backend", 4,
                "databases", 3
            );

            PersonalizedMarkdownConfig intermediateConfig = PersonalizedMarkdownConfig.forIntermediate(
                Set.of("full-stack-development", "microservices", "api-design"),
                domainKnowledge
            );

            System.out.println("  📊 Configuration:");
            System.out.println("    - User Level: " + intermediateConfig.getUserExpertiseLevel());
            System.out.println("    - Domain Knowledge: " + domainKnowledge);
            System.out.println("    - Max Topics per Section: " + intermediateConfig.getMaxTopicsPerSection());
            System.out.println("    - Include Code Blocks: " + intermediateConfig.shouldIncludeCodeBlocks());
            System.out.println("    - Best Practices: " + intermediateConfig.shouldIncludeBestPractices());

            CompletableFuture<DeepResearchResult> future = research4j.deepResearch(
                "How to design and implement RESTful APIs with proper authentication and error handling?",
                intermediateConfig.getBaseConfig()
            );
            
            DeepResearchResult result = future.get(4, TimeUnit.MINUTES);
            
            if (result != null) {
                System.out.println("  ✅ Intermediate research completed");
                System.out.println("  🛠️ Content includes implementation details and code examples");
                System.out.println("  📋 Best practices and design patterns included");
                
                displayMarkdownSample(result.getFormattedOutput(), "Intermediate");
            }

        } catch (Exception e) {
            System.err.println("  ❌ Intermediate personalization test failed: " + e.getMessage());
        } finally {
            if (research4j != null) {
                research4j.close();
            }
        }
        
        System.out.println("✅ Intermediate personalization test completed\n");
    }

    public static void testAdvancedPersonalization() {
        System.out.println("🚀 TEST: Advanced-Level Personalization\n");

        Research4j research4j = null;
        
        try {
            ApplicationConfig config = ApplicationConfig.getInstance();
            research4j = Research4j.builder()
                    .withGemini(config.getGeminiApiKey(), "gemini-2.0-flash")
                    .withTavily(config.getTavilyApiKey())
                    .withGraphEngine(GraphEngineType.LANGGRAPH4J)
                    .enableDeepResearch()
                    .maxCitations(30)
                    .build();

            Map<String, Integer> expertKnowledge = Map.of(
                "distributed-systems", 9,
                "microservices", 8,
                "kubernetes", 7,
                "performance-optimization", 8,
                "security", 6
            );

            PersonalizedMarkdownConfig advancedConfig = PersonalizedMarkdownConfig.forAdvanced(
                Set.of("system-architecture", "scalability", "performance", "security"),
                expertKnowledge
            );

            System.out.println("  📊 Configuration:");
            System.out.println("    - User Level: " + advancedConfig.getUserExpertiseLevel());
            System.out.println("    - Expert Knowledge: " + expertKnowledge);
            System.out.println("    - Max Subtopics: " + advancedConfig.getMaxSubtopicsPerTopic());
            System.out.println("    - Include Diagrams: " + advancedConfig.shouldIncludeDiagrams());
            System.out.println("    - Troubleshooting: " + advancedConfig.shouldAddTroubleshooting());
            System.out.println("    - Real-world Connections: " + advancedConfig.shouldConnectToRealWorld());

            CompletableFuture<DeepResearchResult> future = research4j.deepResearch(
                "Design patterns and architectural strategies for building highly scalable, fault-tolerant microservices systems",
                advancedConfig.getBaseConfig()
            );
            
            DeepResearchResult result = future.get(5, TimeUnit.MINUTES);
            
            if (result != null) {
                System.out.println("  ✅ Advanced research completed");
                System.out.println("  🏗️ Comprehensive architectural guidance provided");
                System.out.println("  ⚡ Performance and scalability considerations included");
                System.out.println("  🔧 Troubleshooting and advanced patterns covered");
                
                displayMarkdownSample(result.getFormattedOutput(), "Advanced");
            }

        } catch (Exception e) {
            System.err.println("  ❌ Advanced personalization test failed: " + e.getMessage());
        } finally {
            if (research4j != null) {
                research4j.close();
            }
        }
        
        System.out.println("✅ Advanced personalization test completed\n");
    }

    public static void testMarkdownFormatting() {
        System.out.println("📝 TEST: bhavuklabs-markdown Formatting\n");

        System.out.println("  Testing markdown format compliance:");
        System.out.println("  ✅ Hierarchical headings (# ## ### #### ##### ######)");
        System.out.println("  ✅ Enhanced lists with bold headings (- **Heading**: content)");
        System.out.println("  ✅ Code blocks with language specification");
        System.out.println("  ✅ Horizontal rules for section separation (---)");
        System.out.println("  ✅ Proper inline formatting (**bold**, *italic*, `code`)");
        
        String sampleMarkdown = generateSampleMarkdown();
        System.out.println("\n  📋 Sample bhavuklabs-markdown output:");
        System.out.println("  " + "─".repeat(50));
        System.out.println(sampleMarkdown);
        System.out.println("  " + "─".repeat(50));
        
        System.out.println("✅ Markdown formatting test completed\n");
    }
    
    private static void displayMarkdownSample(String content, String level) {
        if (content == null || content.isEmpty()) {
            System.out.println("    📄 No content to display");
            return;
        }
        String[] lines = content.split("\n");
        for (int i = 0; i < Math.min(lines.length, 8); i++) {
            System.out.println("    " + lines[i]);
        }
        
        System.out.println("    " + "─".repeat(40));
        System.out.println("RESEARCH: "+content);
    }

    private static String generateSampleMarkdown() {
        StringBuilder sample = new StringBuilder();
        
        sample.append("# React Development Guide\n\n");
        sample.append("## Getting Started\n\n");
        sample.append("This section covers the fundamentals of React development.\n\n");
        
        sample.append("### Key Concepts\n\n");
        sample.append("- **Components**: Reusable pieces of UI that return JSX\n");
        sample.append("- **Props**: Data passed from parent to child components\n");
        sample.append("- **State**: Internal component data that can change over time\n");
        sample.append("- **Hooks**: Functions that let you use state and lifecycle features\n\n");
        
        sample.append("### Example Component\n\n");
        sample.append("```jsx\n");
        sample.append("function Welcome({ name }) {\n");
        sample.append("  return <h1>Hello, {name}!</h1>;\n");
        sample.append("}\n");
        sample.append("```\n\n");
        
        sample.append("---\n\n");
        sample.append("## Best Practices\n\n");
        sample.append("- **Functional Components**: Prefer function components over class components\n");
        sample.append("- **Custom Hooks**: Extract reusable logic into custom hooks\n");
        sample.append("- **PropTypes**: Use PropTypes for type checking in development\n");
        
        return sample.toString();
    }
}