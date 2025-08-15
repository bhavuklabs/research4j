package com.github.bhavuklabs.deepresearch.pipeline;

import java.util.ArrayList;
import java.util.List;

import com.github.bhavuklabs.core.contracts.LLMClient;
import com.github.bhavuklabs.core.payloads.LLMResponse;
import com.github.bhavuklabs.deepresearch.models.PersonalizedMarkdownConfig;
import com.github.bhavuklabs.deepresearch.models.DeepResearchResult;
import com.github.bhavuklabs.citation.CitationResult;


public class PersonalizedMarkdownBuilder {

    private final LLMClient llmClient;

    public PersonalizedMarkdownBuilder(LLMClient llmClient) {
        this.llmClient = llmClient;
    }

    
    public String buildPersonalizedContent(DeepResearchResult researchResult, PersonalizedMarkdownConfig config) {
        StringBuilder markdown = new StringBuilder();

        addTitle(markdown, researchResult.getOriginalQuery());
        addExecutiveSummary(markdown, researchResult, config);

        if (config.shouldEnableHorizontalRules()) {
            markdown.append("\n---\n\n");
        }

        buildMainContent(markdown, researchResult, config);

        if (config.shouldRequirePracticalExamples()) {
            addPracticalExamples(markdown, researchResult, config);
        }

        if (config.shouldIncludeBestPractices()) {
            addBestPractices(markdown, researchResult, config);
        }

        if (config.shouldAddTroubleshooting() && isAdvancedUser(config)) {
            addTroubleshootingSection(markdown, researchResult, config);
        }

        addReferences(markdown, researchResult.getAllCitations());

        return markdown.toString();
    }

    private void addTitle(StringBuilder markdown, String query) {
        String title = formatAsTitle(query);
        markdown.append("# ").append(title).append("\n\n");
    }

    private void addExecutiveSummary(StringBuilder markdown, DeepResearchResult researchResult, PersonalizedMarkdownConfig config) {
        markdown.append("## Executive Summary\n\n");
        
        String summary = generatePersonalizedSummary(researchResult, config);
        markdown.append(summary).append("\n\n");

        if (config.shouldUseEnhancedLists()) {
            markdown.append("### Key Takeaways\n\n");
            List<String> keyPoints = extractKeyPoints(researchResult, config);
            for (String point : keyPoints) {
                markdown.append("- **").append(point.split(":")[0]).append("**: ")
                       .append(point.split(":", 2)[1].trim()).append("\n");
            }
            markdown.append("\n");
        }
    }

    private void buildMainContent(StringBuilder markdown, DeepResearchResult researchResult, PersonalizedMarkdownConfig config) {
        List<ContentSection> sections = organizeContentSections(researchResult, config);
        
        for (ContentSection section : sections) {
            addContentSection(markdown, section, config);
        }
    }

    private void addContentSection(StringBuilder markdown, ContentSection section, PersonalizedMarkdownConfig config) {

        markdown.append("## ").append(section.getTitle()).append("\n\n");

        markdown.append(section.getIntroduction()).append("\n\n");

        for (ContentSubsection subsection : section.getSubsections()) {
            addSubsection(markdown, subsection, config);
        }

        if (config.shouldEnableHorizontalRules()) {
            markdown.append("---\n\n");
        }
    }

    private void addSubsection(StringBuilder markdown, ContentSubsection subsection, PersonalizedMarkdownConfig config) {

        markdown.append("### ").append(subsection.getTitle()).append("\n\n");

        if (config.shouldUseEnhancedLists() && subsection.hasStructuredContent()) {
            for (String point : subsection.getStructuredPoints()) {
                if (point.contains(":")) {
                    String[] parts = point.split(":", 2);
                    markdown.append("- **").append(parts[0].trim()).append("**: ")
                           .append(parts[1].trim()).append("\n");
                } else {
                    markdown.append("- ").append(point).append("\n");
                }
            }
            markdown.append("\n");
        } else {
            markdown.append(subsection.getContent()).append("\n\n");
        }

        if (config.shouldIncludeCodeBlocks() && subsection.hasCodeExample()) {
            addCodeBlock(markdown, subsection.getCodeExample(), subsection.getCodeLanguage());
        }

        if (config.shouldRequirePracticalExamples() && subsection.hasExample()) {
            addPracticalExample(markdown, subsection.getExample(), config);
        }
    }

    private void addCodeBlock(StringBuilder markdown, String code, String language) {
        markdown.append("```").append(language != null ? language : "").append("\n");
        markdown.append(code).append("\n");
        markdown.append("```\n\n");
    }

    private void addPracticalExample(StringBuilder markdown, String example, PersonalizedMarkdownConfig config) {
        markdown.append("#### Example\n\n");
        markdown.append(example).append("\n\n");
    }

