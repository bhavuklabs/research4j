package com.github.bhavuklabs.examples;

import com.github.bhavuklabs.builders.ConnectedContentBuilder;
import com.github.bhavuklabs.services.ContentVectorizer;
import com.github.bhavuklabs.services.impl.SimpleEmbeddingService;
import com.github.bhavuklabs.vector.VectorStore;
import com.github.bhavuklabs.deepresearch.models.PersonalizedMarkdownConfig;
import com.github.bhavuklabs.core.contracts.LLMClient;
import com.github.bhavuklabs.core.payloads.LLMResponse;

import java.util.*;
import java.util.logging.Logger;


public class ConnectedContentDemoExample {
    
    private static final Logger logger = Logger.getLogger(ConnectedContentDemoExample.class.getName());
    
    private final VectorStore vectorStore;
    private final ContentVectorizer vectorizer;
    private final ConnectedContentBuilder contentBuilder;
    private final String sessionId;
    
    public ConnectedContentDemoExample() {
        this.vectorStore = new VectorStore(384);
        this.vectorizer = new ContentVectorizer(vectorStore, new SimpleEmbeddingService());
        this.sessionId = "demo_session_" + System.currentTimeMillis();

        LLMClient mockLLMClient = new TopicAwareMockLLMClient();
        this.contentBuilder = new ConnectedContentBuilder(mockLLMClient, vectorizer, sessionId);
        
        logger.info("ConnectedContentDemo initialized with session: " + sessionId);
    }
    
    public static void main(String[] args) {
        ConnectedContentDemoExample demo = new ConnectedContentDemoExample();
        demo.runConnectedContentDemo();
    }
    
    public void runConnectedContentDemo() {
        System.out.println("=== Connected Content Generation with Cross-References ===\n");

        Map<String, String> topics = new LinkedHashMap<>();
        topics.put("Programming Fundamentals", "Variables and Data Types");
        topics.put("Programming Fundamentals", "Control Structures");
        topics.put("Data Structures", "Arrays and Lists");
        topics.put("Data Structures", "Trees and Graphs");
        topics.put("Algorithms", "Sorting and Searching");
        topics.put("Algorithms", "Graph Algorithms");
        topics.put("System Design", "Scalability Patterns");
        topics.put("System Design", "Database Design");

        List<String> generatedContent = new ArrayList<>();
        PersonalizedMarkdownConfig config = createProgressiveConfig();
        
        int contentIndex = 1;
        for (Map.Entry<String, String> entry : topics.entrySet()) {
            String topic = entry.getKey();
            String subtopic = entry.getValue();
            
            System.out.printf("Generating Content %d: %s - %s\n", contentIndex++, topic, subtopic);
            
            String connectedContent = contentBuilder.buildConnectedContent(topic, subtopic, config);
            generatedContent.add(connectedContent);
            
            System.out.printf("Generated %d characters\n", connectedContent.length());
            System.out.println("Sample with connections:");
            System.out.println(connectedContent.substring(0, Math.min(200, connectedContent.length())) + "...\n");

            try { Thread.sleep(100); } catch (InterruptedException e) { }
        }

        analyzeContentNetwork();

        demonstrateSemanticDiscovery();

        showLearningPaths();

        displayConnectedContentExample(generatedContent);
        
        System.out.println("=== Connected Content Demo Complete ===");
    }
    
    private void analyzeContentNetwork() {
        System.out.println("=== Content Network Analysis ===");
        
        ContentVectorizer.ContentAnalysis analysis = contentBuilder.getContentAnalysis();
        System.out.printf("Total Content Pieces: %d\n", analysis.getTotalContent());
        System.out.printf("Total Connections: %d\n", analysis.getTotalConnections());
        System.out.printf("Average Connections: %.1f\n", analysis.getAverageConnections());
        System.out.printf("Most Connected: %s\n\n", analysis.getMostConnectedContent());
    }
    
