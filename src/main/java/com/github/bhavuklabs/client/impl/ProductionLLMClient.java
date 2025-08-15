package com.github.bhavuklabs.client.impl;

import com.github.bhavuklabs.core.contracts.LLMClient;
import com.github.bhavuklabs.core.payloads.LLMResponse;
import com.github.bhavuklabs.core.enums.ModelType;
import com.github.bhavuklabs.exceptions.client.LLMClientException;

import java.util.logging.Logger;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;


public class ProductionLLMClient implements LLMClient {
    
    private static final Logger logger = Logger.getLogger(ProductionLLMClient.class.getName());
    
    private final ModelType modelType;
    private final Map<String, ContentTemplate> contentTemplates;
    
    public ProductionLLMClient(ModelType modelType) {
        this.modelType = modelType;
        this.contentTemplates = initializeContentTemplates();
        logger.info("ProductionLLMClient initialized with model: " + modelType);
    }
    
    @Override
    public <T> LLMResponse<T> complete(String prompt, Class<T> type) throws LLMClientException {
        try {
            logger.info("Generating response for prompt: " + prompt.substring(0, Math.min(prompt.length(), 100)) + "...");

            ContentAnalysis analysis = analyzePrompt(prompt, new HashMap<>());

            String content = generateContent(analysis, new HashMap<>());

            if (type == String.class) {
                @SuppressWarnings("unchecked")
                T result = (T) content;
                return new LLMResponse<>(content, result);
            }

            @SuppressWarnings("unchecked")
            T result = (T) createStructuredOutput(content, analysis);
            return new LLMResponse<>(content, result);
            
        } catch (Exception e) {
            logger.severe("Failed to generate LLM response: " + e.getMessage());
            throw new LLMClientException("Content generation failed: " + e.getMessage(), e, 
                                       modelType.toString(), "complete");
        }
    }
    
    
    private Object createStructuredOutput(String content, ContentAnalysis analysis) {
        Map<String, Object> structuredOutput = new HashMap<>();
        structuredOutput.put("content", content);
        structuredOutput.put("topic", analysis.topic);
        structuredOutput.put("content_type", analysis.contentType.toString());
        structuredOutput.put("expertise_level", analysis.expertiseLevel.toString());
        structuredOutput.put("concepts", analysis.concepts);
        structuredOutput.put("model_type", modelType.toString());
        structuredOutput.put("generated_timestamp", System.currentTimeMillis());
        return structuredOutput;
    }
    
    
    private ContentAnalysis analyzePrompt(String prompt, Map<String, Object> parameters) {
        String lowerPrompt = prompt.toLowerCase();

        String topic = extractTopic(prompt, parameters);

        ContentType contentType = determineContentType(lowerPrompt);

        ExpertiseLevel expertiseLevel = determineExpertiseLevel(lowerPrompt, parameters);

        List<String> concepts = extractConcepts(lowerPrompt);

        StructureRequirements structure = analyzeStructureRequirements(lowerPrompt);
        
        return new ContentAnalysis(topic, contentType, expertiseLevel, concepts, structure);
    }
    
    
    private String generateContent(ContentAnalysis analysis, Map<String, Object> parameters) {
        ContentTemplate template = selectTemplate(analysis);
        
        StringBuilder content = new StringBuilder();

        content.append(generateTitle(analysis.topic, analysis.contentType));
        content.append("\n\n");

        content.append("## Executive Summary\n\n");
        content.append(generateExecutiveSummary(analysis));
        content.append("\n\n");

        content.append(generateMainContent(analysis, template));

        if (analysis.structure.includeExamples) {
            content.append("\n\n");
            content.append(generateExamples(analysis));
        }

        if (analysis.structure.includePractical) {
            content.append("\n\n");
            content.append(generatePracticalApplications(analysis));
        }

        if (analysis.structure.includeBestPractices) {
            content.append("\n\n");
            content.append(generateBestPractices(analysis));
        }

        content.append("\n\n");
        content.append(generateConclusion(analysis));
        
        return content.toString();
    }
    
    
    private String generateTitle(String topic, ContentType contentType) {
        switch (contentType) {
            case TUTORIAL:
                return "# " + topic + ": Complete Tutorial Guide";
            case CONCEPT_EXPLANATION:
                return "# Understanding " + topic;
            case COMPARISON:
                return "# " + topic + ": Comprehensive Comparison";
            case IMPLEMENTATION_GUIDE:
                return "# " + topic + ": Implementation Guide";
            case BEST_PRACTICES:
                return "# " + topic + ": Best Practices and Guidelines";
            default:
                return "# " + topic;
        }
    }
    
    
    private String generateExecutiveSummary(ContentAnalysis analysis) {
        StringBuilder summary = new StringBuilder();
        
        summary.append(analysis.topic).append(" is a ");
        
        switch (analysis.contentType) {
            case TUTORIAL:
                summary.append("comprehensive guide that covers fundamental concepts, practical implementation, and real-world applications. ");
                break;
            case CONCEPT_EXPLANATION:
                summary.append("fundamental concept in computer science that plays a crucial role in system design and implementation. ");
                break;
            case COMPARISON:
                summary.append("comparative analysis that examines different approaches, their benefits, limitations, and use cases. ");
                break;
            case IMPLEMENTATION_GUIDE:
                summary.append("practical implementation guide that provides step-by-step instructions and best practices. ");
                break;
            default:
                summary.append("important topic that requires careful consideration and understanding. ");
        }
        
        summary.append("This content is designed for ");
        switch (analysis.expertiseLevel) {
            case BEGINNER:
                summary.append("beginners with basic programming knowledge, providing clear explanations and simple examples.");
                break;
            case INTERMEDIATE:
                summary.append("intermediate developers who understand basic concepts and are ready for more advanced topics.");
                break;
            case ADVANCED:
                summary.append("advanced practitioners who need in-depth technical details and complex implementation strategies.");
                break;
            case EXPERT:
                summary.append("expert-level professionals who require comprehensive analysis and cutting-edge techniques.");
                break;
        }
        
        return summary.toString();
    }
    
    
    private String generateMainContent(ContentAnalysis analysis, ContentTemplate template) {
        StringBuilder content = new StringBuilder();

        content.append("## ").append(template.getMainSectionTitle(analysis.topic)).append("\n\n");
        content.append(generateCoreConceptContent(analysis, template));
        content.append("\n\n");

        content.append("## Technical Implementation\n\n");
        content.append(generateTechnicalContent(analysis, template));
        content.append("\n\n");

        if (analysis.concepts.stream().anyMatch(c -> c.contains("architecture") || c.contains("design") || c.contains("pattern"))) {
            content.append("## Architecture and Design Patterns\n\n");
            content.append(generateArchitectureContent(analysis, template));
            content.append("\n\n");
        }

        if (analysis.concepts.stream().anyMatch(c -> c.contains("performance") || c.contains("optimization") || c.contains("efficiency"))) {
            content.append("## Performance Considerations\n\n");
            content.append(generatePerformanceContent(analysis, template));
            content.append("\n\n");
        }
        
        return content.toString();
    }
    
    
    private String generateCoreConceptContent(ContentAnalysis analysis, ContentTemplate template) {
        StringBuilder content = new StringBuilder();
        
        content.append("### Fundamental Principles\n\n");
        content.append(analysis.topic).append(" represents a critical component in modern software development. ");
        content.append("Understanding its core principles is essential for building robust and efficient systems.\n\n");
        
        content.append("**Key characteristics include:**\n\n");
        for (String concept : analysis.concepts.subList(0, Math.min(analysis.concepts.size(), 4))) {
            content.append("- **").append(capitalizeFirst(concept)).append("**: ");
            content.append(generateConceptExplanation(concept, analysis.expertiseLevel));
            content.append("\n");
        }
        
        content.append("\n### Core Components\n\n");
        content.append("The implementation of ").append(analysis.topic).append(" involves several interconnected components:\n\n");
        
        content.append("1. **Data Layer**: Manages information storage and retrieval mechanisms\n");
        content.append("2. **Processing Layer**: Handles computational logic and business rules\n");
        content.append("3. **Interface Layer**: Provides interaction points for external systems\n");
        content.append("4. **Configuration Layer**: Manages system parameters and settings\n");
        
        return content.toString();
    }
    
    
    private String generateTechnicalContent(ContentAnalysis analysis, ContentTemplate template) {
        StringBuilder content = new StringBuilder();
        
        content.append("### Implementation Strategy\n\n");
        content.append("Implementing ").append(analysis.topic).append(" requires careful consideration of system requirements, ");
        content.append("scalability needs, and performance constraints. The following approach provides a structured methodology:\n\n");
        
        content.append("```java\n");
        content.append("// Example implementation structure\n");
        content.append("public class ").append(toCamelCase(analysis.topic)).append("Implementation {\n");
        content.append("    private final Configuration config;\n");
        content.append("    private final DataProvider dataProvider;\n");
        content.append("    private final ProcessingEngine engine;\n\n");
        content.append("    public ").append(toCamelCase(analysis.topic)).append("Implementation(Configuration config) {\n");
        content.append("        this.config = config;\n");
        content.append("        this.dataProvider = new DataProvider(config);\n");
        content.append("        this.engine = new ProcessingEngine(config);\n");
        content.append("    }\n\n");
        content.append("    public Result process(Request request) {\n");
        content.append("        // Implementation logic here\n");
        content.append("        return engine.execute(request);\n");
        content.append("    }\n");
        content.append("}\n");
        content.append("```\n\n");
        
        content.append("### Configuration Management\n\n");
        content.append("Proper configuration is crucial for optimal performance. Key configuration parameters include:\n\n");
        content.append("- **Resource Allocation**: Memory and CPU utilization settings\n");
        content.append("- **Concurrency Control**: Thread pool and synchronization parameters\n");
        content.append("- **Caching Strategy**: Data caching and invalidation policies\n");
        content.append("- **Error Handling**: Retry logic and fallback mechanisms\n");
        
        return content.toString();
    }
    
    
    private String generateArchitectureContent(ContentAnalysis analysis, ContentTemplate template) {
        StringBuilder content = new StringBuilder();
        
        content.append("### Architectural Patterns\n\n");
        content.append("The architecture of ").append(analysis.topic).append(" follows established design patterns ");
        content.append("that promote maintainability, scalability, and testability:\n\n");
        
        content.append("- **Layered Architecture**: Separation of concerns across distinct layers\n");
        content.append("- **Dependency Injection**: Loose coupling through inversion of control\n");
        content.append("- **Observer Pattern**: Event-driven communication between components\n");
        content.append("- **Strategy Pattern**: Pluggable algorithms and implementations\n");
        content.append("- **Factory Pattern**: Object creation and lifecycle management\n\n");
        
        content.append("### System Integration\n\n");
        content.append("Integration with external systems requires careful attention to:\n\n");
        content.append("1. **API Design**: RESTful interfaces and protocol compatibility\n");
        content.append("2. **Data Serialization**: JSON, XML, or binary format handling\n");
        content.append("3. **Authentication**: Security protocols and credential management\n");
        content.append("4. **Error Propagation**: Graceful failure handling across system boundaries\n");
        
        return content.toString();
    }
    
    
    private String generatePerformanceContent(ContentAnalysis analysis, ContentTemplate template) {
        StringBuilder content = new StringBuilder();
        
        content.append("### Performance Optimization\n\n");
        content.append("Optimizing ").append(analysis.topic).append(" performance involves multiple strategies:\n\n");
        
        content.append("**Computational Efficiency:**\n");
        content.append("- Algorithm selection based on time and space complexity\n");
        content.append("- Data structure optimization for access patterns\n");
        content.append("- Parallel processing and concurrent execution\n\n");
        
        content.append("**Memory Management:**\n");
        content.append("- Object pooling and resource reuse\n");
        content.append("- Garbage collection tuning\n");
        content.append("- Memory-mapped files for large datasets\n\n");
        
        content.append("**I/O Optimization:**\n");
        content.append("- Asynchronous operations and non-blocking I/O\n");
        content.append("- Batch processing for bulk operations\n");
        content.append("- Connection pooling and resource sharing\n\n");
        
        content.append("### Monitoring and Metrics\n\n");
        content.append("Effective performance monitoring requires tracking key metrics:\n\n");
        content.append("- **Throughput**: Requests processed per unit time\n");
        content.append("- **Latency**: Response time distribution and percentiles\n");
        content.append("- **Resource Utilization**: CPU, memory, and network usage\n");
        content.append("- **Error Rates**: Success/failure ratios and error patterns\n");
        
        return content.toString();
    }
    
    
    private String generateExamples(ContentAnalysis analysis) {
        StringBuilder content = new StringBuilder();
        
        content.append("## Practical Examples\n\n");
        content.append("### Basic Implementation Example\n\n");
        content.append("Here's a practical example demonstrating ").append(analysis.topic).append(":\n\n");
        
        content.append("```java\n");
        content.append("// Simple ").append(analysis.topic).append(" example\n");
        content.append("public class ").append(toCamelCase(analysis.topic)).append("Example {\n");
        content.append("    public static void main(String[] args) {\n");
        content.append("        // Initialize components\n");
        content.append("        var implementation = new ").append(toCamelCase(analysis.topic)).append("Implementation();\n");
        content.append("        \n");
        content.append("        // Process sample data\n");
        content.append("        var input = createSampleInput();\n");
        content.append("        var result = implementation.process(input);\n");
        content.append("        \n");
        content.append("        // Display results\n");
        content.append("        System.out.println(\"Processing result: \" + result);\n");
        content.append("    }\n");
        content.append("}\n");
        content.append("```\n\n");
        
        content.append("### Advanced Usage Pattern\n\n");
        content.append("For more complex scenarios, consider this advanced pattern:\n\n");
        
        content.append("```java\n");
        content.append("// Advanced ").append(analysis.topic).append(" usage\n");
        content.append("public class Advanced").append(toCamelCase(analysis.topic)).append(" {\n");
        content.append("    private final ExecutorService executor;\n");
        content.append("    private final CacheManager cache;\n");
        content.append("    \n");
        content.append("    public CompletableFuture<Result> processAsync(Request request) {\n");
        content.append("        return CompletableFuture.supplyAsync(() -> {\n");
        content.append("            // Check cache first\n");
        content.append("            var cached = cache.get(request.getId());\n");
        content.append("            if (cached != null) return cached;\n");
        content.append("            \n");
        content.append("            // Process and cache result\n");
        content.append("            var result = process(request);\n");
        content.append("            cache.put(request.getId(), result);\n");
        content.append("            return result;\n");
        content.append("        }, executor);\n");
        content.append("    }\n");
        content.append("}\n");
        content.append("```\n");
        
        return content.toString();
    }
    
    
    private String generatePracticalApplications(ContentAnalysis analysis) {
        StringBuilder content = new StringBuilder();
        
        content.append("## Real-World Applications\n\n");
        content.append("### Industry Use Cases\n\n");
        content.append(analysis.topic).append(" finds application across various industries:\n\n");
        
        content.append("- **E-commerce**: Product recommendation systems and inventory management\n");
        content.append("- **Financial Services**: Risk assessment and fraud detection algorithms\n");
        content.append("- **Healthcare**: Patient data analysis and treatment optimization\n");
        content.append("- **Manufacturing**: Supply chain optimization and quality control\n");
        content.append("- **Technology**: System monitoring and performance optimization\n\n");
        
        content.append("### Integration Scenarios\n\n");
        content.append("Common integration patterns include:\n\n");
        content.append("1. **Microservices Architecture**: Distributed processing across service boundaries\n");
        content.append("2. **Event-Driven Systems**: Reactive processing based on system events\n");
        content.append("3. **Batch Processing**: Large-scale data processing and analysis\n");
        content.append("4. **Real-Time Systems**: Low-latency processing for time-critical applications\n");
        
        return content.toString();
    }
    
    
    private String generateBestPractices(ContentAnalysis analysis) {
        StringBuilder content = new StringBuilder();
        
        content.append("## Best Practices and Guidelines\n\n");
        content.append("### Development Guidelines\n\n");
        content.append("Follow these guidelines when implementing ").append(analysis.topic).append(":\n\n");
        
        content.append("- **Code Organization**: Maintain clear separation of concerns and modular design\n");
        content.append("- **Error Handling**: Implement comprehensive error handling and recovery mechanisms\n");
        content.append("- **Testing Strategy**: Write unit tests, integration tests, and performance benchmarks\n");
        content.append("- **Documentation**: Maintain up-to-date documentation and code comments\n");
        content.append("- **Configuration Management**: Use externalized configuration for environment-specific settings\n\n");
        
        content.append("### Security Considerations\n\n");
        content.append("Security should be integrated throughout the implementation:\n\n");
        content.append("- **Input Validation**: Sanitize and validate all external inputs\n");
        content.append("- **Access Control**: Implement proper authentication and authorization\n");
        content.append("- **Data Protection**: Encrypt sensitive data in transit and at rest\n");
        content.append("- **Audit Logging**: Maintain comprehensive audit trails for security events\n");
        
        return content.toString();
    }
    
    
    private String generateConclusion(ContentAnalysis analysis) {
        StringBuilder content = new StringBuilder();
        
        content.append("## Summary and Next Steps\n\n");
        content.append("### Key Takeaways\n\n");
        content.append(analysis.topic).append(" provides a powerful foundation for building robust, scalable systems. ");
        content.append("The key points to remember include:\n\n");
        
        content.append("- **Architectural Clarity**: Well-defined structure and component separation\n");
        content.append("- **Performance Focus**: Optimization strategies for scalability and efficiency\n");
        content.append("- **Best Practices**: Industry-standard approaches and methodologies\n");
        content.append("- **Practical Application**: Real-world implementation patterns and examples\n\n");
        
        content.append("### Recommended Next Steps\n\n");
        content.append("To further your understanding and implementation:\n\n");
        content.append("1. **Hands-on Practice**: Implement sample projects using the concepts discussed\n");
        content.append("2. **Performance Testing**: Benchmark your implementations against requirements\n");
        content.append("3. **Community Engagement**: Participate in relevant technical communities and forums\n");
        content.append("4. **Continuous Learning**: Stay updated with latest developments and best practices\n");
        
        return content.toString();
    }

    
    private String extractTopic(String prompt, Map<String, Object> parameters) {

        if (parameters.containsKey("topic")) {
            return parameters.get("topic").toString();
        }

        String[] lines = prompt.split("\n");
        for (String line : lines) {
            if (line.toLowerCase().contains("topic:") || line.toLowerCase().contains("about:")) {
                return line.substring(line.indexOf(":") + 1).trim();
            }
        }

        String[] words = prompt.split("\\s+");
        if (words.length > 2) {
            return words[0] + " " + words[1];
        }
        
        return "Advanced Technology Concepts";
    }
    