    private void addPracticalExamples(StringBuilder markdown, DeepResearchResult researchResult, PersonalizedMarkdownConfig config) {
        markdown.append("## Practical Examples\n\n");
        
        List<String> examples = generatePersonalizedExamples(researchResult, config);
        
        for (int i = 0; i < examples.size(); i++) {
            markdown.append("### Example ").append(i + 1).append("\n\n");
            markdown.append(examples.get(i)).append("\n\n");
        }
    }

    private void addBestPractices(StringBuilder markdown, DeepResearchResult researchResult, PersonalizedMarkdownConfig config) {
        markdown.append("## Best Practices\n\n");
        
        List<String> practices = extractBestPractices(researchResult, config);
        
        if (config.shouldUseEnhancedLists()) {
            for (String practice : practices) {
                if (practice.contains(":")) {
                    String[] parts = practice.split(":", 2);
                    markdown.append("- **").append(parts[0].trim()).append("**: ")
                           .append(parts[1].trim()).append("\n");
                } else {
                    markdown.append("- ").append(practice).append("\n");
                }
            }
        } else {
            for (String practice : practices) {
                markdown.append("- ").append(practice).append("\n");
            }
        }
        
        markdown.append("\n");
    }

    private void addTroubleshootingSection(StringBuilder markdown, DeepResearchResult researchResult, PersonalizedMarkdownConfig config) {
        markdown.append("## Troubleshooting\n\n");
        
        List<String> troubleshooting = generateTroubleshootingGuide(researchResult, config);
        
        for (String issue : troubleshooting) {
            if (issue.contains(":")) {
                String[] parts = issue.split(":", 2);
                markdown.append("- **").append(parts[0].trim()).append("**: ")
                       .append(parts[1].trim()).append("\n");
            } else {
                markdown.append("- ").append(issue).append("\n");
            }
        }
        
        markdown.append("\n");
    }

    private void addReferences(StringBuilder markdown, List<CitationResult> citations) {
        if (citations == null || citations.isEmpty()) {
            return;
        }

        markdown.append("## References\n\n");
        
        for (int i = 0; i < citations.size(); i++) {
            CitationResult citation = citations.get(i);
            markdown.append(i + 1).append(". ")
                    .append(citation.getTitle() != null ? citation.getTitle() : "Source")
                    .append(" - ")
                    .append(citation.getUrl())
                    .append("\n");
        }
    }


    private String formatAsTitle(String query) {

        return query.substring(0, 1).toUpperCase() + query.substring(1);
    }

    private String generatePersonalizedSummary(DeepResearchResult researchResult, PersonalizedMarkdownConfig config) {
        String prompt = buildSummaryPrompt(researchResult, config);
        
        try {
            LLMResponse<String> response = llmClient.complete(prompt, String.class);
            return response.structuredOutput() != null ? response.structuredOutput() : response.rawText();
        } catch (Exception e) {

            return "This research provides comprehensive insights into " + researchResult.getOriginalQuery() + 
                   " tailored for " + config.getUserExpertiseLevel() + "-level understanding.";
        }
    }

    private String buildSummaryPrompt(DeepResearchResult researchResult, PersonalizedMarkdownConfig config) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Create a personalized executive summary for a ")
              .append(config.getUserExpertiseLevel())
              .append("-level user interested in: ")
              .append(String.join(", ", config.getUserInterests()))
              .append("\n\n");
        
        prompt.append("Original query: ").append(researchResult.getOriginalQuery()).append("\n");
        prompt.append("Research findings: ").append(researchResult.getFinalReport()).append("\n\n");
        
        prompt.append("Generate a ").append(config.getTargetWordsPerSubtopic())
              .append("-word summary that:\n");
        prompt.append("- Uses accessible language for ").append(config.getUserExpertiseLevel()).append(" level\n");
        prompt.append("- Focuses on practical applications\n");
        prompt.append("- Connects to user interests: ").append(String.join(", ", config.getUserInterests())).append("\n");
        
