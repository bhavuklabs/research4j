package com.github.bhavuklabs.pipeline.models;

import java.util.List;
import java.util.Map;

public class QueryAnalysis {

    public String intent;
    public int complexityScore;
    public List<String> topics;
    public boolean requiresCitations;
    public String estimatedTime;
    public String suggestedReasoning;

    public QueryAnalysis() {
    }

    public QueryAnalysis(String intent, int complexityScore, List<String> topics, 
                        boolean requiresCitations, String estimatedTime, String suggestedReasoning) {
        this.intent = intent;
        this.complexityScore = complexityScore;
        this.topics = topics;
        this.requiresCitations = requiresCitations;
        this.estimatedTime = estimatedTime;
        this.suggestedReasoning = suggestedReasoning;
    }

    public String getAnalysisType() {
        return "query_analysis";
    }

    public Map<String, Object> getAnalysisData() {
        return Map.of(
            "intent", intent != null ? intent : "research",
            "complexityScore", complexityScore,
            "topics", topics != null ? topics : List.of(),
            "requiresCitations", requiresCitations,
            "estimatedTime", estimatedTime != null ? estimatedTime : "unknown",
            "suggestedReasoning", suggestedReasoning != null ? suggestedReasoning : "chain_of_thought"
        );
    }
}