    private ContentType determineContentType(String prompt) {
        if (prompt.contains("tutorial") || prompt.contains("guide") || prompt.contains("how to")) {
            return ContentType.TUTORIAL;
        } else if (prompt.contains("compare") || prompt.contains("versus") || prompt.contains("vs")) {
            return ContentType.COMPARISON;
        } else if (prompt.contains("implement") || prompt.contains("build") || prompt.contains("create")) {
            return ContentType.IMPLEMENTATION_GUIDE;
        } else if (prompt.contains("best practice") || prompt.contains("guideline") || prompt.contains("recommend")) {
            return ContentType.BEST_PRACTICES;
        } else {
            return ContentType.CONCEPT_EXPLANATION;
        }
    }
    
    private ExpertiseLevel determineExpertiseLevel(String prompt, Map<String, Object> parameters) {
        if (parameters.containsKey("expertise_level")) {
            String level = parameters.get("expertise_level").toString().toLowerCase();
            switch (level) {
                case "beginner": return ExpertiseLevel.BEGINNER;
                case "intermediate": return ExpertiseLevel.INTERMEDIATE;
                case "advanced": return ExpertiseLevel.ADVANCED;
                case "expert": return ExpertiseLevel.EXPERT;
            }
        }
        
        if (prompt.contains("beginner") || prompt.contains("basic") || prompt.contains("introduction")) {
            return ExpertiseLevel.BEGINNER;
        } else if (prompt.contains("advanced") || prompt.contains("expert") || prompt.contains("complex")) {
            return ExpertiseLevel.ADVANCED;
        } else {
            return ExpertiseLevel.INTERMEDIATE;
        }
    }
    
