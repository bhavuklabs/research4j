package com.github.bhavuklabs.builders;

import com.github.bhavuklabs.services.ContentVectorizer;
import com.github.bhavuklabs.services.ContentVectorizer.RelatedContent;
import com.github.bhavuklabs.deepresearch.models.PersonalizedMarkdownConfig;
import com.github.bhavuklabs.deepresearch.pipeline.PersonalizedMarkdownBuilder;
import com.github.bhavuklabs.deepresearch.models.DeepResearchResult;
import com.github.bhavuklabs.core.contracts.LLMClient;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;


public class ConnectedContentBuilder extends PersonalizedMarkdownBuilder {
    
    private static final Logger logger = Logger.getLogger(ConnectedContentBuilder.class.getName());
    
    private final ContentVectorizer vectorizer;
    private final String sessionId;
    private final Map<String, String> generatedContent;
    
    public ConnectedContentBuilder(LLMClient llmClient, ContentVectorizer vectorizer, String sessionId) {
        super(llmClient);
        this.vectorizer = vectorizer;
        this.sessionId = sessionId;
        this.generatedContent = new HashMap<>();
    }
    
    
    public String buildConnectedContent(String topic, String subtopic, PersonalizedMarkdownConfig config) {
        String contentId = generateContentId(topic, subtopic);

        DeepResearchResult mockResult = createMockResearchResult(topic, subtopic);

        String baseContent = super.buildPersonalizedContent(mockResult, config);

        List<RelatedContent> relatedContent = vectorizer.findRelatedContent(
            topic + " " + subtopic, 5, sessionId);
        List<RelatedContent> sessionContent = vectorizer.findSessionContent(
            sessionId, contentId, 3);

        String connectedContent = enhanceWithConnections(baseContent, topic, subtopic, 
                                                       relatedContent, sessionContent, config);

        storeContent(contentId, connectedContent, topic, subtopic, config);

        generatedContent.put(contentId, connectedContent);
        
        return connectedContent;
    }
    
    
    private String enhanceWithConnections(String baseContent, String topic, String subtopic,
                                        List<RelatedContent> relatedContent, 
                                        List<RelatedContent> sessionContent,
                                        PersonalizedMarkdownConfig config) {
        
        StringBuilder enhanced = new StringBuilder();

        String prerequisites = generatePrerequisites(relatedContent, config);
        if (!prerequisites.isEmpty()) {
            enhanced.append(prerequisites).append("\n\n");
        }

        enhanced.append(baseContent);

        String relatedConcepts = generateRelatedConcepts(relatedContent, config);
        if (!relatedConcepts.isEmpty()) {
            enhanced.append("\n\n").append(relatedConcepts);
        }

        String learningPath = generateLearningPath(sessionContent, topic, config);
        if (!learningPath.isEmpty()) {
            enhanced.append("\n\n").append(learningPath);
        }

        String seeAlso = generateSeeAlso(relatedContent, sessionContent, config);
        if (!seeAlso.isEmpty()) {
            enhanced.append("\n\n").append(seeAlso);
        }
        
        return enhanced.toString();
    }
    
    
    private String generatePrerequisites(List<RelatedContent> relatedContent, PersonalizedMarkdownConfig config) {
        List<RelatedContent> prerequisites = relatedContent.stream()
                .filter(content -> isPrerequisite(content, config))
                .limit(2)
                .collect(Collectors.toList());
        
        if (prerequisites.isEmpty()) {
            return "";
        }
        
        StringBuilder prereq = new StringBuilder();
        prereq.append("### Prerequisites\n");
        prereq.append("> **Before proceeding**: Ensure you understand these concepts:\n");
        
        for (RelatedContent content : prerequisites) {
            prereq.append("> - **")
                  .append(content.getTopic())
                  .append("**: ")
                  .append(extractSummary(content.getContent(), 80))
                  .append("\n");
        }
        
        return prereq.toString();
    }
    
    
    private String generateRelatedConcepts(List<RelatedContent> relatedContent, PersonalizedMarkdownConfig config) {
        if (relatedContent.isEmpty()) {
            return "";
        }
        
        StringBuilder related = new StringBuilder();
        related.append("### Related Concepts\n");
        
        for (RelatedContent content : relatedContent.stream().limit(4).collect(Collectors.toList())) {
            String connectionReason = generateConnectionReason(content);
            related.append("- **")
                   .append(content.getTopic())
                   .append("**: ")
                   .append(extractSummary(content.getContent(), 100))
                   .append(" *(").append(connectionReason).append(")*\n");
        }
        
        return related.toString();
    }
    
    
    private String generateLearningPath(List<RelatedContent> sessionContent, String currentTopic, PersonalizedMarkdownConfig config) {
        if (sessionContent.isEmpty()) {
            return "";
        }

        List<RelatedContent> sortedContent = sessionContent.stream()
                .sorted(this::compareContentComplexity)
                .collect(Collectors.toList());
        
        StringBuilder path = new StringBuilder();
        path.append("### Learning Path\n");

        String previousTopic = findPreviousTopic(sortedContent, currentTopic);
        String nextTopic = findNextTopic(sortedContent, currentTopic);
        
        if (previousTopic != null) {
            path.append("1. **Previous**: ").append(previousTopic).append(" - Foundation concepts\n");
        }
        path.append("2. **Current**: ").append(currentTopic).append(" - You are here\n");
        if (nextTopic != null) {
            path.append("3. **Next**: ").append(nextTopic).append(" - Advanced applications\n");
        }
        
        return path.toString();
    }
    
    
    private String generateSeeAlso(List<RelatedContent> relatedContent, List<RelatedContent> sessionContent, PersonalizedMarkdownConfig config) {
        List<RelatedContent> seeAlsoContent = new ArrayList<>();
        seeAlsoContent.addAll(relatedContent.stream().limit(2).collect(Collectors.toList()));
        seeAlsoContent.addAll(sessionContent.stream().limit(2).collect(Collectors.toList()));
        
        if (seeAlsoContent.isEmpty()) {
            return "";
        }
        
        StringBuilder seeAlso = new StringBuilder();
        seeAlso.append("### See Also\n");
        
        for (RelatedContent content : seeAlsoContent.stream().distinct().limit(4).collect(Collectors.toList())) {
            String relevance = generateRelevanceExplanation(content);
            seeAlso.append("- [")
                   .append(content.getTopic())
                   .append("](")
                   .append(generateContentLink(content.getContentId()))
                   .append("): ")
                   .append(relevance)
                   .append("\n");
        }
        
        return seeAlso.toString();
    }
    
    
    private void storeContent(String contentId, String content, String topic, String subtopic, PersonalizedMarkdownConfig config) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("topic", topic);
        metadata.put("subtopic", subtopic);
        metadata.put("expertise_level", config.getUserExpertiseLevel());
        metadata.put("domain_knowledge", config.getDomainKnowledge());
        metadata.put("section_type", determineSectionType(content));
        metadata.put("content_structure", "hierarchical");
        metadata.put("complexity_score", calculateComplexityScore(content));
        
