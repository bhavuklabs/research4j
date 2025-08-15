package com.github.bhavuklabs.examples;

import com.github.bhavuklabs.Research4j;
import com.github.bhavuklabs.agent.ResearchResult;
import com.github.bhavuklabs.core.enums.GraphEngineType;
import com.github.bhavuklabs.core.enums.OutputFormat;
import com.github.bhavuklabs.config.ApplicationConfig;

public class FixedDeepResearchExample {

    public static void main(String[] args) {
        System.out.println("=== Research4j LangGraph Engine Test ===\n");

        try {
            testLangGraphEngine();
        } catch (Exception e) {
            System.err.println("Test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void testLangGraphEngine() {
        Research4j research4j = null;
        
        try {
            System.out.println("🔧 Attempting to initialize LangGraph4j engine...");
            
            try {
                Class.forName("org.bsc.langgraph4j.StateGraph");
                System.out.println("✅ LangGraph4j dependency found");
            } catch (ClassNotFoundException e) {
                System.err.println("❌ LangGraph4j dependency not found in classpath!");
                System.err.println("   Check if langgraph4j-core is properly included in dependencies");
                return;
            }
            
            ApplicationConfig config = ApplicationConfig.getInstance();
            research4j = Research4j.builder()
                    .withGemini(config.getGeminiApiKey(), "gemini-2.0-flash")
                    .withTavily(config.getTavilyApiKey())
                    .withGraphEngine(GraphEngineType.LANGGRAPH4J)
                    .maxCitations(50)
                    .defaultOutputFormat(OutputFormat.MARKDOWN)
                    .enableDeepResearch()
                    .build();

            System.out.println("✅ Research4j instance created");
            System.out.println("📊 Actual Engine Type: " + research4j.getGraphEngineType());
            
            if (!research4j.getGraphEngineType().toString().equals("LANGGRAPH4J")) {
                System.out.println("⚠️ CRITICAL: LangGraph4j initialization failed!");
                System.out.println("   Requested: LANGGRAPH4J");
                System.out.println("   Got: " + research4j.getGraphEngineType());
                System.out.println("   Check the logs above for the actual error causing the fallback");
                return;
            }

            System.out.println("🔍 Running research query...");
            
            ResearchResult result = research4j.research("What are the key features of Spring Boot?");

            System.out.println("\n=== LangGraph Engine Test Results ===");
            System.out.println("Question: What are the key features of Spring Boot?");
            System.out.println("Engine Type: " + research4j.getGraphEngineType());

            if (!research4j.getGraphEngineType().toString().equals("LANGGRAPH4J")) {
                System.out.println("⚠️ WARNING: Expected LANGGRAPH4J but got " + research4j.getGraphEngineType());
                System.out.println("   This indicates LangGraph initialization failed and system fell back to legacy");
            }
            
            if (result != null && result.getAnswer() != null && !result.getAnswer().isEmpty()) {
                System.out.println("Result: " + result.getAnswer().substring(0, Math.min(200, result.getAnswer().length())) + "...");
                System.out.println("✅ Success: Research completed with " + research4j.getGraphEngineType() + " engine");
            } else {
                System.out.println("❌ Result: No answer generated");
                System.out.println("   This suggests the pipeline failed or got stuck in a loop");
            }

        } catch (Exception e) {
            System.err.println("❌ Error testing LangGraph engine: " + e.getMessage());
            System.err.println("   This could indicate:");
            System.err.println("   1. LangGraph4j dependency missing or incompatible");
            System.err.println("   2. Runtime initialization error in LangGraphExecutor");
            System.err.println("   3. Configuration issue with the graph setup");
            e.printStackTrace();
        } finally {
            if (research4j != null) {
                research4j.close();
            }
        }
    }
}