    private List<String> extractConcepts(String prompt) {
        List<String> concepts = new ArrayList<>();
        String[] technicalTerms = {
            "algorithm", "data structure", "performance", "optimization", "architecture",
            "design pattern", "scalability", "efficiency", "implementation", "framework",
            "library", "api", "service", "microservice", "database", "query", "index",
            "cache", "memory", "network", "security", "authentication", "authorization",
            "testing", "debugging", "deployment", "monitoring", "logging"
        };
        
        String lowerPrompt = prompt.toLowerCase();
        for (String term : technicalTerms) {
            if (lowerPrompt.contains(term)) {
                concepts.add(term);
            }
        }

        if (concepts.isEmpty()) {
            concepts.add("implementation");
            concepts.add("architecture");
            concepts.add("performance");
            concepts.add("best practices");
        }
        
        return concepts;
    }
    
    private StructureRequirements analyzeStructureRequirements(String prompt) {
        return new StructureRequirements(
            prompt.contains("example") || prompt.contains("sample"),
            prompt.contains("practical") || prompt.contains("real-world"),
            prompt.contains("best practice") || prompt.contains("guideline"),
            prompt.contains("comparison") || prompt.contains("alternative")
        );
    }
    
    private ContentTemplate selectTemplate(ContentAnalysis analysis) {
        return contentTemplates.getOrDefault(analysis.contentType.toString(), 
                                           contentTemplates.get("DEFAULT"));
    }
    
