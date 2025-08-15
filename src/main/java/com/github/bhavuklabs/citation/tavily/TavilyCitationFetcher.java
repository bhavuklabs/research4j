package com.github.bhavuklabs.citation.tavily;

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
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchResults;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;
import com.github.bhavuklabs.citation.CitationFetcher;
import com.github.bhavuklabs.citation.CitationResult;
import com.github.bhavuklabs.exceptions.citation.CitationException;

public class TavilyCitationFetcher implements CitationFetcher, AutoCloseable {

    private static final Logger logger = Logger.getLogger(TavilyCitationFetcher.class.getName());

    private static final Duration DEFAULT_HTTP_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(8);
    private static final int MAX_CONTENT_LENGTH = 10000;
    private static final int MIN_CONTENT_LENGTH = 100;
    private static final int MAX_CONCURRENT_REQUESTS = 10;
    private static final int MAX_RESULTS_PER_SEARCH = 20;

    private final WebSearchEngine webSearchEngine;
    private final HttpClient httpClient;
    private final ExecutorService executor;
    private final String apiKey;
    private volatile boolean closed = false;

    public TavilyCitationFetcher(String apiKey) throws CitationException {
        if (apiKey == null || apiKey.trim()
            .isEmpty()) {
            throw new CitationException("Tavily API key cannot be null or empty", null, "initialization", "TAVILY");
        }

        this.apiKey = apiKey;

        try {

            this.webSearchEngine = TavilyWebSearchEngine.builder()
                .apiKey(apiKey)
                .includeRawContent(true)
                .build();

            this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECTION_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

            this.executor = Executors.newFixedThreadPool(MAX_CONCURRENT_REQUESTS);

            logger.info("TavilyCitationFetcher initialized successfully with enhanced features");

        } catch (Exception e) {
            throw new CitationException("Failed to initialize Tavily citation fetcher: " + e.getMessage(), e, "initialization", "TAVILY");
        }
    }

    @Override
    public List<CitationResult> fetch(String query) throws CitationException {
        if (closed) {
            throw new IllegalStateException("Citation fetcher has been closed");
        }

        validateQuery(query);

        try {
            logger.info("Fetching citations from Tavily for query: " + truncateString(query, 100));

            WebSearchResults searchResults = executeSearchWithRetry(query);

            if (searchResults == null || searchResults.results()
                .isEmpty()) {
                logger.warning("No search results found for query: " + query);
                return new ArrayList<>();
            }

            List<CitationResult> citations = processSearchResultsParallel(searchResults, query);

            List<CitationResult> validatedCitations = validateAndFilterResults(citations);

            logger.info("Successfully fetched " + validatedCitations.size() + " validated citations from Tavily (from " + searchResults.results()
                .size() + " raw results)");

            return validatedCitations;

        } catch (CitationException e) {
            throw e;
        } catch (Exception e) {
            logger.severe("Error fetching citations from Tavily: " + e.getMessage());
            throw new CitationException("Tavily search failed: " + e.getMessage(), e, query, "TAVILY");
        }
    }

