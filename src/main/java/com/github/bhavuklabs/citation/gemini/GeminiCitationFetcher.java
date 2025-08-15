package com.github.bhavuklabs.citation.gemini;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchResults;
import dev.langchain4j.web.search.google.customsearch.GoogleCustomWebSearchEngine;
import com.github.bhavuklabs.citation.CitationFetcher;
import com.github.bhavuklabs.citation.CitationResult;
import com.github.bhavuklabs.config.Research4jConfig;
import com.github.bhavuklabs.exceptions.citation.CitationException;

public class GeminiCitationFetcher implements CitationFetcher, AutoCloseable {

    private static final Logger logger = Logger.getLogger(GeminiCitationFetcher.class.getName());
    private static final Duration DEFAULT_HTTP_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final int MAX_CONTENT_LENGTH = 15000;
    private static final int MIN_CONTENT_LENGTH = 200;
    private static final int MAX_RESULTS_PER_SEARCH = 50;
    private static final int MAX_TOTAL_SEARCHES = 10;
    private static final double QUALITY_THRESHOLD = 0.6;
    private static final double DIVERSITY_THRESHOLD = 0.7;

    private final WebSearchEngine webSearchEngine;
    private final HttpClient httpClient;
    private final ExecutorService executor;
    private final Duration httpTimeout;
    private final int maxResultsPerSearch;
    private volatile boolean closed = false;

    public GeminiCitationFetcher(String apiKey, String cseId) throws CitationException {
        this(apiKey, cseId, DEFAULT_HTTP_TIMEOUT, MAX_RESULTS_PER_SEARCH);
    }

    public GeminiCitationFetcher(String apiKey, String cseId, Duration httpTimeout, int maxResultsPerSearch) throws CitationException {
        validateInputs(apiKey, cseId);

        this.httpTimeout = httpTimeout != null ? httpTimeout : DEFAULT_HTTP_TIMEOUT;
        this.maxResultsPerSearch = Math.min(Math.max(maxResultsPerSearch, 10), MAX_RESULTS_PER_SEARCH);

        try {
            this.webSearchEngine = GoogleCustomWebSearchEngine.builder()
                .apiKey(apiKey)
                .csi(cseId)
                .includeImages(false)
                .logRequests(false)
                .logResponses(false)
                .build();

            this.httpClient = HttpClient.newBuilder()
                .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

            this.executor = Executors.newVirtualThreadPerTaskExecutor();

            logger.info("Enhanced GeminiCitationFetcher initialized with unlimited capabilities");

        } catch (Exception e) {
            throw new CitationException("Failed to initialize enhanced Gemini citation fetcher: " + e.getMessage(), e, "initialization", "GEMINI");
        }
    }

    public GeminiCitationFetcher(Research4jConfig config) throws CitationException {
        this(config.getGoogleSearchApiKey(), config.getGoogleCseId(), config.getRequestTimeout(), MAX_RESULTS_PER_SEARCH);
    }

    @Override
    public List<CitationResult> fetch(String query) throws CitationException {
        if (closed) {
            throw new IllegalStateException("Citation fetcher has been closed");
        }

        validateQuery(query);

        try {
            logger.info("Starting unlimited citation fetch for query: " + truncateString(query, 100));
            List<CitationResult> allResults = executeComprehensiveFetch(query);
            List<CitationResult> optimizedResults = optimizeResultSet(allResults, query);

            logger.info("Unlimited citation fetch completed - gathered " + allResults.size() + " total results, optimized to " + optimizedResults.size() +
                " high-quality citations");

            return optimizedResults;

        } catch (Exception e) {
            logger.severe("Enhanced citation fetch failed: " + e.getMessage());
            throw new CitationException("Citation fetch failed: " + e.getMessage(), e, query, "GEMINI");
        }
    }