        try {
            vectorizer.storeContent(sessionId, contentId, content, metadata);
        } catch (Exception e) {
            logger.warning("Failed to store content in vector database: " + e.getMessage());
        }
    }

    
    private String generateContentId(String topic, String subtopic) {
        return (topic + "_" + subtopic).replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
    }
    
    private boolean isPrerequisite(RelatedContent content, PersonalizedMarkdownConfig config) {

        Object complexityObj = content.getMetadata().get("complexity_score");
        if (complexityObj instanceof Integer) {
            int contentComplexity = (Integer) complexityObj;
            String currentLevel = config.getUserExpertiseLevel();
            int currentComplexity = getComplexityLevel(currentLevel);
            return contentComplexity < currentComplexity;
        }
        return false;
    }
    
    private int getComplexityLevel(String expertiseLevel) {
        switch (expertiseLevel.toLowerCase()) {
            case "beginner": return 1;
            case "intermediate": return 2;
            case "advanced": return 3;
            case "expert": return 4;
            default: return 2; // default to intermediate
        }
    }
    
    
    private DeepResearchResult createMockResearchResult(String topic, String subtopic) {
        String query = topic + ": " + subtopic;
        String sessionId = this.sessionId;
        String finalReport = "Research content for " + query;
        List<com.github.bhavuklabs.citation.CitationResult> citations = new ArrayList<>();
        List<com.github.bhavuklabs.deepresearch.models.ResearchQuestion> questions = new ArrayList<>();
        Map<String, Object> knowledgeMap = new HashMap<>();
        java.time.Duration processingTime = java.time.Duration.ofSeconds(1);
        String strategyUsed = "Connected Content Generation";
        
        return new DeepResearchResult(sessionId, query, finalReport, citations, 
                                    questions, knowledgeMap, processingTime, strategyUsed);
    }
    
    private String extractSummary(String content, int maxLength) {
        if (content.length() <= maxLength) {
            return content;
        }

        String truncated = content.substring(0, maxLength);
        int lastSentence = truncated.lastIndexOf('.');
        if (lastSentence > maxLength / 2) {
            return truncated.substring(0, lastSentence + 1);
        }

        int lastSpace = truncated.lastIndexOf(' ');
        return truncated.substring(0, lastSpace) + "...";
    }
    
    private String generateConnectionReason(RelatedContent content) {
        float similarity = content.getSimilarity();
        if (similarity > 0.9f) return "Highly related concept";
        if (similarity > 0.8f) return "Similar approach";
        if (similarity > 0.7f) return "Related topic";
        return "Connected concept";
    }
    
    private int compareContentComplexity(RelatedContent a, RelatedContent b) {
        Integer complexityA = (Integer) a.getMetadata().getOrDefault("complexity_score", 0);
        Integer complexityB = (Integer) b.getMetadata().getOrDefault("complexity_score", 0);
        return complexityA.compareTo(complexityB);
    }
    
    private String findPreviousTopic(List<RelatedContent> sortedContent, String currentTopic) {

        return sortedContent.stream()
                .filter(content -> !content.getTopic().equals(currentTopic))
                .findFirst()
                .map(RelatedContent::getTopic)
                .orElse(null);
    }
    
    private String findNextTopic(List<RelatedContent> sortedContent, String currentTopic) {

        return sortedContent.stream()
                .filter(content -> !content.getTopic().equals(currentTopic))
                .reduce((first, second) -> second)
                .map(RelatedContent::getTopic)
                .orElse(null);
    }
    
    private String generateRelevanceExplanation(RelatedContent content) {
        String sectionType = content.getSectionType();
        if ("example".equals(sectionType)) {
            return "Practical implementation details";
        } else if ("introduction".equals(sectionType)) {
            return "Foundational concepts";
        } else if ("summary".equals(sectionType)) {
            return "Key takeaways and conclusions";
        }
        return "Related conceptual material";
    }
    
    private String generateContentLink(String contentId) {
        return "#" + contentId.replace("_", "-");
    }
    
    private String determineSectionType(String content) {
        String lowerContent = content.toLowerCase();
        if (lowerContent.contains("introduction") || lowerContent.contains("overview")) {
            return "introduction";
        } else if (lowerContent.contains("example") || lowerContent.contains("implementation")) {
            return "example";
        } else if (lowerContent.contains("summary") || lowerContent.contains("conclusion")) {
            return "summary";
        }
        return "concept";
    }
    
    private int calculateComplexityScore(String content) {
        int score = 0;
        String lowerContent = content.toLowerCase();

        String[] techTerms = {"algorithm", "implementation", "architecture", "framework", "optimization"};
        for (String term : techTerms) {
            if (lowerContent.contains(term)) score++;
        }

        score += (content.split("```").length - 1) / 2;

        score += content.length() / 1000;
        
        return Math.min(score, 10); // Cap at 10
    }
    
    
    public Map<String, String> getSessionContent() {
        return new HashMap<>(generatedContent);
    }
    
    
    public ContentVectorizer.ContentAnalysis getContentAnalysis() {
        return vectorizer.analyzeContent(sessionId);
    }
}