    private void demonstrateSemanticDiscovery() {
        System.out.println("=== Semantic Content Discovery ===");
        
        String[] searchQueries = {
            "data organization and storage",
            "algorithm efficiency optimization",
            "system architecture patterns",
            "programming language concepts"
        };
        
        for (String query : searchQueries) {
            System.out.printf("Search: '%s'\n", query);
            List<ContentVectorizer.RelatedContent> results = 
                vectorizer.findRelatedContent(query, 3, sessionId);
            
            if (results.isEmpty()) {
                System.out.println("  No related content found\n");
            } else {
                for (ContentVectorizer.RelatedContent content : results) {
                    System.out.printf("  ├─ %s (similarity: %.3f)\n", 
                                    content.getTopic(), content.getSimilarity());
                    System.out.printf("     %s\n", 
                                    content.getContent().substring(0, Math.min(80, content.getContent().length())) + "...");
                }
                System.out.println();
            }
        }
    }
    
    private void showLearningPaths() {
        System.out.println("=== Learning Path Generation ===");
        
        Map<String, String> sessionContent = contentBuilder.getSessionContent();
        System.out.printf("Generated %d interconnected content pieces:\n", sessionContent.size());
        
        int index = 1;
        for (Map.Entry<String, String> entry : sessionContent.entrySet()) {
            String contentId = entry.getKey();
            String content = entry.getValue();
            
            System.out.printf("  %d. %s (%d chars)\n", 
                            index++, formatContentId(contentId), content.length());
        }

        List<String> gaps = vectorizer.suggestContentGaps(sessionId, Arrays.asList(
            "testing strategies", "deployment patterns", "security practices", 
            "performance monitoring", "code review processes"
        ));
        
        System.out.println("\nSuggested Content Gaps:");
        for (String gap : gaps) {
            System.out.printf("  ├─ %s\n", gap);
        }
        System.out.println();
    }
    
    private void displayConnectedContentExample(List<String> generatedContent) {
        System.out.println("=== Sample Connected Content ===");
        
        if (!generatedContent.isEmpty()) {
            String sampleContent = generatedContent.get(generatedContent.size() - 1);

            System.out.println("Latest generated content with cross-references:");
            System.out.println("─".repeat(60));
            System.out.println(sampleContent);
            System.out.println("─".repeat(60));
        }
    }
    
    private PersonalizedMarkdownConfig createProgressiveConfig() {
        return PersonalizedMarkdownConfig.builder()
                .userExpertiseLevel("intermediate")
                .maxTopicsPerSection(3)
                .maxSubtopicsPerTopic(4)
                .targetWordsPerSubtopic(150)
                .enableHierarchicalBreakdown(true)
                .includeExamples(true)
                .enableProgressiveDisclosure(true)
                .useEnhancedLists(true)
                .includeCodeBlocks(true)
                .maxHeadingDepth(4)
                .requirePracticalExamples(true)
                .build();
    }
    