    private List<CitationResult> executeComprehensiveFetch(String originalQuery) throws Exception {
        List<CitationResult> allResults = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();

        List<String> searchQueries = generateSearchVariations(originalQuery);
        logger.info("Generated " + searchQueries.size() + " search variations for comprehensive coverage");

        int searchRound = 0;
        boolean shouldContinue = true;
        QualityMetrics lastMetrics = new QualityMetrics();

        while (shouldContinue && searchRound < MAX_TOTAL_SEARCHES && searchRound < searchQueries.size()) {
            searchRound++;
            String currentQuery = searchQueries.get(searchRound - 1);

            logger.info("Executing search round " + searchRound + "/" + MAX_TOTAL_SEARCHES + " with query: " + truncateString(currentQuery, 80));
            List<CitationResult> roundResults = executeSingleSearchRound(currentQuery, originalQuery, seenUrls);

            int addedCount = 0;
            for (CitationResult result : roundResults) {
                if (result != null && result.isValid() && !seenUrls.contains(result.getUrl())) {
                    seenUrls.add(result.getUrl());
                    allResults.add(result);
                    addedCount++;
                }
            }

            logger.info("Search round " + searchRound + " completed - added " + addedCount + " new citations (total: " + allResults.size() + ")");
            QualityMetrics currentMetrics = assessQualityMetrics(allResults, originalQuery);
            shouldContinue = shouldContinueSearching(currentMetrics, lastMetrics, searchRound, allResults.size());
            lastMetrics = currentMetrics;

            if (shouldContinue && searchRound < MAX_TOTAL_SEARCHES) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread()
                        .interrupt();
                    break;
                }
            }
        }

        logger.info("Comprehensive fetch completed after " + searchRound + " rounds with " + allResults.size() + " total citations");
        return allResults;
    }

    private List<String> generateSearchVariations(String originalQuery) {
        List<String> variations = new ArrayList<>();

        variations.add(originalQuery);

        variations.add("\"" + originalQuery + "\"");
        variations.add(originalQuery + " guide");
        variations.add(originalQuery + " tutorial");
        variations.add(originalQuery + " explanation");
        variations.add(originalQuery + " overview");
        variations.add(originalQuery + " analysis");
        variations.add(originalQuery + " research");
        variations.add(originalQuery + " study");

        variations.add(originalQuery + " academic");
        variations.add(originalQuery + " scholarly");
        variations.add(originalQuery + " scientific");
        variations.add(originalQuery + " expert");
        variations.add(originalQuery + " professional");
        variations.add(originalQuery + " examples");
        variations.add(originalQuery + " best practices");
        variations.add(originalQuery + " implementation");
        variations.add(originalQuery + " practical");
        variations.add(originalQuery + " case study");
        variations.add("understanding " + originalQuery);
        variations.add("introduction to " + originalQuery);
        variations.add("advanced " + originalQuery);
        variations.add("comprehensive " + originalQuery);

        return variations;
    }

    private List<CitationResult> executeSingleSearchRound(String query, String originalQuery, Set<String> seenUrls) {
        try {
            WebSearchResults searchResults = webSearchEngine.search(query);

            if (searchResults == null || searchResults.results()
                .isEmpty()) {
                logger.warning("No search results found for query: " + query);
                return List.of();
            }

            List<CompletableFuture<CitationResult>> futures = searchResults.results()
                .stream()
                .limit(maxResultsPerSearch)
                .filter(result -> !seenUrls.contains(result.url()
                    .toString()))
                .map(result -> fetchCitationAsync(result, originalQuery))
                .toList();

            List<CitationResult> roundResults = new ArrayList<>();
            for (CompletableFuture<CitationResult> future : futures) {
                try {
                    CitationResult result = future.get(httpTimeout.toSeconds() + 10, TimeUnit.SECONDS);
                    if (result != null && result.isValid()) {
                        roundResults.add(result);
                    }
                } catch (Exception e) {
                    logger.warning("Failed to fetch citation result: " + e.getMessage());
                }
            }

            return roundResults;

        } catch (Exception e) {
            logger.warning("Search round failed for query: " + query + " - " + e.getMessage());
            return List.of();
        }
    }

    private CompletableFuture<CitationResult> fetchCitationAsync(dev.langchain4j.web.search.WebSearchOrganicResult result, String originalQuery) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = result.url()
                    .toString();
                String content = fetchEnhancedContent(url);
                double relevanceScore = calculateEnhancedRelevanceScore(result, originalQuery, content);

                return CitationResult.builder()
                    .title(result.title())
                    .snippet(result.snippet())
                    .content(content)
                    .url(url)
                    .relevanceScore(relevanceScore)
                    .retrievedAt(LocalDateTime.now())
                    .language(detectLanguage(content))
                    .build();

            } catch (Exception e) {
                logger.warning("Failed to fetch content from URL: " + result.url() + " - " + e.getMessage());

                return CitationResult.builder()
                    .title(result.title())
                    .snippet(result.snippet())
                    .content(result.snippet() != null ? result.snippet() : "Content not available")
                    .url(result.url()
                        .toString())
                    .relevanceScore(0.4)
                    .retrievedAt(LocalDateTime.now())
                    .language("unknown")
                    .build();
            }
        }, executor);
    }

    private String fetchEnhancedContent(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(httpTimeout)
                .header("User-Agent", "Research4j/2.0 Academic Research Bot (+https://github.com/bhavuklabs/research4j)")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.warning("HTTP " + response.statusCode() + " for URL: " + url);
                return "Content not available (HTTP " + response.statusCode() + ")";
            }

            String htmlContent = response.body();
            if (htmlContent == null || htmlContent.trim()
                .isEmpty()) {
                return "No content available";
            }

            Document doc = Jsoup.parse(htmlContent);

            doc.select("script, style, nav, footer, header, aside, .advertisement, .ads, .social-media, .comments")
                .remove();

            String mainContent = extractMainContent(doc);
            if (mainContent != null && mainContent.length() > MIN_CONTENT_LENGTH) {
                return processContent(mainContent);
            }

            String bodyText = doc.body()
                .text();
            return processContent(bodyText);

        } catch (java.net.SocketTimeoutException e) {
            logger.warning("Timeout fetching content from: " + url);
            return "Content not available (timeout)";
        } catch (java.net.ConnectException e) {
            logger.warning("Connection failed for: " + url);
            return "Content not available (connection failed)";
        } catch (Exception e) {
            logger.warning("Error fetching content from " + url + ": " + e.getMessage());
            return "Content not available (error: " + e.getClass()
                .getSimpleName() + ")";
        }
    }

    private String extractMainContent(Document doc) {
        String[] mainSelectors = { "main", "article", ".main-content", ".content", ".post-content", ".entry-content", ".article-content", "#main", "#content" };
        for (String selector : mainSelectors) {
            try {
                var elements = doc.select(selector);
                if (!elements.isEmpty()) {
                    String text = elements.first()
                        .text();
                    if (text.length() > MIN_CONTENT_LENGTH) {
                        return text;
                    }
                }
            } catch (Exception e) {
            }
        }

        return "No content available";
    }

    private String processContent(String rawContent) {
        if (rawContent == null || rawContent.trim()
            .isEmpty()) {
            return "No content available";
        }

        String processed = rawContent.replaceAll("\\s+", " ")
            .replaceAll("\\n{3,}", "\n\n")
            .trim();
        if (processed.length() > MAX_CONTENT_LENGTH) {
            String truncated = processed.substring(0, MAX_CONTENT_LENGTH);
            int lastSentence = Math.max(truncated.lastIndexOf(". "), Math.max(truncated.lastIndexOf("! "), truncated.lastIndexOf("? ")));

            if (lastSentence > MAX_CONTENT_LENGTH * 0.8) {
                processed = truncated.substring(0, lastSentence + 1);
            } else {
                processed = truncated + "...";
            }
        }

        return processed;
    }

    private double calculateEnhancedRelevanceScore(dev.langchain4j.web.search.WebSearchOrganicResult result, String originalQuery, String content) {
        try {
            double score = 0.0;
            String queryLower = originalQuery.toLowerCase();
            String titleLower = result.title()
                .toLowerCase();
            String snippetLower = result.snippet()
                .toLowerCase();
            String contentLower = content != null ? content.toLowerCase() : "";
            String url = result.url()
                .toString()
                .toLowerCase();

            score += calculateAdvancedTextRelevance(titleLower, queryLower) * 0.35;
            score += calculateAdvancedTextRelevance(snippetLower, queryLower) * 0.25;

            if (!contentLower.isEmpty() && content.length() > MIN_CONTENT_LENGTH) {
                score += calculateAdvancedTextRelevance(contentLower, queryLower) * 0.25;
            }

            score += calculateTechnicalContentScore(contentLower, queryLower) * 0.10;
            score += calculateDomainAuthority(result.url()
                .toString()) * 0.05;

            return Math.min(1.0, Math.max(0.0, score));

        } catch (Exception e) {
            logger.warning("Error calculating enhanced relevance score: " + e.getMessage());
            return 0.3;
        }
    }

    private double calculateAdvancedTextRelevance(String text, String query) {
        if (text == null || text.isEmpty() || query == null || query.isEmpty()) {
            return 0.0;
        }

        double score = 0.0;
        String[] queryTerms = query.split("\\s+");
        if (text.contains(query)) {
            score = 1.0;
        } else {
            int matchingTerms = 0;
            for (String term : queryTerms) {
                if (text.contains(term)) {
                    matchingTerms++;
                }
            }
            score = (double) matchingTerms / queryTerms.length;
        }

        if (query.contains("cqrs") || query.contains("command query")) {
            if (text.contains("command") && text.contains("query")) {
                score += 0.3;
            }
            if (text.contains("segregation") || text.contains("responsibility")) {
                score += 0.2;
            }
            if (text.contains("event sourcing") || text.contains("read model") || text.contains("write model")) {
                score += 0.2;
            }
            if (text.contains("microservices") || text.contains("service")) {
                score += 0.1;
            }
        }

        if (query.contains("microservices") || query.contains("service")) {
            if (text.contains("distributed") || text.contains("architecture")) {
                score += 0.2;
            }
            if (text.contains("container") || text.contains("docker") || text.contains("kubernetes")) {
                score += 0.1;
            }
            if (text.contains("api gateway") || text.contains("service discovery")) {
                score += 0.1;
            }
        }

        if (query.contains("spring") || query.contains("boot")) {
            if (text.contains("spring boot") || text.contains("spring framework")) {
                score += 0.3;
            }
            if (text.contains("annotation") || text.contains("@")) {
                score += 0.2;
            }
            if (text.contains("configuration") || text.contains("bean")) {
                score += 0.1;
            }
        }

        return Math.min(1.0, score);
    }

    private double calculateTechnicalContentScore(String content, String query) {
        if (content == null || content.isEmpty()) {
            return 0.0;
        }
        double score = 0.0;
        if (content.contains("class ") || content.contains("interface ") || content.contains("public ")) {
            score += 0.3;
        }
        if (content.contains("@") && (content.contains("component") || content.contains("service") || content.contains("controller"))) {
            score += 0.3;
        }
        if (content.contains("implementation") || content.contains("example") || content.contains("code")) {
            score += 0.2;
        }
        if (content.contains("{") && content.contains("}")) {
            score += 0.2;
        }
        if (content.contains("import") || content.contains("package")) {
            score += 0.1;
        }
        if (content.contains("configuration") || content.contains("setup")) {
            score += 0.1;
        }

        return Math.min(1.0, score);
    }

    private double calculateWordProximity(String text, String[] queryWords) {
        if (queryWords.length < 2) {
            return 0.0;
        }

        String[] textWords = text.split("\\s+");
        int minDistance = Integer.MAX_VALUE;

        for (int i = 0; i < textWords.length; i++) {
            for (String queryWord : queryWords) {
                if (textWords[i].equalsIgnoreCase(queryWord)) {
                    for (int j = 0; j < textWords.length; j++) {
                        if (i != j) {
                            for (String otherQueryWord : queryWords) {
                                if (!otherQueryWord.equals(queryWord) && textWords[j].equalsIgnoreCase(otherQueryWord)) {
                                    minDistance = Math.min(minDistance, Math.abs(i - j));
                                }
                            }
                        }
                    }
                }
            }
        }

        if (minDistance == Integer.MAX_VALUE) {
            return 0.0;
        }
        return Math.max(0.0, 1.0 - (minDistance / 20.0));
    }

    private double calculateDomainAuthority(String url) {
        if (url == null || url.isEmpty()) {
            return 0.0;
        }

        String urlLower = url.toLowerCase();
        double score = 0.5;

        if (urlLower.contains("wikipedia.org") || urlLower.contains(".edu") || urlLower.contains(".gov") || urlLower.contains(".org")) {
            score += 0.3;
        }

        if (urlLower.contains("stackoverflow.com") || urlLower.contains("github.com") || urlLower.contains("medium.com") ||
            urlLower.contains("researchgate.net")) {
            score += 0.2;
        }

        if (urlLower.contains("reuters.com") || urlLower.contains("bbc.com") || urlLower.contains("nature.com") || urlLower.contains("science.org")) {
            score += 0.25;
        }

        if (urlLower.startsWith("https://")) {
            score += 0.05;
        }

        if (urlLower.contains("ads") || urlLower.contains("spam") || urlLower.contains("clickbait")) {
            score -= 0.2;
        }

        return Math.min(1.0, Math.max(0.0, score));
    }

    private String detectLanguage(String content) {
        if (content == null || content.trim()
            .isEmpty()) {
            return "unknown";
        }

        String contentLower = content.toLowerCase();

        String[] englishIndicators = { " the ", " and ", " is ", " of ", " to ", " in ", " that ", " for " };
        int englishMatches = 0;
        for (String indicator : englishIndicators) {
            if (contentLower.contains(indicator)) {
                englishMatches++;
            }
        }

        if (englishMatches >= 4) {
            return "en";
        }

        return "unknown";
    }

    private QualityMetrics assessQualityMetrics(List<CitationResult> citations, String originalQuery) {
        QualityMetrics metrics = new QualityMetrics();

        if (citations.isEmpty()) {
            return metrics;
        }

        double totalRelevance = citations.stream()
            .mapToDouble(CitationResult::getRelevanceScore)
            .sum();
        metrics.averageRelevance = totalRelevance / citations.size();

        Set<String> uniqueDomains = citations.stream()
            .map(CitationResult::getDomain)
            .collect(java.util.stream.Collectors.toSet());
        metrics.domainDiversity = (double) uniqueDomains.size() / citations.size();

        long substantiveContent = citations.stream()
            .mapToLong(citation -> citation.getContent() != null && citation.getContent()
                .length() > MIN_CONTENT_LENGTH ? 1 : 0)
            .sum();
        metrics.contentQuality = (double) substantiveContent / citations.size();

        long highQualityCitations = citations.stream()
            .mapToLong(citation -> citation.getRelevanceScore() >= 0.7 ? 1 : 0)
            .sum();
        metrics.highQualityRatio = (double) highQualityCitations / citations.size();

        metrics.overallQuality =
            (metrics.averageRelevance * 0.4) + (metrics.domainDiversity * 0.2) + (metrics.contentQuality * 0.2) + (metrics.highQualityRatio * 0.2);

        return metrics;
    }

    private boolean shouldContinueSearching(QualityMetrics currentMetrics, QualityMetrics lastMetrics, int searchRound, int totalResults) {
        if (searchRound <= 2) {
            return true;
        }

        if (searchRound >= MAX_TOTAL_SEARCHES) {
            return false;
        }

        if (totalResults < 5) {
            return true;
        }

        if (currentMetrics.overallQuality > lastMetrics.overallQuality + 0.1) {
            return true;
        }

        if (currentMetrics.overallQuality < QUALITY_THRESHOLD && searchRound < 6) {
            return true;
        }

        if (currentMetrics.domainDiversity < DIVERSITY_THRESHOLD && searchRound < 5) {
            return true;
        }

        return false;
    }

    private List<CitationResult> optimizeResultSet(List<CitationResult> allResults, String originalQuery) {
        if (allResults.isEmpty()) {
            return allResults;
        }

        List<CitationResult> filtered = allResults.stream()
            .filter(citation -> citation.getRelevanceScore() >= 0.3)
            .filter(citation -> citation.getContent() != null && citation.getContent()
                .length() >= MIN_CONTENT_LENGTH)
            .sorted((a, b) -> Double.compare(b.getRelevanceScore(), a.getRelevanceScore()))
            .collect(java.util.stream.Collectors.toList());

        logger.info("Result optimization: " + allResults.size() + " -> " + filtered.size() + " citations");
        return filtered;
    }

    private void validateInputs(String apiKey, String cseId) {
        if (apiKey == null || apiKey.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("Google Search API key cannot be null or empty");
        }
        if (cseId == null || cseId.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("Google CSE ID cannot be null or empty");
        }
    }

    private void validateQuery(String query) {
        if (query == null || query.trim()
            .isEmpty()) {
            throw new IllegalArgumentException("Query cannot be null or empty");
        }
    }

    private String truncateString(String str, int maxLength) {
        if (str == null) {
            return "null";
        }
        return str.length() <= maxLength ? str : str.substring(0, maxLength) + "...";
    }

    public boolean isHealthy() {
        if (closed) {
            return false;
        }

        try {
            List<CitationResult> results = fetch("test query");
            return true;
        } catch (Exception e) {
            logger.warning("Health check failed: " + e.getMessage());
            return false;
        }
    }

    public Duration getHttpTimeout() {
        return httpTimeout;
    }

    public int getMaxResultsPerSearch() {
        return maxResultsPerSearch;
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        try {
            executor.shutdown();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.warning("Executor did not terminate cleanly");
                }
            }
            logger.info("Enhanced GeminiCitationFetcher closed successfully");
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread()
                .interrupt();
            logger.warning("Interrupted during shutdown");
        } catch (Exception e) {
            logger.warning("Error during shutdown: " + e.getMessage());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String apiKey;
        private String cseId;
        private Duration httpTimeout = DEFAULT_HTTP_TIMEOUT;
        private int maxResultsPerSearch = MAX_RESULTS_PER_SEARCH;

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder cseId(String cseId) {
            this.cseId = cseId;
            return this;
        }

        public Builder httpTimeout(Duration timeout) {
            this.httpTimeout = timeout;
            return this;
        }

        public Builder maxResultsPerSearch(int maxResults) {
            this.maxResultsPerSearch = maxResults;
            return this;
        }

        public GeminiCitationFetcher build() throws CitationException {
            return new GeminiCitationFetcher(apiKey, cseId, httpTimeout, maxResultsPerSearch);
        }
    }

    private static class QualityMetrics {

        double averageRelevance = 0.0;
        double domainDiversity = 0.0;
        double contentQuality = 0.0;
        double highQualityRatio = 0.0;
        double overallQuality = 0.0;

        @Override
        public String toString() {
            return String.format("QualityMetrics[overall=%.2f, relevance=%.2f, diversity=%.2f, content=%.2f, hq_ratio=%.2f]", overallQuality, averageRelevance,
                domainDiversity, contentQuality, highQualityRatio);
        }
    }
}