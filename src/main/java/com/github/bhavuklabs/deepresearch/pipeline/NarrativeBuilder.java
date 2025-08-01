package com.github.bhavuklabs.deepresearch.pipeline;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.logging.Logger;

import com.github.bhavuklabs.citation.CitationResult;
import com.github.bhavuklabs.core.contracts.LLMClient;
import com.github.bhavuklabs.core.payloads.LLMResponse;
import com.github.bhavuklabs.deepresearch.context.DeepResearchContext;
import com.github.bhavuklabs.deepresearch.models.NarrativeSection;
import com.github.bhavuklabs.deepresearch.models.NarrativeStructure;
import com.github.bhavuklabs.deepresearch.models.ResearchQuestion;

public class NarrativeBuilder {

    private static final Logger logger = Logger.getLogger(NarrativeBuilder.class.getName());

    
    private final int TARGET_NARRATIVE_LENGTH = 8000; 
    private final int MAX_SECTION_LENGTH = 1200; 
    private final int CONTEXT_WINDOW_LIMIT = 32000; 
    private final double CHUNK_OVERLAP_RATIO = 0.15; 

    private final LLMClient llmClient;
    private final HierarchicalSynthesizer hierarchicalSynthesizer;
    private final ContextAwareChunker contextChunker;
    private final ExecutorService executorService;

