package com.github.bhavuklabs.deepresearch.models;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.github.bhavuklabs.core.enums.OutputFormat;


public class PersonalizedMarkdownConfig {

    public static PersonalizedMarkdownConfig.Builder builder() {
        return new PersonalizedMarkdownConfig.Builder();
    }

    private final DeepResearchConfig baseConfig;

    private final int maxTopicsPerSection;
    private final int maxSubtopicsPerTopic;
    private final int targetWordsPerSubtopic;
    private final boolean enableHierarchicalBreakdown;
    private final boolean includeExamples;
    private final boolean includeDiagrams;
    private final boolean enableProgressiveDisclosure;

    private final String userExpertiseLevel; // beginner, intermediate, advanced, expert
    private final Set<String> userInterests;
    private final Map<String, Integer> domainKnowledge; // domain -> proficiency (1-10)
    private final List<String> preferredLearningStyle; // visual, textual, example-driven, theoretical
    private final boolean adaptComplexity;

    private final boolean useEnhancedLists;
    private final boolean includeCodeBlocks;
    private final boolean enableHorizontalRules;
    private final int maxHeadingDepth;
    private final boolean autoGenerateAnchors;

    private final boolean requirePracticalExamples;
    private final boolean includeBestPractices;
    private final boolean addTroubleshooting;
    private final boolean connectToRealWorld;
    private final int minExamplesPerTopic;

    private PersonalizedMarkdownConfig(Builder builder) {
        this.baseConfig = builder.baseConfig;
        this.maxTopicsPerSection = builder.maxTopicsPerSection;
        this.maxSubtopicsPerTopic = builder.maxSubtopicsPerTopic;
        this.targetWordsPerSubtopic = builder.targetWordsPerSubtopic;
        this.enableHierarchicalBreakdown = builder.enableHierarchicalBreakdown;
        this.includeExamples = builder.includeExamples;
        this.includeDiagrams = builder.includeDiagrams;
        this.enableProgressiveDisclosure = builder.enableProgressiveDisclosure;
        
        this.userExpertiseLevel = builder.userExpertiseLevel;
        this.userInterests = builder.userInterests;
        this.domainKnowledge = builder.domainKnowledge;
        this.preferredLearningStyle = builder.preferredLearningStyle;
        this.adaptComplexity = builder.adaptComplexity;
        
        this.useEnhancedLists = builder.useEnhancedLists;
        this.includeCodeBlocks = builder.includeCodeBlocks;
        this.enableHorizontalRules = builder.enableHorizontalRules;
        this.maxHeadingDepth = builder.maxHeadingDepth;
        this.autoGenerateAnchors = builder.autoGenerateAnchors;
        
        this.requirePracticalExamples = builder.requirePracticalExamples;
        this.includeBestPractices = builder.includeBestPractices;
        this.addTroubleshooting = builder.addTroubleshooting;
        this.connectToRealWorld = builder.connectToRealWorld;
        this.minExamplesPerTopic = builder.minExamplesPerTopic;
    }

    public DeepResearchConfig getBaseConfig() { return baseConfig; }
    public int getMaxTopicsPerSection() { return maxTopicsPerSection; }
    public int getMaxSubtopicsPerTopic() { return maxSubtopicsPerTopic; }
    public int getTargetWordsPerSubtopic() { return targetWordsPerSubtopic; }
    public boolean isHierarchicalBreakdownEnabled() { return enableHierarchicalBreakdown; }
    public boolean shouldIncludeExamples() { return includeExamples; }
    public boolean shouldIncludeDiagrams() { return includeDiagrams; }
    public boolean isProgressiveDisclosureEnabled() { return enableProgressiveDisclosure; }
    
    public String getUserExpertiseLevel() { return userExpertiseLevel; }
    public Set<String> getUserInterests() { return userInterests; }
    public Map<String, Integer> getDomainKnowledge() { return domainKnowledge; }
    public List<String> getPreferredLearningStyle() { return preferredLearningStyle; }
    public boolean shouldAdaptComplexity() { return adaptComplexity; }
    
