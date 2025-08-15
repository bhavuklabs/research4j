package com.github.bhavuklabs.citation.service;

import static com.github.bhavuklabs.citation.enums.CitationSource.TAVILY;
import static com.github.bhavuklabs.citation.enums.CitationSource.GOOGLE_GEMINI;

import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;

import com.github.bhavuklabs.citation.CitationFetcher;
import com.github.bhavuklabs.citation.CitationResult;
import com.github.bhavuklabs.citation.config.CitationConfig;
import com.github.bhavuklabs.citation.gemini.GeminiCitationFetcher;
import com.github.bhavuklabs.citation.tavily.TavilyCitationFetcher;
import com.github.bhavuklabs.exceptions.citation.CitationException;


public class CitationService implements AutoCloseable {

    private static final Logger logger = Logger.getLogger(CitationService.class.getName());

    private final CitationFetcher primaryFetcher;
    private final CitationFetcher fallbackFetcher;
    private final CitationConfig config;
    private final String cseId;

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;
    private static final long RATE_LIMIT_MS = 500;

    private long lastRequestTime = 0;


    public CitationService(CitationConfig citationConfig) throws CitationException {
        this(citationConfig, null);
    }


    public CitationService(CitationConfig citationConfig, String cseId) throws CitationException {
        this.config = citationConfig;
        this.cseId = cseId;

        try {
            this.primaryFetcher = createPrimaryFetcher(citationConfig, cseId);

            this.fallbackFetcher = createFallbackFetcher(citationConfig, cseId);

            logger.info("CitationService initialized with primary source: " +
                citationConfig.getCitationSource() +
                (fallbackFetcher != null ? " and fallback support" : ""));

        } catch (Exception e) {
            throw new CitationException("Failed to initialize CitationService: " + e.getMessage(),
                e, "initialization", citationConfig.getCitationSource().toString());
        }
    }


    public List<CitationResult> search(String query) throws CitationException {
        validateQuery(query);

        applyRateLimit();

        logger.info("Searching for: " + truncateQuery(query));

        List<CitationResult> results = searchWithFetcher(primaryFetcher, query, "primary");

        if ((results.isEmpty() || results.size() < 3) && fallbackFetcher != null) {
            logger.info("Primary fetcher returned " + results.size() +
                " results, trying fallback fetcher");

            List<CitationResult> fallbackResults = searchWithFetcher(fallbackFetcher, query, "fallback");

            results = mergeResults(results, fallbackResults);
        }

        List<CitationResult> validatedResults = validateAndEnhanceResults(results, query);

        logger.info("Search completed: " + validatedResults.size() + " valid citations returned");
        return validatedResults;
    }