    public NarrativeBuilder(LLMClient llmClient,
        HierarchicalSynthesizer hierarchicalSynthesizer,
        ExecutorService executorService) {
        this.llmClient = llmClient;
        this.hierarchicalSynthesizer = hierarchicalSynthesizer;
        this.contextChunker = new ContextAwareChunker(CONTEXT_WINDOW_LIMIT);
        this.executorService = executorService;
    }

    
    public String buildComprehensiveNarrative(DeepResearchContext context,
        String synthesizedKnowledge) {
        try {
            logger.info("Building comprehensive narrative for session: " + context.getSessionId());

            
            NarrativeStructure structure = planAdaptiveNarrativeStructure(context);

            
            List<ContextAwareChunker.ContentChunk> contentChunks = contextChunker.chunkContent(
                synthesizedKnowledge, context, structure);

            
            Map<String, String> sectionContents = generateSectionsInParallel(
                structure, contentChunks, context);

            
            String narrative = assembleProgressiveNarrative(structure, sectionContents, context);

            
            String enhancedNarrative = enhanceNarrativeCoherence(narrative, context);

            logger.info("Comprehensive narrative completed: " + enhancedNarrative.length() + " characters");
            return enhancedNarrative;

        } catch (Exception e) {
            logger.severe("Comprehensive narrative building failed: " + e.getMessage());
            return buildFallbackNarrative(context, synthesizedKnowledge);
        }
    }

    
    private NarrativeStructure planAdaptiveNarrativeStructure(DeepResearchContext context) {
        try {
            String structurePlanningPrompt = buildAdvancedStructurePlanningPrompt(context);

            
            List<ContextAwareChunker.ContextChunk> planningChunks = contextChunker.chunkPrompt(structurePlanningPrompt);

            NarrativeStructure structure = null;
            for (ContextAwareChunker.ContextChunk chunk : planningChunks) {
                LLMResponse<String> response = llmClient.complete(chunk.getContent(), String.class);
                structure = parseAndMergeStructure(response.structuredOutput(), structure, context);
            }

            return structure != null ? structure : createAdaptiveDefaultStructure(context);

        } catch (Exception e) {
            logger.warning("Advanced structure planning failed: " + e.getMessage());
            return createAdaptiveDefaultStructure(context);
        }
    }

    
    private String buildAdvancedStructurePlanningPrompt(DeepResearchContext context) {
        int researchComplexity = calculateResearchComplexity(context);
        String researchCategories = getUniqueResearchCategories(context);

        return String.format("""
            Plan a comprehensive research narrative for: "%s"
            
            RESEARCH ANALYSIS:
            - Complexity Score: %d/10
            - Questions Explored: %d
            - Sources Analyzed: %d
            - Research Categories: %s
            - Processing Depth: %s
            
            EXISTING INSIGHTS OVERVIEW:
            %s
            
            Create a detailed narrative structure (8000+ words) with:
            1. Adaptive section hierarchy based on research complexity
            2. Each section should be 1000-1500 words
            3. Focus on implementation details, case studies, and quantitative insights
            4. Ensure logical flow and seamless transitions
            5. Include technical specifications and real-world applications
            6. Prioritize actionable, evidence-based recommendations
            
            Structure Format:
            SECTION: [Title]
            FOCUS: [Specific focus area]
            TARGET_LENGTH: [Word count]
            PRIORITY: [High/Medium/Low]
            DEPENDENCIES: [Related sections]
            
            Plan the adaptive structure:
            """,
            context.getOriginalQuery(),
            researchComplexity,
            context.getResearchQuestions().size(),
            context.getAllCitations().size(),
            researchCategories,
            context.getConfig().getResearchDepth(),
            getCondensedInsightsSummary(context)
        );
    }

    
    private Map<String, String> generateSectionsInParallel(NarrativeStructure structure,
        List<ContextAwareChunker.ContentChunk> contentChunks,
        DeepResearchContext context) {
        try {
            List<CompletableFuture<Map.Entry<String, String>>> futures = structure.getSections()
                .stream()
                .map(section -> CompletableFuture.supplyAsync(() -> {
                    try {
                        String sectionContent = generateContextAwareSection(section, contentChunks, context);
                        return Map.entry(section.getTitle(), sectionContent);
                    } catch (Exception e) {
                        logger.warning("Section generation failed for: " + section.getTitle());
                        return Map.entry(section.getTitle(), buildFallbackSection(section, context));
                    }
                }, executorService))
                .collect(Collectors.toList());

            return futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        } catch (Exception e) {
            logger.warning("Parallel section generation failed: " + e.getMessage());
            return generateSectionsSequentially(structure, contentChunks, context);
        }
    }

    
    private String generateContextAwareSection(NarrativeSection section,
        List<ContextAwareChunker.ContentChunk> contentChunks,
        DeepResearchContext context) {
        try {
            
            List<ContextAwareChunker.ContentChunk> relevantChunks = filterRelevantChunks(section, contentChunks);

            
            StringBuilder sectionBuilder = new StringBuilder();

            for (ContextAwareChunker.ContentChunk chunk : relevantChunks) {
                String sectionPrompt = buildContextAwareSectionPrompt(section, chunk, context);

                
                if (countTokens(sectionPrompt) > CONTEXT_WINDOW_LIMIT) {
                    sectionPrompt = contextChunker.compressPrompt(sectionPrompt, CONTEXT_WINDOW_LIMIT);
                }

                LLMResponse<String> response = llmClient.complete(sectionPrompt, String.class);
                String chunkContent = response.structuredOutput();

                
                sectionBuilder.append(chunkContent).append("\n\n");
            }

            
            String rawSection = sectionBuilder.toString();
            return enhanceSectionCoherence(rawSection, section, context);

        } catch (Exception e) {
            logger.warning("Context-aware section generation failed: " + e.getMessage());
            return buildFallbackSection(section, context);
        }
    }

    
    private String buildContextAwareSectionPrompt(NarrativeSection section,
        ContextAwareChunker.ContentChunk chunk,
        DeepResearchContext context) {
        List<String> relevantInsights = getRelevantInsights(section, context);
        List<CitationResult> relevantCitations = getRelevantCitations(section, context);

        return String.format("""
            Write a comprehensive section: "%s"
            
            SECTION SPECIFICATIONS:
            - Focus: %s
            - Target Length: %d words
            - Priority: %s
            - Main Topic: %s
            
            RELEVANT CONTENT CHUNK:
            %s
            
            SUPPORTING INSIGHTS:
            %s
            
            AUTHORITATIVE SOURCES:
            %s
            
            WRITING REQUIREMENTS:
            1. Write exactly %d words of detailed, technical content
            2. Include specific examples, implementations, and quantitative data
            3. Use metrics, benchmarks, and performance indicators where available
            4. Maintain authoritative, professional tone throughout
            5. Ensure smooth logical flow and clear organization
            6. Include inline source references [1], [2], etc.
            7. Focus on actionable, practical information with evidence
            8. Avoid generic statements - be specific and data-driven
            9. Connect concepts to real-world applications and case studies
            10. Provide implementation guidance and best practices
            
            Generate the complete section content:
            """,
            section.getTitle(),
            section.getFocus(),
            section.getTargetLength(),
            section.getPriority(),
            context.getOriginalQuery(),
            chunk.getContent(),
            formatInsightsForSection(relevantInsights),
            formatCitationsForSection(relevantCitations),
            section.getTargetLength()
        );
    }

    
    private String assembleProgressiveNarrative(NarrativeStructure structure,
        Map<String, String> sectionContents,
        DeepResearchContext context) {
        StringBuilder narrative = new StringBuilder();

        
        narrative.append(buildExecutiveSummary(context, sectionContents));
        narrative.append("\n\n");

        
        for (int i = 0; i < structure.getSections().size(); i++) {
            NarrativeSection section = structure.getSections().get(i);
            String sectionContent = sectionContents.getOrDefault(section.getTitle(), "");

            
            narrative.append("## ").append(section.getTitle()).append("\n\n");
            narrative.append(sectionContent);

            
            if (i < structure.getSections().size() - 1) {
                String transition = generateIntelligentTransition(
                    section, structure.getSections().get(i + 1), context);
                narrative.append("\n\n").append(transition);
            }

            narrative.append("\n\n");
        }

        
        narrative.append(buildComprehensiveConclusion(context, sectionContents));
        narrative.append("\n\n");

        
        narrative.append(buildEnhancedBibliography(context));

        return narrative.toString();
    }

    
    private String enhanceNarrativeCoherence(String narrative, DeepResearchContext context) {
        try {
            
            if (narrative.length() < TARGET_NARRATIVE_LENGTH * 0.8) {
                logger.info("Narrative below target length, applying expansion enhancement");
                return expandNarrativeContent(narrative, context);
            }

            
            List<ContextAwareChunker.ContextChunk> narrativeChunks = contextChunker.chunkNarrative(narrative);
            StringBuilder enhancedNarrative = new StringBuilder();

            for (ContextAwareChunker.ContextChunk chunk : narrativeChunks) {
                String enhancedChunk = enhanceChunkCoherence(chunk, context);
                enhancedNarrative.append(enhancedChunk).append("\n");
            }

            return enhancedNarrative.toString();

        } catch (Exception e) {
            logger.warning("Narrative coherence enhancement failed: " + e.getMessage());
            return narrative;
        }
    }

    
    private int calculateResearchComplexity(DeepResearchContext context) {
        int complexity = 0;
        complexity += Math.min(context.getResearchQuestions().size() / 2, 3);
        complexity += Math.min(context.getAllCitations().size() / 20, 3);
        complexity += context.getAllInsights().size() > 10 ? 2 : 1;
        complexity += context.getConfig().getResearchDepth().ordinal() + 1;
        return Math.min(complexity, 10);
    }

