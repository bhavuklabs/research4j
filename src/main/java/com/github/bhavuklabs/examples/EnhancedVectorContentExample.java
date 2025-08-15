package com.github.bhavuklabs.examples;

import com.github.bhavuklabs.builders.ConnectedContentBuilder;
import com.github.bhavuklabs.services.ContentVectorizer;
import com.github.bhavuklabs.services.ContentVectorizer.RelatedContent;
import com.github.bhavuklabs.services.impl.SimpleEmbeddingService;
import com.github.bhavuklabs.vector.VectorStore;
import com.github.bhavuklabs.deepresearch.models.PersonalizedMarkdownConfig;
import com.github.bhavuklabs.core.contracts.LLMClient;
import com.github.bhavuklabs.core.payloads.LLMResponse;
import com.github.bhavuklabs.exceptions.client.LLMClientException;

import java.util.*;
import java.util.logging.Logger;


public class EnhancedVectorContentExample {
    
    private static final Logger logger = Logger.getLogger(EnhancedVectorContentExample.class.getName());
    
    private final VectorStore vectorStore;
    private final ContentVectorizer vectorizer;
    private final ConnectedContentBuilder contentBuilder;
    private final String sessionId;
    
    public EnhancedVectorContentExample() {

        this.vectorStore = new VectorStore(384);
        this.vectorizer = new ContentVectorizer(vectorStore, new SimpleEmbeddingService());
        this.sessionId = "enhanced_session_" + System.currentTimeMillis();

        LLMClient mockLLMClient = new MockLLMClient();
        this.contentBuilder = new ConnectedContentBuilder(mockLLMClient, vectorizer, sessionId);
        
        logger.info("EnhancedVectorContentExample initialized with session: " + sessionId);
    }
    
    public void demonstrateConnectedContent() {
        System.out.println("=== Enhanced Vector-Based Connected Content Generation ===\n");

        PersonalizedMarkdownConfig config = PersonalizedMarkdownConfig.builder()
                .userExpertiseLevel("intermediate")
                .domainKnowledge(Map.of("computer_science", 7, "programming", 8))
                .maxTopicsPerSection(3)
                .targetWordsPerSubtopic(200)
                .includeExamples(true)
                .enableProgressiveDisclosure(true)
                .build();

        String[] topics = {
            "Data Structures|Array Operations",
            "Data Structures|Linked Lists", 
            "Algorithms|Sorting Algorithms",
            "Algorithms|Search Algorithms",
            "Performance|Time Complexity",
            "Performance|Space Optimization"
        };
        
        Map<String, String> generatedContent = new HashMap<>();

        for (String topicPair : topics) {
            String[] parts = topicPair.split("\\|");
            String topic = parts[0];
            String subtopic = parts[1];
            
            System.out.println("Generating content for: " + topic + " - " + subtopic);
            
            String content = contentBuilder.buildConnectedContent(topic, subtopic, config);
            generatedContent.put(topic + "_" + subtopic, content);
            
            System.out.println("Generated " + content.length() + " characters");

            String sample = content.length() > 200 ? content.substring(0, 200) + "..." : content;
            System.out.println("Sample: " + sample + "\n");
        }

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        analyzeContentConnections();

        demonstrateSemanticSearch();

        demonstrateLearningPaths();
        
        System.out.println("=== Enhanced Demo Complete ===");
    }
    
    private void analyzeContentConnections() {
        System.out.println("=== Content Connectivity Analysis ===");
        
        ContentVectorizer.ContentAnalysis analysis = vectorizer.analyzeContent(sessionId);
        System.out.println("Total Content Pieces: " + analysis.getTotalContent());
        System.out.println("Total Connections: " + analysis.getTotalConnections());
        System.out.printf("Average Connections per Content: %.1f%n", analysis.getAverageConnections());
        System.out.println("Most Connected Content: " + analysis.getMostConnectedContent());

        Map<String, Set<String>> connections = vectorizer.getContentConnections(sessionId);
        System.out.println("\nDetailed Connection Map:");
        for (Map.Entry<String, Set<String>> entry : connections.entrySet()) {
            System.out.println("  " + entry.getKey() + " -> " + entry.getValue().size() + " connections");
            for (String connection : entry.getValue()) {
                System.out.println("    ├─ " + connection);
            }
        }
        System.out.println();
    }
    