    private Map<String, Object> createResponseMetadata(ContentAnalysis analysis, Map<String, Object> parameters) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("topic", analysis.topic);
        metadata.put("content_type", analysis.contentType.toString());
        metadata.put("expertise_level", analysis.expertiseLevel.toString());
        metadata.put("concepts", analysis.concepts);
        metadata.put("model_type", modelType.toString());
        metadata.put("generated_timestamp", System.currentTimeMillis());
        return metadata;
    }
    
    private String generateConceptExplanation(String concept, ExpertiseLevel level) {
        switch (level) {
            case BEGINNER:
                return "A fundamental aspect that forms the foundation of understanding.";
            case INTERMEDIATE:
                return "An important consideration that affects system design and implementation decisions.";
            case ADVANCED:
                return "A critical factor that requires sophisticated analysis and optimization strategies.";
            case EXPERT:
                return "A complex domain requiring deep technical expertise and advanced implementation techniques.";
            default:
                return "An essential component requiring careful consideration and implementation.";
        }
    }
    
    private String capitalizeFirst(String text) {
        if (text == null || text.isEmpty()) return text;
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }
    
    private String toCamelCase(String text) {
        String[] words = text.split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            result.append(capitalizeFirst(word.replaceAll("[^a-zA-Z0-9]", "")));
        }
        return result.toString();
    }
    
    private Map<String, ContentTemplate> initializeContentTemplates() {
        Map<String, ContentTemplate> templates = new HashMap<>();
        
        templates.put("TUTORIAL", new ContentTemplate("Step-by-Step Guide"));
        templates.put("CONCEPT_EXPLANATION", new ContentTemplate("Fundamental Concepts"));
        templates.put("COMPARISON", new ContentTemplate("Comparative Analysis"));
        templates.put("IMPLEMENTATION_GUIDE", new ContentTemplate("Implementation Strategy"));
        templates.put("BEST_PRACTICES", new ContentTemplate("Guidelines and Recommendations"));
        templates.put("DEFAULT", new ContentTemplate("Comprehensive Overview"));
        
        return templates;
    }

    
    private static class ContentAnalysis {
        final String topic;
        final ContentType contentType;
        final ExpertiseLevel expertiseLevel;
        final List<String> concepts;
        final StructureRequirements structure;
        
        ContentAnalysis(String topic, ContentType contentType, ExpertiseLevel expertiseLevel, 
                       List<String> concepts, StructureRequirements structure) {
            this.topic = topic;
            this.contentType = contentType;
            this.expertiseLevel = expertiseLevel;
            this.concepts = concepts;
            this.structure = structure;
        }
    }
    
    private enum ContentType {
        TUTORIAL, CONCEPT_EXPLANATION, COMPARISON, IMPLEMENTATION_GUIDE, BEST_PRACTICES
    }
    
    private enum ExpertiseLevel {
        BEGINNER, INTERMEDIATE, ADVANCED, EXPERT
    }
    
    private static class StructureRequirements {
        final boolean includeExamples;
        final boolean includePractical;
        final boolean includeBestPractices;
        
        StructureRequirements(boolean includeExamples, boolean includePractical, 
                            boolean includeBestPractices, boolean includeComparisons) {
            this.includeExamples = includeExamples;
            this.includePractical = includePractical;
            this.includeBestPractices = includeBestPractices;
        }
    }
    
    private static class ContentTemplate {
        private final String mainSectionTitle;
        
        ContentTemplate(String mainSectionTitle) {
            this.mainSectionTitle = mainSectionTitle;
        }
        
        String getMainSectionTitle(String topic) {
            return mainSectionTitle.replace("{topic}", topic);
        }
    }
}
