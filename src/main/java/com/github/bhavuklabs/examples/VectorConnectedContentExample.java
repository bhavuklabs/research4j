package com.github.bhavuklabs.examples;

import com.github.bhavuklabs.vector.VectorStore;
import com.github.bhavuklabs.services.ContentVectorizer;
import com.github.bhavuklabs.services.impl.SimpleEmbeddingService;
import com.github.bhavuklabs.builders.ConnectedContentBuilder;
import com.github.bhavuklabs.deepresearch.models.PersonalizedMarkdownConfig;
import com.github.bhavuklabs.core.contracts.LLMClient;
import com.github.bhavuklabs.core.payloads.LLMResponse;

import java.util.*;
import java.util.logging.Logger;


public class VectorConnectedContentExample {
    
    private static final Logger logger = Logger.getLogger(VectorConnectedContentExample.class.getName());
    
    private final VectorStore vectorStore;
    private final ContentVectorizer contentVectorizer;
    private final ConnectedContentBuilder contentBuilder;
    private final String sessionId;
    
    public VectorConnectedContentExample() {

        this.vectorStore = new VectorStore(384); // 384-dimensional embeddings
        SimpleEmbeddingService embeddingService = new SimpleEmbeddingService();
        this.contentVectorizer = new ContentVectorizer(vectorStore, embeddingService);

        this.sessionId = "connected_session_" + System.currentTimeMillis();

        MockLLMClient mockLLM = new MockLLMClient();
        this.contentBuilder = new ConnectedContentBuilder(mockLLM, contentVectorizer, sessionId);
        
        logger.info("VectorConnectedContentExample initialized with session: " + sessionId);
    }
    
    
    public void demonstrateConnectedContent() {
        System.out.println("=== Vector-Based Connected Content Generation Demo ===\\n");

        PersonalizedMarkdownConfig config = createIntermediateConfig();

        String[] topics = {
            "Data Structures",
            "Algorithms", 
            "Database Design",
            "System Architecture",
            "Performance Optimization"
        };
        
        String[] subtopics = {
            "Array and List Operations",
            "Sorting and Searching",
            "Relational vs NoSQL",
            "Microservices Patterns",
            "Caching Strategies"
        };

        for (int i = 0; i < topics.length; i++) {
            System.out.println("Generating content for: " + topics[i] + " - " + subtopics[i]);
            
            String connectedContent = contentBuilder.buildConnectedContent(
                topics[i], subtopics[i], config);
            
            System.out.println("Generated " + connectedContent.length() + " characters");
            System.out.println("Sample content:");
            System.out.println(connectedContent.substring(0, Math.min(200, connectedContent.length())) + "...\\n");
        }

        analyzeContentConnectivity();

        demonstrateContentDiscovery();

        demonstrateLearningPath();
    }
    
    
    private void analyzeContentConnectivity() {
        System.out.println("=== Content Connectivity Analysis ===");
        
        ContentVectorizer.ContentAnalysis analysis = contentBuilder.getContentAnalysis();
        
        System.out.println("Total Content Pieces: " + analysis.getTotalContent());
        System.out.println("Total Connections: " + analysis.getTotalConnections());
        System.out.println("Average Connections per Content: " + String.format("%.1f", analysis.getAverageConnections()));
        System.out.println("Most Connected Content: " + analysis.getMostConnectedContent());

        Map<String, Set<String>> connections = contentVectorizer.getContentConnections(sessionId);
        System.out.println("\\nContent Relationship Map:");
        connections.forEach((contentId, relatedIds) -> {
            System.out.println("  " + contentId + " -> " + relatedIds.size() + " connections");
            relatedIds.forEach(relatedId -> System.out.println("    ├─ " + relatedId));
        });
        
        System.out.println();
    }
    
    
    private void demonstrateContentDiscovery() {
        System.out.println("=== Semantic Content Discovery ===");
        
        String[] searchQueries = {
            "data storage and retrieval",
            "algorithm efficiency and optimization", 
            "system scalability patterns",
            "performance bottlenecks"
        };
        
        for (String query : searchQueries) {
            System.out.println("Search Query: '" + query + "'");
            
            List<ContentVectorizer.RelatedContent> relatedContent = 
                contentVectorizer.findRelatedContent(query, 3, sessionId);
            
            if (relatedContent.isEmpty()) {
                System.out.println("  No related content found");
            } else {
                relatedContent.forEach(content -> {
                    System.out.printf("  ├─ %s (%.2f similarity)\\n", 
                                    content.getTopic(), content.getSimilarity());
                    System.out.println("     " + content.getContent().substring(0, 
                                     Math.min(100, content.getContent().length())) + "...");
                });
            }
            System.out.println();
        }
    }
    
    
    private void demonstrateLearningPath() {
        System.out.println("=== Learning Path Generation ===");

        List<String> targetConcepts = Arrays.asList(
            "machine learning fundamentals",
            "distributed systems",
            "security best practices",
            "testing strategies",
            "deployment patterns"
        );
        
        List<String> contentGaps = contentVectorizer.suggestContentGaps(sessionId, targetConcepts);
        
        System.out.println("Content Gaps Analysis:");
        if (contentGaps.isEmpty()) {
            System.out.println("  All target concepts are covered!");
        } else {
            System.out.println("  Missing coverage for:");
            contentGaps.forEach(gap -> System.out.println("    ├─ " + gap));
        }
        
        System.out.println("\\nGenerated Session Content:");
        Map<String, String> sessionContent = contentBuilder.getSessionContent();
        sessionContent.forEach((contentId, content) -> {
            System.out.println("  ├─ " + contentId + " (" + content.length() + " chars)");
        });
        
        System.out.println();
    }
    
    
    private PersonalizedMarkdownConfig createIntermediateConfig() {
        Map<String, Integer> domainKnowledge = new HashMap<>();
        domainKnowledge.put("programming", 6);
        domainKnowledge.put("algorithms", 5);
        domainKnowledge.put("databases", 4);
        domainKnowledge.put("architecture", 3);
        
        Set<String> interests = new HashSet<>(Arrays.asList(
            "software engineering", "system design", "performance optimization"
        ));
        
        List<String> learningStyle = Arrays.asList("example-driven", "textual");
        
        return PersonalizedMarkdownConfig.builder()
                .userExpertiseLevel("intermediate")
                .userInterests(interests)
                .domainKnowledge(domainKnowledge)
                .preferredLearningStyle(learningStyle)
                .maxTopicsPerSection(4)
                .maxSubtopicsPerTopic(3)
                .targetWordsPerSubtopic(200)
                .enableHierarchicalBreakdown(true)
                .includeExamples(true)
                .enableProgressiveDisclosure(true)
                .useEnhancedLists(true)
                .includeCodeBlocks(true)
                .maxHeadingDepth(4)
                .build();
    }
    
    
    private static class MockLLMClient implements LLMClient {
        