    private void demonstrateSemanticSearch() {
        System.out.println("=== Semantic Content Discovery ===");
        
        String[] searchQueries = {
            "array data structure operations",
            "sorting algorithm efficiency", 
            "time complexity analysis",
            "memory optimization techniques",
            "search performance comparison"
        };
        
        for (String query : searchQueries) {
            System.out.println("Search Query: '" + query + "'");
            
            List<RelatedContent> results = vectorizer.findAllRelatedContent(query, 3, "");
            
            if (results.isEmpty()) {
                System.out.println("  No related content found\n");
            } else {
                for (RelatedContent content : results) {
                    System.out.printf("  ├─ %s (similarity: %.3f)%n", 
                                    content.getTopic(), content.getSimilarity());
                    System.out.printf("     %s%n", 
                                    content.getContent().substring(0, Math.min(100, content.getContent().length())) + "...");
                }
                System.out.println();
            }
        }
    }
    
    private void demonstrateLearningPaths() {
        System.out.println("=== Learning Path Generation ===");

        String[] concepts = {
            "basic data structures",
            "algorithm analysis", 
            "advanced algorithms",
            "performance optimization",
            "distributed systems"
        };
        
        List<String> gaps = vectorizer.suggestContentGaps(sessionId, Arrays.asList(concepts));
        System.out.println("Content Gaps Analysis:");
        if (gaps.isEmpty()) {
            System.out.println("  All concepts are well covered!");
        } else {
            System.out.println("  Missing coverage for:");
            for (String gap : gaps) {
                System.out.println("    ├─ " + gap);
            }
        }

        Map<String, String> sessionContent = contentBuilder.getSessionContent();
        System.out.println("\nGenerated Session Content:");
        for (Map.Entry<String, String> entry : sessionContent.entrySet()) {
            System.out.println("  ├─ " + entry.getKey() + " (" + entry.getValue().length() + " chars)");
        }
        System.out.println();
    }
    
    
    private static class MockLLMClient implements LLMClient {
        
        @Override
        public <T> LLMResponse<T> complete(String prompt, Class<T> type) throws LLMClientException {

            String topic = extractTopic(prompt);
            String content = generateMockContent(topic);

            @SuppressWarnings("unchecked")
            T structuredOutput = (T) content;
            
            return new LLMResponse<>(content, structuredOutput);
        }
        
        private String extractTopic(String prompt) {
            String lowerPrompt = prompt.toLowerCase();
            if (lowerPrompt.contains("array")) return "arrays";
            if (lowerPrompt.contains("linked")) return "linked_lists";
            if (lowerPrompt.contains("sort")) return "sorting";
            if (lowerPrompt.contains("search")) return "searching"; 
            if (lowerPrompt.contains("time complexity")) return "complexity";
            if (lowerPrompt.contains("optimization")) return "optimization";
            return "general";
        }
        