        return prompt.toString();
    }

    private List<String> extractKeyPoints(DeepResearchResult researchResult, PersonalizedMarkdownConfig config) {

        List<String> keyPoints = new ArrayList<>();


        keyPoints.add("Core Concept: " + extractCoreConcept(researchResult));
        keyPoints.add("Key Benefits: " + extractKeyBenefits(researchResult));
        keyPoints.add("Implementation: " + extractImplementationGuidance(researchResult));
        
        if (config.shouldIncludeBestPractices()) {
            keyPoints.add("Best Practices: " + extractTopPractice(researchResult));
        }
        
        return keyPoints.subList(0, Math.min(keyPoints.size(), config.getMaxTopicsPerSection()));
    }

    private List<ContentSection> organizeContentSections(DeepResearchResult researchResult, PersonalizedMarkdownConfig config) {
        List<ContentSection> sections = new ArrayList<>();


        sections.add(createOverviewSection(researchResult, config));
        sections.add(createDetailsSection(researchResult, config));
        
        if (config.shouldConnectToRealWorld()) {
            sections.add(createApplicationsSection(researchResult, config));
        }
        
        return sections;
    }

    private ContentSection createOverviewSection(DeepResearchResult researchResult, PersonalizedMarkdownConfig config) {
        return new ContentSection(
            "Overview",
            "This section provides a comprehensive overview of the topic.",
            createSubsections(researchResult, config, "overview")
        );
    }

    private ContentSection createDetailsSection(DeepResearchResult researchResult, PersonalizedMarkdownConfig config) {
        return new ContentSection(
            "Detailed Analysis",
            "This section delves deeper into the technical details and implementation aspects.",
            createSubsections(researchResult, config, "details")
        );
    }

    private ContentSection createApplicationsSection(DeepResearchResult researchResult, PersonalizedMarkdownConfig config) {
        return new ContentSection(
            "Real-World Applications",
            "This section explores practical applications and use cases.",
            createSubsections(researchResult, config, "applications")
        );
    }

    private List<ContentSubsection> createSubsections(DeepResearchResult researchResult, PersonalizedMarkdownConfig config, String type) {
        List<ContentSubsection> subsections = new ArrayList<>();


        for (int i = 0; i < Math.min(config.getMaxSubtopicsPerTopic(), 3); i++) {
            subsections.add(new ContentSubsection(
                type + " Subsection " + (i + 1),
                "Detailed content for this subsection...",
                config.shouldIncludeCodeBlocks(),
                config.shouldRequirePracticalExamples()
            ));
        }
        
        return subsections;
    }

    private List<String> generatePersonalizedExamples(DeepResearchResult researchResult, PersonalizedMarkdownConfig config) {
        List<String> examples = new ArrayList<>();
        
        for (int i = 0; i < config.getMinExamplesPerTopic(); i++) {
            examples.add("Practical example " + (i + 1) + " tailored for " + config.getUserExpertiseLevel() + " level users.");
        }
        
        return examples;
    }

    private List<String> extractBestPractices(DeepResearchResult researchResult, PersonalizedMarkdownConfig config) {
        List<String> practices = new ArrayList<>();
        
        practices.add("Follow Industry Standards: Adhere to established conventions and best practices");
        practices.add("Performance Optimization: Consider performance implications in implementation");
        practices.add("Security Considerations: Implement appropriate security measures");
        
        return practices;
    }

    private List<String> generateTroubleshootingGuide(DeepResearchResult researchResult, PersonalizedMarkdownConfig config) {
        List<String> troubleshooting = new ArrayList<>();
        
        troubleshooting.add("Common Issue 1: Description and solution");
        troubleshooting.add("Performance Problems: Optimization strategies");
        troubleshooting.add("Configuration Errors: Setup and configuration guidance");
        
        return troubleshooting;
    }

    private boolean isAdvancedUser(PersonalizedMarkdownConfig config) {
        return "advanced".equals(config.getUserExpertiseLevel()) || 
               "expert".equals(config.getUserExpertiseLevel());
    }

    private String extractCoreConcept(DeepResearchResult researchResult) {
        return "Core concept extracted from research";
    }

    private String extractKeyBenefits(DeepResearchResult researchResult) {
        return "Key benefits identified in research";
    }

    private String extractImplementationGuidance(DeepResearchResult researchResult) {
        return "Implementation guidance from research";
    }

    private String extractTopPractice(DeepResearchResult researchResult) {
        return "Top practice recommendation";
    }

    
    private static class ContentSection {
        private final String title;
        private final String introduction;
        private final List<ContentSubsection> subsections;

        public ContentSection(String title, String introduction, List<ContentSubsection> subsections) {
            this.title = title;
            this.introduction = introduction;
            this.subsections = subsections;
        }

        public String getTitle() { return title; }
        public String getIntroduction() { return introduction; }
        public List<ContentSubsection> getSubsections() { return subsections; }
    }

    private static class ContentSubsection {
        private final String title;
        private final String content;
        private final boolean hasCodeExample;
        private final boolean hasExample;
        private final List<String> structuredPoints;

        public ContentSubsection(String title, String content, boolean hasCodeExample, boolean hasExample) {
            this.title = title;
            this.content = content;
            this.hasCodeExample = hasCodeExample;
            this.hasExample = hasExample;
            this.structuredPoints = new ArrayList<>();

            if (hasExample) {
                structuredPoints.add("Key Feature: Primary functionality explanation");
                structuredPoints.add("Use Case: Practical application scenario");
                structuredPoints.add("Benefits: Advantages and value proposition");
            }
        }

        public String getTitle() { return title; }
        public String getContent() { return content; }
        public boolean hasCodeExample() { return hasCodeExample; }
        public boolean hasExample() { return hasExample; }
        public boolean hasStructuredContent() { return !structuredPoints.isEmpty(); }
        public List<String> getStructuredPoints() { return structuredPoints; }
        public String getCodeExample() { return "// Example code implementation\nclass Example {\n    // Implementation details\n}"; }
        public String getCodeLanguage() { return "java"; }
        public String getExample() { return "Practical example demonstrating the concept with real-world application."; }
    }
}