    public boolean shouldUseEnhancedLists() { return useEnhancedLists; }
    public boolean shouldIncludeCodeBlocks() { return includeCodeBlocks; }
    public boolean shouldEnableHorizontalRules() { return enableHorizontalRules; }
    public int getMaxHeadingDepth() { return maxHeadingDepth; }
    public boolean shouldAutoGenerateAnchors() { return autoGenerateAnchors; }
    
    public boolean shouldRequirePracticalExamples() { return requirePracticalExamples; }
    public boolean shouldIncludeBestPractices() { return includeBestPractices; }
    public boolean shouldAddTroubleshooting() { return addTroubleshooting; }
    public boolean shouldConnectToRealWorld() { return connectToRealWorld; }
    public int getMinExamplesPerTopic() { return minExamplesPerTopic; }

    
    public static PersonalizedMarkdownConfig forBeginner(Set<String> interests) {
        return builder()
                .userExpertiseLevel("beginner")
                .userInterests(interests)
                .maxTopicsPerSection(3)
                .maxSubtopicsPerTopic(4)
                .targetWordsPerSubtopic(200)
                .enableHierarchicalBreakdown(true)
                .includeExamples(true)
                .requirePracticalExamples(true)
                .adaptComplexity(true)
                .useEnhancedLists(true)
                .maxHeadingDepth(3)
                .minExamplesPerTopic(2)
                .build();
    }

    
    public static PersonalizedMarkdownConfig forIntermediate(Set<String> interests, Map<String, Integer> domainKnowledge) {
        return builder()
                .userExpertiseLevel("intermediate")
                .userInterests(interests)
                .domainKnowledge(domainKnowledge)
                .maxTopicsPerSection(4)
                .maxSubtopicsPerTopic(5)
                .targetWordsPerSubtopic(300)
                .enableHierarchicalBreakdown(true)
                .includeExamples(true)
                .includeBestPractices(true)
                .adaptComplexity(true)
                .useEnhancedLists(true)
                .includeCodeBlocks(true)
                .maxHeadingDepth(4)
                .minExamplesPerTopic(1)
                .build();
    }

    
    public static PersonalizedMarkdownConfig forAdvanced(Set<String> interests, Map<String, Integer> domainKnowledge) {
        return builder()
                .userExpertiseLevel("advanced")
                .userInterests(interests)
                .domainKnowledge(domainKnowledge)
                .maxTopicsPerSection(5)
                .maxSubtopicsPerTopic(6)
                .targetWordsPerSubtopic(400)
                .enableHierarchicalBreakdown(true)
                .includeExamples(true)
                .includeDiagrams(true)
                .includeBestPractices(true)
                .addTroubleshooting(true)
                .connectToRealWorld(true)
                .useEnhancedLists(true)
                .includeCodeBlocks(true)
                .enableHorizontalRules(true)
                .maxHeadingDepth(5)
                .minExamplesPerTopic(1)
                .build();
    }

    public static class Builder {
        private DeepResearchConfig baseConfig;
        private int maxTopicsPerSection = 4;
        private int maxSubtopicsPerTopic = 5;
        private int targetWordsPerSubtopic = 250;
        private boolean enableHierarchicalBreakdown = true;
        private boolean includeExamples = true;
        private boolean includeDiagrams = false;
        private boolean enableProgressiveDisclosure = true;

        private String userExpertiseLevel = "intermediate";
        private Set<String> userInterests = Set.of();
        private Map<String, Integer> domainKnowledge = Map.of();
        private List<String> preferredLearningStyle = List.of("example-driven", "textual");
        private boolean adaptComplexity = true;

        private boolean useEnhancedLists = true;
        private boolean includeCodeBlocks = true;
        private boolean enableHorizontalRules = true;
        private int maxHeadingDepth = 4;
        private boolean autoGenerateAnchors = true;

        private boolean requirePracticalExamples = true;
        private boolean includeBestPractices = true;
        private boolean addTroubleshooting = false;
        private boolean connectToRealWorld = true;
        private int minExamplesPerTopic = 1;