    private WebSearchResults executeSearchWithRetry(String query) throws CitationException {
        int maxRetries = 3;
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                logger.fine("Executing Tavily search (attempt " + attempt + "): " + query);

                WebSearchResults results = webSearchEngine.search(query);

                if (results != null && !results.results()
                    .isEmpty()) {
                    logger.info("Tavily search successful: " + results.results()
                        .size() + " results");
                    return results;
                }

                logger.warning("Tavily search returned empty results on attempt " + attempt);

                if (attempt < maxRetries) {

                    String enhancedQuery = enhanceQuery(query, attempt);
                    logger.info("Retrying with enhanced query: " + enhancedQuery);
                    results = webSearchEngine.search(enhancedQuery);

                    if (results != null && !results.results()
                        .isEmpty()) {
                        return results;
                    }
                }

            } catch (Exception e) {
                lastException = e;
                logger.warning("Tavily search attempt " + attempt + " failed: " + e.getMessage());

                if (attempt < maxRetries) {
                    try {

                        long delay = 1000L * (long) Math.pow(2, attempt - 1);
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread()
                            .interrupt();
                        break;
                    }
                }
            }
        }

        if (lastException != null) {
            throw new CitationException("All Tavily search attempts failed: " + lastException.getMessage(), lastException, query, "TAVILY");
        } else {
            throw new CitationException("Tavily search returned no results after " + maxRetries + " attempts", null, query, "TAVILY");
        }
    }

    private String enhanceQuery(String originalQuery, int attempt) {
        switch (attempt) {
            case 1:
                return "\"" + originalQuery + "\"";
            case 2:
                return originalQuery + " guide tutorial";
            case 3:
                return originalQuery.replace(" ", " AND ");
            default:
                return originalQuery;
        }
    }

    private List<CitationResult> processSearchResultsParallel(WebSearchResults searchResults, String originalQuery) {
        List<CompletableFuture<CitationResult>> futures = searchResults.results()
            .stream()
            .limit(MAX_RESULTS_PER_SEARCH)
            .map(result -> processResultAsync(result, originalQuery))
            .collect(Collectors.toList());

        List<CitationResult> citations = new ArrayList<>();

        for (CompletableFuture<CitationResult> future : futures) {
            try {
                CitationResult citation = future.get(DEFAULT_HTTP_TIMEOUT.toSeconds() + 10, TimeUnit.SECONDS);
                if (citation != null && citation.isValid()) {
                    citations.add(citation);
                }
            } catch (Exception e) {
                logger.warning("Failed to process citation result: " + e.getMessage());
            }
        }

        return citations;
    }

    private CompletableFuture<CitationResult> processResultAsync(dev.langchain4j.web.search.WebSearchOrganicResult result, String originalQuery) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = result.url()
                    .toString();
                String title = cleanText(result.title());
                String snippet = cleanText(result.snippet());
                String rawContent = result.content();

                String processedContent = processContent(rawContent, url);

                double relevanceScore = calculateEnhancedRelevanceScore(result, originalQuery, processedContent);

                CitationResult citation = CitationResult.builder()
                    .title(title)
                    .snippet(snippet)
                    .content(processedContent)
                    .url(url)
                    .relevanceScore(relevanceScore)
                    .retrievedAt(LocalDateTime.now())
                    .language(detectLanguage(processedContent))
                    .build();

                enhanceCitationMetadata(citation, result, originalQuery);

                return citation;

            } catch (Exception e) {
                logger.warning("Failed to process citation result from URL: " + result.url() + " - " + e.getMessage());
                return createFallbackCitation(result, originalQuery);
            }
        }, executor);
    }

    private String processContent(String rawContent, String url) {
        if (rawContent == null || rawContent.trim()
            .isEmpty()) {

            return fetchContentFromUrl(url);
        }

        try {

            String cleanedContent = cleanHtmlContent(rawContent);

            if (cleanedContent.length() < MIN_CONTENT_LENGTH) {
                String fetchedContent = fetchContentFromUrl(url);
                if (fetchedContent.length() > cleanedContent.length()) {
                    return fetchedContent;
                }
            }

            return cleanedContent;

        } catch (Exception e) {
            logger.warning("Error processing content from " + url + ": " + e.getMessage());
            return cleanText(rawContent);
        }
    }

    private String cleanHtmlContent(String htmlContent) {
        if (htmlContent == null || htmlContent.trim()
            .isEmpty()) {
            return "No content available";
        }

        try {

            Document doc;
            try {
                doc = Jsoup.parse(htmlContent);
            } catch (Exception e) {

                doc = Jsoup.parse(htmlContent, "", Parser.xmlParser());
            }

            doc.select("script, style, nav, footer, header, aside")
                .remove();
            doc.select(".advertisement, .ads, .social-media, .comments")
                .remove();
            doc.select("[class*=ad], [id*=ad], [class*=social], [id*=social]")
                .remove();
            doc.select("iframe, embed, object")
                .remove();

            String mainContent = extractMainContent(doc);
            if (mainContent != null && mainContent.length() > MIN_CONTENT_LENGTH) {
                return processTextContent(mainContent);
            }

            String bodyText = doc.body() != null ? doc.body()
                .text() : doc.text();
            return processTextContent(bodyText);

        } catch (Exception e) {
            logger.warning("Error parsing HTML content: " + e.getMessage());

            return cleanText(htmlContent.replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " "));
        }
    }

    private String extractMainContent(Document doc) {

        String[] mainSelectors = { "main", "article", ".main-content", ".content", ".post-content", ".entry-content", ".article-content", "#main", "#content",
            ".container .content", ".page-content", ".post-body" };

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

    private String processTextContent(String textContent) {
        if (textContent == null || textContent.trim()
            .isEmpty()) {
            return "No content available";
        }

        String processed = textContent.replaceAll("\\s+", " ")
            .replaceAll("\\n{3,}", "\n\n")
            .replaceAll("\\t+", " ")
            .trim();

        if (processed.length() > MAX_CONTENT_LENGTH) {
            processed = truncateAtSentenceBoundary(processed, MAX_CONTENT_LENGTH);
        }

        return processed;
    }

    private String truncateAtSentenceBoundary(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }

        String truncated = text.substring(0, maxLength);

        int lastSentence = Math.max(truncated.lastIndexOf(". "), Math.max(truncated.lastIndexOf("! "), truncated.lastIndexOf("? ")));

        if (lastSentence > maxLength * 0.8) {
            return truncated.substring(0, lastSentence + 1);
        }

        return truncated + "...";
    }

    private String fetchContentFromUrl(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(DEFAULT_HTTP_TIMEOUT)
                .header("User-Agent", "Research4j/2.0 Academic Research Bot (+https://github.com/research4j)")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept-Encoding", "gzip, deflate")
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return cleanHtmlContent(response.body());
            } else {
                logger.warning("HTTP " + response.statusCode() + " for URL: " + url);
                return "Content not available (HTTP " + response.statusCode() + ")";
            }

        } catch (java.net.SocketTimeoutException e) {
            logger.warning("Timeout fetching content from: " + url);
            return "Content not available (timeout)";
        } catch (java.net.ConnectException e) {
            logger.warning("Connection failed for: " + url);
            return "Content not available (connection failed)";
        } catch (Exception e) {
            logger.warning("Failed to fetch content from URL " + url + ": " + e.getMessage());
            return "Content not available";
        }
    }

    private double calculateEnhancedRelevanceScore(dev.langchain4j.web.search.WebSearchOrganicResult result, String originalQuery, String processedContent) {

        try {
            double score = 0.5;

            String queryLower = originalQuery.toLowerCase();
            String titleLower = result.title() != null ? result.title()
                .toLowerCase() : "";
            String snippetLower = result.snippet() != null ? result.snippet()
                .toLowerCase() : "";
            String contentLower = processedContent != null ? processedContent.toLowerCase() : "";
            String url = result.url()
                .toString()
                .toLowerCase();

            if (titleLower.contains(queryLower)) {
                score += 0.3;
            } else {
                String[] queryWords = queryLower.split("\\s+");
                long titleMatches = java.util.Arrays.stream(queryWords)
                    .mapToLong(word -> titleLower.contains(word) ? 1 : 0)
                    .sum();
                score += (titleMatches / (double) queryWords.length) * 0.2;
            }

            if (!snippetLower.isEmpty()) {
                String[] queryWords = queryLower.split("\\s+");
                long snippetMatches = java.util.Arrays.stream(queryWords)
                    .mapToLong(word -> snippetLower.contains(word) ? 1 : 0)
                    .sum();
                score += (snippetMatches / (double) queryWords.length) * 0.2;
            }

            if (!contentLower.isEmpty() && processedContent.length() > MIN_CONTENT_LENGTH) {
                String[] queryWords = queryLower.split("\\s+");
                long contentMatches = java.util.Arrays.stream(queryWords)
                    .mapToLong(word -> contentLower.contains(word) ? 1 : 0)
                    .sum();
                score += (contentMatches / (double) queryWords.length) * 0.25;

                if (contentLower.contains(queryLower)) {
                    score += 0.1;
                }
            }

            score += calculateDomainAuthority(url) * 0.15;

            if (processedContent.length() > MIN_CONTENT_LENGTH * 3) {
                score += 0.05;
            }
            if (processedContent.length() > MIN_CONTENT_LENGTH * 6) {
                score += 0.05;
            }

            return Math.min(1.0, Math.max(0.0, score));

        } catch (Exception e) {
            logger.warning("Error calculating enhanced relevance score: " + e.getMessage());
            return 0.4;
        }
    }

    private double calculateDomainAuthority(String url) {
        if (url == null || url.isEmpty()) {
            return 0.0;
        }

        double score = 0.0;

        if (url.contains("wikipedia.org") || url.contains(".edu") || url.contains(".gov") || url.contains(".org")) {
            score += 0.8;
        }

        if (url.contains("stackoverflow.com") || url.contains("github.com") || url.contains("medium.com") || url.contains("dev.to")) {
            score += 0.6;
        }

        if (url.contains("reuters.com") || url.contains("bbc.com") || url.contains("nature.com") || url.contains("arxiv.org")) {
            score += 0.7;
        }

        if (url.contains("docs.") || url.contains("documentation") || url.contains("tutorial") || url.contains("guide")) {
            score += 0.5;
        }

        if (url.startsWith("https://")) {
            score += 0.1;
        }

        if (url.contains("ads") || url.contains("spam") || url.contains("clickbait") || url.contains("buy") || url.contains("deal")) {
            score -= 0.3;
        }

        return Math.min(1.0, Math.max(0.0, score));
    }

    private String detectLanguage(String content) {
        if (content == null || content.trim()
            .isEmpty()) {
            return "unknown";
        }

        String contentLower = content.toLowerCase();

        String[] englishIndicators = { " the ", " and ", " is ", " of ", " to ", " in ", " that ", " for ", " with ", " as ", " be ", " at ", " by ", " this ",
            " have ", " from " };

        int englishMatches = 0;
        for (String indicator : englishIndicators) {
            if (contentLower.contains(indicator)) {
                englishMatches++;
            }
        }

        if (englishMatches >= 6) {
            return "en";
        }

        return "unknown";
    }

    private void enhanceCitationMetadata(CitationResult citation, dev.langchain4j.web.search.WebSearchOrganicResult result, String originalQuery) {
        try {

            citation.addMetadata("source", "tavily");
            citation.addMetadata("search_query", originalQuery);
            citation.addMetadata("fetched_at", java.time.Instant.now()
                .toString());

            String url = result.url()
                .toString();
            citation.addMetadata("url_scheme", url.startsWith("https") ? "https" : "http");
            citation.addMetadata("domain_type", classifyDomain(url));

            String content = citation.getContent();
            if (content != null) {
                citation.addMetadata("content_length", String.valueOf(content.length()));
                citation.addMetadata("estimated_read_time", String.valueOf(content.split("\\s+").length / 200));
            }

        } catch (Exception e) {
            logger.warning("Error enhancing citation metadata: " + e.getMessage());
        }
    }

    private String classifyDomain(String url) {
        String urlLower = url.toLowerCase();

        if (urlLower.contains(".edu")) {
            return "educational";
        }
        if (urlLower.contains(".gov")) {
            return "government";
        }
        if (urlLower.contains(".org")) {
            return "organization";
        }
        if (urlLower.contains("wikipedia")) {
            return "encyclopedia";
        }
        if (urlLower.contains("github")) {
            return "code_repository";
        }
        if (urlLower.contains("stackoverflow")) {
            return "q_and_a";
        }
        if (urlLower.contains("medium") || urlLower.contains("blog")) {
            return "blog";
        }
        if (urlLower.contains("news")) {
            return "news";
        }
        if (urlLower.contains("docs.") || urlLower.contains("documentation")) {
            return "documentation";
        }

        return "general";
    }

    private CitationResult createFallbackCitation(dev.langchain4j.web.search.WebSearchOrganicResult result, String originalQuery) {
        try {
            return CitationResult.builder()
                .title(cleanText(result.title() != null ? result.title() : "Unknown Title"))
                .snippet(cleanText(result.snippet() != null ? result.snippet() : "No snippet available"))
                .content(cleanText(result.content() != null ? result.content() : "No content available"))
                .url(result.url()
                    .toString())
                .relevanceScore(0.3)
                .retrievedAt(LocalDateTime.now())
                .language("unknown")
                .build();
        } catch (Exception e) {
            logger.severe("Failed to create fallback citation: " + e.getMessage());
            return CitationResult.builder().content("Failed to process citation").build();
        }
    }

    private List<CitationResult> validateAndFilterResults(List<CitationResult> citations) {
        if (citations == null || citations.isEmpty()) {
            return new ArrayList<>();
        }

        Set<String> seenUrls = new HashSet<>();
        List<CitationResult> validatedResults = new ArrayList<>();

        for (CitationResult citation : citations) {
            if (isValidCitation(citation) && !seenUrls.contains(citation.getUrl())) {
                seenUrls.add(citation.getUrl());
                validatedResults.add(citation);
            }
        }

        validatedResults.sort((c1, c2) -> Double.compare(c2.getRelevanceScore(), c1.getRelevanceScore()));

        logger.info("Validated results: " + citations.size() + " -> " + validatedResults.size() + " unique citations");
        return validatedResults;
    }

    private boolean isValidCitation(CitationResult citation) {
        if (citation == null || !citation.isValid()) {
            return false;
        }

        String content = citation.getContent();
        if (content == null || content.trim()
            .length() < 50) {
            return false;
        }

        String title = citation.getTitle() != null ? citation.getTitle()
            .toLowerCase() : "";
        String contentLower = content.toLowerCase();

        String[] spamIndicators = { "click here", "buy now", "limited time", "act now", "free money", "get rich quick", "lose weight fast", "amazing deal" };

        for (String indicator : spamIndicators) {
            if (title.contains(indicator) || contentLower.contains(indicator)) {
                return false;
            }
        }

        String url = citation.getUrl();
        if (url == null || url.trim()
            .isEmpty() || url.contains("javascript:") || url.startsWith("mailto:")) {
            return false;
        }

        return true;
    }

    private String cleanText(String text) {
        if (text == null) {
            return "No content available";
        }

        return text.replaceAll("\\s+", " ")
            .replaceAll("[\\r\\n]+", " ")
            .replaceAll("\\t+", " ")
            .trim();
    }

    private void validateQuery(String query) throws CitationException {
        if (query == null || query.trim()
            .isEmpty()) {
            throw new CitationException("Query cannot be null or empty", null, query, "TAVILY");
        }

        if (query.length() > 500) {
            throw new CitationException("Query too long (max 500 characters)", null, query, "TAVILY");
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

            List<CitationResult> results = fetch("test health check");
            return true;
        } catch (Exception e) {
            logger.warning("Health check failed: " + e.getMessage());
            return false;
        }
    }

    public FetcherConfig getConfig() {
        return new FetcherConfig("TAVILY", MAX_RESULTS_PER_SEARCH, DEFAULT_HTTP_TIMEOUT, !closed);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        try {
            executor.shutdown();
            if (!executor.awaitTermination(15, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.warning("Executor did not terminate cleanly");
                }
            }
            logger.info("TavilyCitationFetcher closed successfully");
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread()
                .interrupt();
            logger.warning("Interrupted during shutdown");
        }
    }

    public static class FetcherConfig {

        private final String source;
        private final int maxResults;
        private final Duration timeout;
        private final boolean active;

        public FetcherConfig(String source, int maxResults, Duration timeout, boolean active) {
            this.source = source;
            this.maxResults = maxResults;
            this.timeout = timeout;
            this.active = active;
        }

        public String getSource() {
            return source;
        }

        public int getMaxResults() {
            return maxResults;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public boolean isActive() {
            return active;
        }

        @Override
        public String toString() {
            return String.format("FetcherConfig{source=%s, maxResults=%d, timeout=%s, active=%s}", source, maxResults, timeout, active);
        }
    }
}