    private List<CitationResult> searchWithFetcher(CitationFetcher fetcher, String query, String fetcherType) {
        if (fetcher == null) {
            return new ArrayList<>();
        }

        int retryCount = 0;
        Exception lastException = null;

        while (retryCount < MAX_RETRIES) {
            try {
                logger.fine("Attempting search with " + fetcherType + " fetcher (attempt " + (retryCount + 1) + ")");

                List<CitationResult> results = fetcher.fetch(query);

                if (results != null && !results.isEmpty()) {
                    logger.info(fetcherType + " fetcher returned " + results.size() + " results");
                    return results;
                }

                logger.warning(fetcherType + " fetcher returned empty results on attempt " + (retryCount + 1));

            } catch (CitationException e) {
                lastException = e;
                logger.warning(fetcherType + " fetcher failed on attempt " + (retryCount + 1) + ": " + e.getMessage());

                if (e.getMessage().contains("API key") || e.getMessage().contains("authentication")) {
                    logger.severe("Authentication error with " + fetcherType + " fetcher, not retrying");
                    break;
                }

            } catch (Exception e) {
                lastException = new CitationException("Unexpected error in " + fetcherType + " fetcher",
                    e, query, fetcherType);
                logger.warning("Unexpected error with " + fetcherType + " fetcher: " + e.getMessage());
            }

            retryCount++;

            if (retryCount < MAX_RETRIES) {
                try {
                    long delay = RETRY_DELAY_MS * (long) Math.pow(2, retryCount - 1);
                    logger.fine("Waiting " + delay + "ms before retry");
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        if (lastException != null) {
            logger.warning("All " + MAX_RETRIES + " attempts failed for " + fetcherType + " fetcher: " +
                lastException.getMessage());
        }

        return new ArrayList<>();
    }


    private List<CitationResult> mergeResults(List<CitationResult> primaryResults,
        List<CitationResult> fallbackResults) {
        List<CitationResult> merged = new ArrayList<>(primaryResults);

        for (CitationResult fallbackResult : fallbackResults) {
            boolean isDuplicate = primaryResults.stream()
                .anyMatch(primary -> areDuplicates(primary, fallbackResult));

            if (!isDuplicate) {

                merged.add(fallbackResult);
            }
        }

        logger.info("Merged results: " + primaryResults.size() + " primary + " +
            (merged.size() - primaryResults.size()) + " unique fallback = " + merged.size() + " total");

        return merged;
    }


    private boolean areDuplicates(CitationResult result1, CitationResult result2) {
        if (result1 == null || result2 == null) {
            return false;
        }

        String url1 = result1.getUrl();
        String url2 = result2.getUrl();
        if (url1 != null && url2 != null && url1.equals(url2)) {
            return true;
        }

        String title1 = result1.getTitle();
        String title2 = result2.getTitle();
        if (title1 != null && title2 != null) {
            double similarity = calculateStringSimilarity(title1, title2);
            if (similarity > 0.8) {
                return true;
            }
        }

        return false;
    }


    private double calculateStringSimilarity(String str1, String str2) {
        if (str1 == null || str2 == null) {
            return 0.0;
        }

        String[] words1 = str1.toLowerCase().split("\\W+");
        String[] words2 = str2.toLowerCase().split("\\W+");

        java.util.Set<String> set1 = new java.util.HashSet<>(java.util.Arrays.asList(words1));
        java.util.Set<String> set2 = new java.util.HashSet<>(java.util.Arrays.asList(words2));

        java.util.Set<String> intersection = new java.util.HashSet<>(set1);
        intersection.retainAll(set2);

        java.util.Set<String> union = new java.util.HashSet<>(set1);
        union.addAll(set2);

        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }


    private List<CitationResult> validateAndEnhanceResults(List<CitationResult> results, String query) {
        if (results == null) {
            return new ArrayList<>();
        }

        return results.stream()
            .filter(this::isValidResult)
            .map(result -> enhanceResult(result, query))
            .filter(result -> result.getRelevanceScore() >= 0.2) // Minimum relevance threshold
            .sorted((r1, r2) -> Double.compare(r2.getRelevanceScore(), r1.getRelevanceScore()))
            .limit(25) // Reasonable limit for deep research
            .collect(java.util.stream.Collectors.toList());
    }


    private boolean isValidResult(CitationResult result) {
        if (result == null || !result.isValid()) {
            return false;
        }

        if (result.getContent() == null || result.getContent().trim().length() < 50) {
            return false;
        }

        String url = result.getUrl();
        if (url == null || url.trim().isEmpty()) {
            return false;
        }

        if (url.contains("javascript:") || url.startsWith("mailto:") || url.startsWith("tel:")) {
            return false;
        }

        String title = result.getTitle() != null ? result.getTitle().toLowerCase() : "";
        String content = result.getContent().toLowerCase();

        String[] spamIndicators = {"click here", "buy now", "limited time", "act now", "free money"};
        for (String indicator : spamIndicators) {
            if (title.contains(indicator) || content.contains(indicator)) {
                return false;
            }
        }

        return true;
    }


    private CitationResult enhanceResult(CitationResult result, String query) {
        double enhancedScore = calculateEnhancedRelevance(result, query);
        result.setRelevanceScore(enhancedScore);

        String domain = result.getDomain();
        if (domain != null) {
            result.setDomain(classifyDomain(domain));
        }

        return result;
    }


    private double calculateEnhancedRelevance(CitationResult result, String query) {
        double baseScore = result.getRelevanceScore();
        double enhancement = 0.0;

        String queryLower = query.toLowerCase();
        String titleLower = result.getTitle() != null ? result.getTitle().toLowerCase() : "";
        String contentLower = result.getContent() != null ? result.getContent().toLowerCase() : "";

        if (titleLower.contains(queryLower)) {
            enhancement += 0.2;
        } else {

            String[] queryWords = queryLower.split("\\s+");
            long titleMatches = java.util.Arrays.stream(queryWords)
                .mapToLong(word -> titleLower.contains(word) ? 1 : 0)
                .sum();
            enhancement += (titleMatches / (double) queryWords.length) * 0.1;
        }

        String[] queryWords = queryLower.split("\\s+");
        long contentMatches = java.util.Arrays.stream(queryWords)
            .mapToLong(word -> contentLower.contains(word) ? 1 : 0)
            .sum();
        enhancement += (contentMatches / (double) queryWords.length) * 0.1;

        String domain = result.getDomain();
        if (domain != null) {
            enhancement += getDomainAuthorityBoost(domain);
        }

        int contentLength = result.getContent() != null ? result.getContent().length() : 0;
        if (contentLength > 1000) {
            enhancement += 0.05;
        } else if (contentLength > 500) {
            enhancement += 0.02;
        }

        if (result.getRetrievedAt() != null) {
            java.time.Duration timeSinceRetrieval = java.time.Duration.between(
                result.getRetrievedAt(), java.time.LocalDateTime.now());
            if (timeSinceRetrieval.toDays() < 30) {
                enhancement += 0.03;
            }
        }

        return Math.min(1.0, Math.max(0.0, baseScore + enhancement));
    }


    private double getDomainAuthorityBoost(String domain) {
        String domainLower = domain.toLowerCase();

        if (domainLower.contains("wikipedia") || domainLower.contains(".edu") ||
            domainLower.contains(".gov") || domainLower.contains(".org")) {
            return 0.15;
        }

        if (domainLower.contains("github") || domainLower.contains("stackoverflow") ||
            domainLower.contains("medium") || domainLower.contains("arxiv")) {
            return 0.1;
        }

        if (domainLower.contains("reuters") || domainLower.contains("bbc") ||
            domainLower.contains("nature") || domainLower.contains("science")) {
            return 0.08;
        }

        if (domainLower.startsWith("https://")) {
            return 0.02;
        }

        return 0.0;
    }


    private String classifyDomain(String domain) {
        String domainLower = domain.toLowerCase();

        if (domainLower.contains(".edu")) return "educational";
        if (domainLower.contains(".gov")) return "government";
        if (domainLower.contains(".org")) return "organization";
        if (domainLower.contains("wikipedia")) return "encyclopedia";
        if (domainLower.contains("github")) return "code_repository";
        if (domainLower.contains("stackoverflow")) return "q_and_a";
        if (domainLower.contains("medium") || domainLower.contains("blog")) return "blog";
        if (domainLower.contains("news") || domainLower.contains("reuters") || domainLower.contains("bbc")) return "news";
        if (domainLower.contains("arxiv") || domainLower.contains("research")) return "research";

        return "general";
    }


    private CitationFetcher createPrimaryFetcher(CitationConfig config, String cseId) throws CitationException {
        switch (config.getCitationSource()) {
            case TAVILY:
                return new TavilyCitationFetcher(config.getApiKey());

            case GOOGLE_GEMINI:
                if (cseId == null || cseId.trim().isEmpty()) {
                    throw new CitationException("Google CSE ID required for Google Gemini citation source",
                        null, "configuration", "GOOGLE_GEMINI");
                }
                return new GeminiCitationFetcher(config.getApiKey(), cseId);

            default:
                throw new CitationException("Unsupported primary citation source: " + config.getCitationSource(),
                    null, "configuration", config.getCitationSource().toString());
        }
    }


    private CitationFetcher createFallbackFetcher(CitationConfig config, String cseId) {
        try {

            if (config.getCitationSource() == TAVILY && cseId != null && !cseId.trim().isEmpty()) {

                logger.info("Fallback to Google Search not implemented (missing API key configuration)");
                return null;
            }

            if (config.getCitationSource() == GOOGLE_GEMINI) {

                logger.info("Fallback to Tavily not implemented (missing API key configuration)");
                return null;
            }

        } catch (Exception e) {
            logger.warning("Failed to create fallback fetcher: " + e.getMessage());
        }

        return null;
    }


    private void applyRateLimit() {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastRequest = currentTime - lastRequestTime;

        if (timeSinceLastRequest < RATE_LIMIT_MS) {
            try {
                long sleepTime = RATE_LIMIT_MS - timeSinceLastRequest;
                logger.fine("Rate limiting: sleeping for " + sleepTime + "ms");
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warning("Rate limiting interrupted");
            }
        }

        lastRequestTime = System.currentTimeMillis();
    }


    private void validateQuery(String query) throws CitationException {
        if (query == null || query.trim().isEmpty()) {
            throw new CitationException("Search query cannot be null or empty",
                null, query, "validation");
        }

        if (query.length() > 1000) {
            throw new CitationException("Search query too long (max 1000 characters)",
                null, query, "validation");
        }
    }


    private String truncateQuery(String query) {
        if (query == null) return "null";
        return query.length() > 100 ? query.substring(0, 100) + "..." : query;
    }


    public boolean isHealthy() {
        try {

            List<CitationResult> testResults = search("test");
            return true; // If no exception thrown, service is healthy
        } catch (Exception e) {
            logger.warning("Health check failed: " + e.getMessage());
            return false;
        }
    }

    
    public ServiceStats getStats() {
        return new ServiceStats(
            config.getCitationSource().toString(),
            fallbackFetcher != null,
            lastRequestTime > 0
        );
    }

    
    @Override
    public void close() {
        try {
            if (primaryFetcher instanceof AutoCloseable) {
                ((AutoCloseable) primaryFetcher).close();
            }
            if (fallbackFetcher instanceof AutoCloseable) {
                ((AutoCloseable) fallbackFetcher).close();
            }
            logger.info("CitationService closed successfully");
        } catch (Exception e) {
            logger.warning("Error closing CitationService: " + e.getMessage());
        }
    }

    
    public static class ServiceStats {
        private final String primarySource;
        private final boolean hasFallback;
        private final boolean hasBeenUsed;

        public ServiceStats(String primarySource, boolean hasFallback, boolean hasBeenUsed) {
            this.primarySource = primarySource;
            this.hasFallback = hasFallback;
            this.hasBeenUsed = hasBeenUsed;
        }

        public String getPrimarySource() { return primarySource; }
        public boolean hasFallback() { return hasFallback; }
        public boolean hasBeenUsed() { return hasBeenUsed; }

        @Override
        public String toString() {
            return String.format("ServiceStats{primary=%s, fallback=%s, used=%s}",
                primarySource, hasFallback, hasBeenUsed);
        }
    }
}