        public Builder() {

            this.baseConfig = DeepResearchConfig.builder()
                 .researchDepth(DeepResearchConfig.ResearchDepth.COMPREHENSIVE)
                 .narrativeStyle(DeepResearchConfig.NarrativeStyle.TECHNICAL)
                 .maxSources(20)
                 .maxQuestions(8)
                 .maxRounds(3)
                 .maxProcessingTime(Duration.ofMinutes(10))
                 .enableQualityFiltering(true)
                 .enableCrossValidation(true)
                 .includeExecutiveSummary(true)
                 .outputFormat(OutputFormat.MARKDOWN)
                 .build();
        }

        public Builder baseConfig(DeepResearchConfig baseConfig) {
            this.baseConfig = baseConfig;
            return this;
        }

        public Builder maxTopicsPerSection(int maxTopicsPerSection) {
            this.maxTopicsPerSection = maxTopicsPerSection;
            return this;
        }

        public Builder maxSubtopicsPerTopic(int maxSubtopicsPerTopic) {
            this.maxSubtopicsPerTopic = maxSubtopicsPerTopic;
            return this;
        }

        public Builder targetWordsPerSubtopic(int targetWordsPerSubtopic) {
            this.targetWordsPerSubtopic = targetWordsPerSubtopic;
            return this;
        }

        public Builder enableHierarchicalBreakdown(boolean enableHierarchicalBreakdown) {
            this.enableHierarchicalBreakdown = enableHierarchicalBreakdown;
            return this;
        }

        public Builder includeExamples(boolean includeExamples) {
            this.includeExamples = includeExamples;
            return this;
        }

        public Builder includeDiagrams(boolean includeDiagrams) {
            this.includeDiagrams = includeDiagrams;
            return this;
        }

        public Builder enableProgressiveDisclosure(boolean enableProgressiveDisclosure) {
            this.enableProgressiveDisclosure = enableProgressiveDisclosure;
            return this;
        }

        public Builder userExpertiseLevel(String userExpertiseLevel) {
            this.userExpertiseLevel = userExpertiseLevel;
            return this;
        }

        public Builder userInterests(Set<String> userInterests) {
            this.userInterests = userInterests;
            return this;
        }

        public Builder domainKnowledge(Map<String, Integer> domainKnowledge) {
            this.domainKnowledge = domainKnowledge;
            return this;
        }

        public Builder preferredLearningStyle(List<String> preferredLearningStyle) {
            this.preferredLearningStyle = preferredLearningStyle;
            return this;
        }

        public Builder adaptComplexity(boolean adaptComplexity) {
            this.adaptComplexity = adaptComplexity;
            return this;
        }

        public Builder useEnhancedLists(boolean useEnhancedLists) {
            this.useEnhancedLists = useEnhancedLists;
            return this;
        }

        public Builder includeCodeBlocks(boolean includeCodeBlocks) {
            this.includeCodeBlocks = includeCodeBlocks;
            return this;
        }

        public Builder enableHorizontalRules(boolean enableHorizontalRules) {
            this.enableHorizontalRules = enableHorizontalRules;
            return this;
        }

        public Builder maxHeadingDepth(int maxHeadingDepth) {
            this.maxHeadingDepth = maxHeadingDepth;
            return this;
        }

        public Builder autoGenerateAnchors(boolean autoGenerateAnchors) {
            this.autoGenerateAnchors = autoGenerateAnchors;
            return this;
        }

        public Builder requirePracticalExamples(boolean requirePracticalExamples) {
            this.requirePracticalExamples = requirePracticalExamples;
            return this;
        }

        public Builder includeBestPractices(boolean includeBestPractices) {
            this.includeBestPractices = includeBestPractices;
            return this;
        }

        public Builder addTroubleshooting(boolean addTroubleshooting) {
            this.addTroubleshooting = addTroubleshooting;
            return this;
        }

        public Builder connectToRealWorld(boolean connectToRealWorld) {
            this.connectToRealWorld = connectToRealWorld;
            return this;
        }

        public Builder minExamplesPerTopic(int minExamplesPerTopic) {
            this.minExamplesPerTopic = minExamplesPerTopic;
            return this;
        }

        public PersonalizedMarkdownConfig build() {
            return new PersonalizedMarkdownConfig(this);
        }
    }
}