        private String generateMockContent(String topic) {
            StringBuilder content = new StringBuilder();
            
            switch (topic) {
                case "arrays":
                    content.append("# Array Data Structures\n\n")
                           .append("## Overview\n")
                           .append("Arrays are fundamental data structures that store elements in contiguous memory locations. ")
                           .append("They provide efficient random access with O(1) time complexity for element retrieval. ")
                           .append("Array operations include insertion, deletion, searching, and traversal.\n\n")
                           .append("## Key Operations\n")
                           .append("- **Access**: Direct indexing allows O(1) access time\n")
                           .append("- **Insertion**: Adding elements at specific positions\n")
                           .append("- **Deletion**: Removing elements and shifting remaining ones\n")
                           .append("- **Search**: Linear or binary search depending on array state\n\n")
                           .append("## Performance Characteristics\n")
                           .append("Arrays excel at random access but have limitations with dynamic sizing. ")
                           .append("Understanding array performance is crucial for algorithm design and optimization.");
                    break;
                    
                case "linked_lists":
                    content.append("# Linked List Data Structures\n\n")
                           .append("## Structure\n")
                           .append("Linked lists consist of nodes containing data and references to the next node. ")
                           .append("Unlike arrays, linked lists provide dynamic memory allocation and efficient insertion/deletion. ")
                           .append("They sacrifice random access for flexibility in size management.\n\n")
                           .append("## Types and Operations\n")
                           .append("- **Singly Linked**: Each node points to the next\n")
                           .append("- **Doubly Linked**: Nodes have both next and previous pointers\n")
                           .append("- **Circular**: Last node connects back to the first\n\n")
                           .append("## Memory Considerations\n")
                           .append("Linked lists use non-contiguous memory, affecting cache performance. ")
                           .append("However, they excel at dynamic data management and memory efficiency.");
                    break;
                    
                case "sorting":
                    content.append("# Sorting Algorithms\n\n")
                           .append("## Algorithm Categories\n")
                           .append("Sorting algorithms organize data in specific order for efficient access and processing. ")
                           .append("Different algorithms offer various trade-offs between time complexity, space usage, and stability. ")
                           .append("Understanding these trade-offs is essential for algorithm selection.\n\n")
                           .append("## Common Algorithms\n")
                           .append("- **Bubble Sort**: Simple but inefficient O(n²) algorithm\n")
                           .append("- **Quick Sort**: Efficient divide-and-conquer with O(n log n) average case\n")
                           .append("- **Merge Sort**: Stable O(n log n) algorithm with predictable performance\n")
                           .append("- **Heap Sort**: In-place O(n log n) using heap data structure\n\n")
                           .append("## Selection Criteria\n")
                           .append("Algorithm choice depends on data size, memory constraints, stability requirements, and performance needs.");
                    break;
                    
                case "searching":
                    content.append("# Search Algorithms\n\n")
                           .append("## Search Strategies\n")
                           .append("Search algorithms locate specific elements within data structures efficiently. ")
                           .append("The choice of search algorithm depends on data organization, size, and access patterns. ")
                           .append("Proper search strategy selection significantly impacts application performance.\n\n")
                           .append("## Algorithm Types\n")
                           .append("- **Linear Search**: Sequential checking with O(n) complexity\n")
                           .append("- **Binary Search**: Divide-and-conquer on sorted data with O(log n)\n")
                           .append("- **Hash-based Search**: O(1) average case using hash tables\n")
                           .append("- **Tree Search**: Hierarchical searching in tree structures\n\n")
                           .append("## Optimization Techniques\n")
                           .append("Search performance can be enhanced through indexing, caching, and preprocessing strategies.");
                    break;
                    
                case "complexity":
                    content.append("# Time Complexity Analysis\n\n")
                           .append("## Complexity Theory\n")
                           .append("Time complexity describes how algorithm execution time grows with input size. ")
                           .append("Understanding complexity helps predict performance and compare algorithms effectively. ")
                           .append("Big O notation provides standardized complexity measurement.\n\n")
                           .append("## Common Complexities\n")
                           .append("- **O(1)**: Constant time for direct access operations\n")
                           .append("- **O(log n)**: Logarithmic time for divide-and-conquer algorithms\n")
                           .append("- **O(n)**: Linear time for single-pass operations\n")
                           .append("- **O(n log n)**: Efficient sorting and searching algorithms\n")
                           .append("- **O(n²)**: Quadratic time for nested loop operations\n\n")
                           .append("## Analysis Techniques\n")
                           .append("Complexity analysis involves identifying loops, recursive calls, and dominant operations in algorithms.");
                    break;
                    
                case "optimization":
                    content.append("# Performance Optimization\n\n")
                           .append("## Optimization Principles\n")
                           .append("Performance optimization focuses on improving algorithm efficiency and resource utilization. ")
                           .append("Successful optimization requires profiling, bottleneck identification, and systematic improvement. ")
                           .append("Memory management and algorithmic efficiency are key optimization areas.\n\n")
                           .append("## Optimization Strategies\n")
                           .append("- **Caching**: Store frequently accessed data for faster retrieval\n")
                           .append("- **Memoization**: Cache function results to avoid redundant calculations\n")
                           .append("- **Data Structure Selection**: Choose optimal structures for specific use cases\n")
                           .append("- **Algorithm Refinement**: Replace inefficient algorithms with better alternatives\n\n")
                           .append("## Measurement and Analysis\n")
                           .append("Performance optimization requires careful measurement, profiling, and validation of improvements.");
                    break;
                    
                default:
                    content.append("# General Computer Science Concepts\n\n")
                           .append("## Fundamental Principles\n")
                           .append("Computer science encompasses algorithms, data structures, and computational theory. ")
                           .append("Understanding these fundamentals enables effective problem-solving and system design. ")
                           .append("Practical application requires balancing theoretical knowledge with implementation skills.\n\n")
                           .append("## Core Areas\n")
                           .append("- **Data Structures**: Organizing and storing data efficiently\n")
                           .append("- **Algorithms**: Step-by-step problem-solving procedures\n")
                           .append("- **Complexity Analysis**: Understanding performance characteristics\n")
                           .append("- **System Design**: Building scalable and maintainable systems\n\n")
                           .append("## Learning Path\n")
                           .append("Mastery requires progressive learning from basic concepts to advanced applications.");
            }
            
            return content.toString();
        }
    }
    
    public static void main(String[] args) {
        EnhancedVectorContentExample example = new EnhancedVectorContentExample();
        example.demonstrateConnectedContent();
    }
}