    private String getUniqueResearchCategories(DeepResearchContext context) {
        return context.getResearchQuestions().stream()
            .map(ResearchQuestion::getCategory)
            .distinct()
            .collect(Collectors.joining(", "));
    }

    private String getCondensedInsightsSummary(DeepResearchContext context) {
        return context.getAllInsights().entrySet().stream()
            .limit(3)
            .map(entry -> "- " + truncate(entry.getValue(), 100))
            .collect(Collectors.joining("\n"));
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    private int countTokens(String text) {
        
        return text != null ? (int) Math.ceil(text.length() / 4.0) : 0;
    }

    
    private List<ContextAwareChunker.ContentChunk> filterRelevantChunks(NarrativeSection section, List<ContextAwareChunker.ContentChunk> allChunks) {
        
        return allChunks.stream().limit(5).collect(Collectors.toList());
    }

    private List<String> getRelevantInsights(NarrativeSection section, DeepResearchContext context) {
        
        return new ArrayList<>();
    }

    private List<CitationResult> getRelevantCitations(NarrativeSection section, DeepResearchContext context) {
        
        return new ArrayList<>();
    }

    private String formatInsightsForSection(List<String> insights) {
        return insights.stream()
            .limit(3)
            .map(insight -> "- " + truncate(insight, 250))
            .collect(Collectors.joining("\n"));
    }

    private String formatCitationsForSection(List<CitationResult> citations) {
        StringBuilder formatted = new StringBuilder();
        for (int i = 0; i < Math.min(citations.size(), 4); i++) {
            CitationResult citation = citations.get(i);
            formatted.append(String.format("[%d] %s: %s\n",
                i + 1, citation.getTitle(), truncate(citation.getContent(), 150)));
        }
        return formatted.toString();
    }

    
    private NarrativeStructure parseAndMergeStructure(String response, NarrativeStructure existing, DeepResearchContext context) {
        return existing != null ? existing : createAdaptiveDefaultStructure(context);
    }

    private NarrativeStructure createAdaptiveDefaultStructure(DeepResearchContext context) {
        List<NarrativeSection> sections = new ArrayList<>();
        sections.add(new NarrativeSection("Introduction", "Overview of the research topic", 800, "High"));
        sections.add(new NarrativeSection("Technical Analysis", "Technical deep dive", 1200, "High"));
        sections.add(new NarrativeSection("Implementation Guide", "Practical implementation", 1000, "Medium"));
        return new NarrativeStructure(sections);
    }

    private String buildFallbackNarrative(DeepResearchContext context, String synthesizedKnowledge) {
        return "# Fallback Narrative\n\n" + synthesizedKnowledge;
    }

    private Map<String, String> generateSectionsSequentially(NarrativeStructure structure, List<ContextAwareChunker.ContentChunk> contentChunks, DeepResearchContext context) {
        Map<String, String> sections = new HashMap<>();
        for (NarrativeSection section : structure.getSections()) {
            sections.put(section.getTitle(), "Generated content for " + section.getTitle());
        }
        return sections;
    }

    private String buildFallbackSection(NarrativeSection section, DeepResearchContext context) {
        return "This section covers " + section.getFocus() + " for the topic: " + context.getOriginalQuery();
    }

    private String enhanceSectionCoherence(String rawSection, NarrativeSection section, DeepResearchContext context) {
        return rawSection; 
    }

    private String buildExecutiveSummary(DeepResearchContext context, Map<String, String> sectionContents) {
        return "# Executive Summary\n\nComprehensive analysis of: " + context.getOriginalQuery();
    }

    private String generateIntelligentTransition(NarrativeSection current, NarrativeSection next, DeepResearchContext context) {
        return String.format("Having explored %s, we now examine %s...", current.getFocus(), next.getFocus());
    }

    private String buildComprehensiveConclusion(DeepResearchContext context, Map<String, String> sectionContents) {
        return "# Conclusion\n\nThis research provides comprehensive insights into " + context.getOriginalQuery();
    }

    private String buildEnhancedBibliography(DeepResearchContext context) {
        return "# References\n\n" + context.getAllCitations().size() + " sources analyzed.";
    }

    private String expandNarrativeContent(String narrative, DeepResearchContext context) {
        return narrative; 
    }

    private String enhanceChunkCoherence(ContextAwareChunker.ContextChunk chunk, DeepResearchContext context) {
        return chunk.getContent(); 
    }
}