        @Override
        public <T> LLMResponse<T> complete(String prompt, Class<T> type) {

            String mockContent = generateMockContent(prompt);
            
            @SuppressWarnings("unchecked")
            T result = (T) mockContent;
            
            return new LLMResponse<T>(mockContent, result);
        }
        
        private String generateMockContent(String prompt) {
            if (prompt.contains("Data Structures")) {
                return """
                # Data Structures: Array and List Operations
                
                ## Overview
                Data structures are fundamental building blocks in computer science that organize and store data efficiently.
                
                ### Arrays
                - **Fixed Size**: Arrays have a predetermined size that cannot be changed
                - **Contiguous Memory**: Elements are stored in adjacent memory locations
                - **Index Access**: O(1) time complexity for accessing elements by index
                
                ### Lists
                - **Dynamic Size**: Lists can grow and shrink during runtime
                - **Memory Management**: Automatic allocation and deallocation
                - **Flexible Operations**: Insert, delete, and modify operations
                
                ## Practical Applications
                Arrays and lists are used in:
                - Database indexing systems
                - Image processing algorithms
                - Scientific computing applications
                """;
            } else if (prompt.contains("Algorithms")) {
                return """
                # Algorithms: Sorting and Searching
                
                ## Fundamentals
                Algorithms are step-by-step procedures for solving computational problems efficiently.
                
                ### Sorting Algorithms
                - **Bubble Sort**: O(n²) time complexity, simple but inefficient
                - **Quick Sort**: O(n log n) average case, divide-and-conquer approach
                - **Merge Sort**: O(n log n) guaranteed, stable sorting algorithm
                
                ### Searching Algorithms
                - **Linear Search**: O(n) time complexity, works on unsorted data
                - **Binary Search**: O(log n) time complexity, requires sorted data
                - **Hash-based Search**: O(1) average case using hash tables
                
                ## Performance Considerations
                Choose algorithms based on:
                - Input size and characteristics
                - Memory constraints
                - Required stability and consistency
                """;
            } else if (prompt.contains("Database")) {
                return """
                # Database Design: Relational vs NoSQL
                
                ## Database Paradigms
                Modern applications require careful consideration of database architecture choices.
                
                ### Relational Databases (RDBMS)
                - **ACID Properties**: Atomicity, Consistency, Isolation, Durability
                - **SQL Interface**: Standardized query language
                - **Normalized Structure**: Reduces data redundancy
                
                ### NoSQL Databases
                - **Horizontal Scaling**: Distributed across multiple servers
                - **Flexible Schema**: Adaptable data models
                - **High Performance**: Optimized for specific use cases
                
                ## Selection Criteria
                - **Data Structure**: Structured vs. unstructured data
                - **Scalability Requirements**: Vertical vs. horizontal scaling
                - **Consistency Needs**: Strong vs. eventual consistency
                """;
            } else if (prompt.contains("System Architecture")) {
                return """
                # System Architecture: Microservices Patterns
                
                ## Architectural Evolution
                Modern systems require scalable and maintainable architectural patterns.
                
                ### Microservices Benefits
                - **Service Independence**: Each service can be developed independently
                - **Technology Diversity**: Different services can use different technologies
                - **Fault Isolation**: Failures in one service don't cascade
                
                ### Design Patterns
                - **API Gateway**: Single entry point for client requests
                - **Service Discovery**: Dynamic service location and registration
                - **Circuit Breaker**: Prevents cascading failures
                
                ## Implementation Challenges
                - **Network Complexity**: Inter-service communication overhead
                - **Data Consistency**: Managing distributed transactions
                - **Monitoring**: Observability across multiple services
                """;
            } else {
                return """
                # Performance Optimization: Caching Strategies
                
                ## Optimization Fundamentals
                Performance optimization requires systematic approaches to identify and resolve bottlenecks.
                
                ### Caching Levels
                - **Application Cache**: In-memory data storage
                - **Database Cache**: Query result optimization
                - **CDN Cache**: Content delivery network acceleration
                
                ### Cache Strategies
                - **Cache-Aside**: Application manages cache population
                - **Write-Through**: Synchronous cache and database writes
                - **Write-Behind**: Asynchronous cache and database writes
                
                ## Performance Metrics
                - **Hit Ratio**: Percentage of cache hits vs. misses
                - **Latency Reduction**: Response time improvements
                - **Throughput Increase**: Requests processed per second
                """;
            }
        }
    }
    
    
    public static void main(String[] args) {
        try {
            VectorConnectedContentExample example = new VectorConnectedContentExample();
            example.demonstrateConnectedContent();
            
            System.out.println("=== Demo Complete ===");
            System.out.println("Vector-based connected content generation successfully demonstrated!");
            
        } catch (Exception e) {
            logger.severe("Error in demonstration: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