    private String formatContentId(String contentId) {
        String formatted = contentId.substring(contentId.indexOf(':') + 1)
                                  .replace('_', ' ');

        String[] words = formatted.split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.length() > 0) {
                result.append(Character.toUpperCase(word.charAt(0)))
                      .append(word.substring(1).toLowerCase())
                      .append(" ");
            }
        }
        return result.toString().trim();
    }
    
    
    private static class TopicAwareMockLLMClient implements LLMClient {
        
        @Override
        @SuppressWarnings("unchecked")
        public <T> LLMResponse<T> complete(String prompt, Class<T> type) {
            String content = generateTopicSpecificContent(prompt);
            return new LLMResponse<>(content, (T) content);
        }
        
        private String generateTopicSpecificContent(String prompt) {
            String lowerPrompt = prompt.toLowerCase();
            
            if (lowerPrompt.contains("variables") || lowerPrompt.contains("data types")) {
                return generateVariablesContent();
            } else if (lowerPrompt.contains("control structures")) {
                return generateControlStructuresContent();
            } else if (lowerPrompt.contains("arrays") || lowerPrompt.contains("lists")) {
                return generateArraysContent();
            } else if (lowerPrompt.contains("trees") || lowerPrompt.contains("graphs")) {
                return generateTreesGraphsContent();
            } else if (lowerPrompt.contains("sorting") || lowerPrompt.contains("searching")) {
                return generateSortingSearchingContent();
            } else if (lowerPrompt.contains("graph algorithms")) {
                return generateGraphAlgorithmsContent();
            } else if (lowerPrompt.contains("scalability")) {
                return generateScalabilityContent();
            } else if (lowerPrompt.contains("database design")) {
                return generateDatabaseDesignContent();
            } else {
                return generateGenericContent(prompt);
            }
        }
        
        private String generateVariablesContent() {
            return """
                # Programming Fundamentals: Variables and Data Types
                
                ## Overview
                Variables are named containers that store data values. Understanding data types is fundamental to programming.
                
                ## Key Concepts
                - **Variable Declaration**: Creating named storage locations
                - **Data Types**: Integer, Float, String, Boolean classifications
                - **Type Safety**: Preventing data type mismatches
                - **Memory Allocation**: How variables occupy system memory
                
                ## Example
                ```java
                int count = 10;        // Integer variable
                String name = "John";  // String variable
                boolean active = true; // Boolean variable
                ```
                
                ## Best Practices
                - Use descriptive variable names
                - Choose appropriate data types
                - Initialize variables before use
                - Follow naming conventions
                """;
        }
        
        private String generateControlStructuresContent() {
            return """
                # Programming Fundamentals: Control Structures
                
                ## Overview
                Control structures direct the flow of program execution through decisions and loops.
                
                ## Key Concepts
                - **Conditional Statements**: if-else, switch-case for decision making
                - **Loops**: for, while, do-while for repetitive tasks
                - **Flow Control**: break, continue, return statements
                - **Nested Structures**: Combining multiple control structures
                
                ## Example
                ```java
                for (int i = 0; i < 10; i++) {
                    if (i % 2 == 0) {
                        System.out.println("Even: " + i);
                    } else {
                        System.out.println("Odd: " + i);
                    }
                }
                ```
                
                ## Applications
                - User input validation
                - Data processing loops
                - Menu-driven programs
                - Algorithm implementation
                """;
        }
        
        private String generateArraysContent() {
            return """
                # Data Structures: Arrays and Lists
                
                ## Overview
                Arrays and lists are fundamental data structures for storing collections of elements.
                
                ## Key Concepts
                - **Array Structure**: Fixed-size, contiguous memory allocation
                - **List Operations**: Dynamic sizing, insertion, deletion
                - **Access Patterns**: Index-based element retrieval
                - **Performance**: Time complexity of operations
                
                ## Example
                ```java
                int[] array = {1, 2, 3, 4, 5};
                List<String> list = new ArrayList<>();
                list.add("apple");
                list.add("banana");
                ```
                
                ## Use Cases
                - Data collection and storage
                - Algorithm input/output
                - Database record representation
                - Mathematical computations
                """;
        }
        
        private String generateTreesGraphsContent() {
            return """
                # Data Structures: Trees and Graphs
                
                ## Overview
                Trees and graphs represent hierarchical and networked data relationships.
                
                ## Key Concepts
                - **Tree Structure**: Hierarchical parent-child relationships
                - **Graph Theory**: Vertices and edges for complex relationships
                - **Traversal Methods**: DFS, BFS for systematic exploration
                - **Applications**: File systems, social networks, routing
                
                ## Example
                ```java
                class TreeNode {
                    int val;
                    TreeNode left, right;
                    TreeNode(int val) { this.val = val; }
                }
                ```
                
                ## Common Types
                - Binary trees for sorted data
                - Graphs for network modeling
                - Heaps for priority queues
                - Tries for string searching
                """;
        }
        
        private String generateSortingSearchingContent() {
            return """
                # Algorithms: Sorting and Searching
                
                ## Overview
                Sorting and searching algorithms are fundamental for data organization and retrieval.
                
                ## Key Concepts
                - **Sorting Algorithms**: QuickSort, MergeSort, HeapSort
                - **Search Techniques**: Binary search, linear search
                - **Time Complexity**: Big O notation for performance analysis
                - **Space Complexity**: Memory usage optimization
                
                ## Example
                ```java
                public int binarySearch(int[] arr, int target) {
                    int left = 0, right = arr.length - 1;
                    while (left <= right) {
                        int mid = left + (right - left) / 2;
                        if (arr[mid] == target) return mid;
                        if (arr[mid] < target) left = mid + 1;
                        else right = mid - 1;
                    }
                    return -1;
                }
                ```
                
                ## Performance Analysis
                - Binary search: O(log n) time complexity
                - QuickSort average: O(n log n)
                - Linear search: O(n) time complexity
                """;
        }
        
        private String generateGraphAlgorithmsContent() {
            return """
                # Algorithms: Graph Algorithms
                
                ## Overview
                Graph algorithms solve problems involving networks, paths, and connectivity.
                
                ## Key Concepts
                - **Path Finding**: Dijkstra's algorithm, A* search
                - **Traversal**: Depth-first search, breadth-first search
                - **Connectivity**: Strongly connected components
                - **Minimum Spanning Tree**: Kruskal's and Prim's algorithms
                
                ## Example
                ```java
                public void dfs(Graph graph, int vertex, boolean[] visited) {
                    visited[vertex] = true;
                    System.out.println(vertex);
                    
                    for (int adjacent : graph.getAdjacent(vertex)) {
                        if (!visited[adjacent]) {
                            dfs(graph, adjacent, visited);
                        }
                    }
                }
                ```
                
                ## Applications
                - Social network analysis
                - Route optimization
                - Dependency resolution
                - Network flow problems
                """;
        }
        
        private String generateScalabilityContent() {
            return """
                # System Design: Scalability Patterns
                
                ## Overview
                Scalability patterns enable systems to handle increasing loads effectively.
                
                ## Key Concepts
                - **Horizontal Scaling**: Adding more servers
                - **Vertical Scaling**: Upgrading existing hardware
                - **Load Balancing**: Distributing requests across servers
                - **Caching Strategies**: Reducing database load
                
                ## Example
                ```java
                @Component
                public class LoadBalancer {
                    private List<Server> servers;
                    private AtomicInteger currentIndex = new AtomicInteger(0);
                    
                    public Server getNextServer() {
                        int index = currentIndex.getAndIncrement() % servers.size();
                        return servers.get(index);
                    }
                }
                ```
                
                ## Patterns
                - Microservices architecture
                - Database sharding
                - Content delivery networks
                - Asynchronous processing
                """;
        }
        
        private String generateDatabaseDesignContent() {
            return """
                # System Design: Database Design
                
                ## Overview
                Database design involves structuring data for efficient storage and retrieval.
                
                ## Key Concepts
                - **Normalization**: Reducing data redundancy
                - **Indexing**: Optimizing query performance
                - **Relationships**: One-to-many, many-to-many associations
                - **ACID Properties**: Ensuring data consistency
                
                ## Example
                ```sql
                CREATE TABLE users (
                    id SERIAL PRIMARY KEY,
                    username VARCHAR(50) UNIQUE NOT NULL,
                    email VARCHAR(100) UNIQUE NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
                
                CREATE INDEX idx_users_email ON users(email);
                ```
                
                ## Design Principles
                - Choose appropriate data types
                - Establish clear relationships
                - Optimize for common queries
                - Plan for future scalability
                """;
        }
        
        private String generateGenericContent(String prompt) {
            return String.format("""
                # Generated Content
                
                ## Overview
                This content was generated for the topic: %s
                
                ## Key Points
                - Comprehensive coverage of the subject matter
                - Practical examples and implementations
                - Best practices and recommendations
                - Performance considerations
                
                ## Example
                ```

                public class Example {
                    public void demonstrate() {
                        System.out.println("Example implementation");
                    }
                }
                ```
                """, prompt.length() > 50 ? prompt.substring(0, 50) + "..." : prompt);
        }
    }
}
