package com.github.bhavuklabs.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ApplicationConfig {
    
    private static final Properties properties = new Properties();
    private static ApplicationConfig instance;
    
    static {
        loadProperties();
    }
    
    private ApplicationConfig() {}
    
    public static ApplicationConfig getInstance() {
        if (instance == null) {
            synchronized (ApplicationConfig.class) {
                if (instance == null) {
                    instance = new ApplicationConfig();
                }
            }
        }
        return instance;
    }
    
    private static void loadProperties() {
        try (InputStream input = ApplicationConfig.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (input != null) {
                properties.load(input);
            } else {
                throw new RuntimeException("application.properties file not found in classpath");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load application.properties", e);
        }
    }
    
    public String getGeminiApiKey() {
        return getProperty("research4j.api.gemini.key");
    }
    
    public String getGeminiModel() {
        return getProperty("research4j.api.gemini.model", "gemini-2.0-flash");
    }
    
    public String getTavilyApiKey() {
        return getProperty("research4j.api.tavily.key");
    }
    
    public String getTavilyAltApiKey() {
        return getProperty("research4j.api.tavily.alt.key");
    }
    
    public String getAppName() {
        return getProperty("research4j.app.name", "Research4j");
    }
    
    public String getAppVersion() {
        return getProperty("research4j.app.version", "1.0.0");
    }
    
    public int getVectorDimension() {
        return Integer.parseInt(getProperty("research4j.vector.dimension", "384"));
    }
    
    public double getVectorSimilarityThreshold() {
        return Double.parseDouble(getProperty("research4j.vector.similarity.threshold", "0.1"));
    }
    
    public int getMaxSources() {
        return Integer.parseInt(getProperty("research4j.research.max.sources", "20"));
    }
    
    public int getMaxRounds() {
        return Integer.parseInt(getProperty("research4j.research.max.rounds", "3"));
    }
    
    public double getMinRelevanceScore() {
        return Double.parseDouble(getProperty("research4j.research.min.relevance.score", "0.7"));
    }
    
    public boolean isParallelProcessingEnabled() {
        return Boolean.parseBoolean(getProperty("research4j.research.enable.parallel.processing", "true"));
    }
    
    public boolean isQualityFilteringEnabled() {
        return Boolean.parseBoolean(getProperty("research4j.research.enable.quality.filtering", "true"));
    }
    
    private String getProperty(String key) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new RuntimeException("Required property '" + key + "' not found or is empty");
        }
        return value.trim();
    }
    
    private String getProperty(String key, String defaultValue) {
        String value = properties.getProperty(key, defaultValue);
        return value != null ? value.trim() : defaultValue;
    }
    
    public void validateConfiguration() {
        try {
            getGeminiApiKey();
            getTavilyApiKey();
            System.out.println("✅ Configuration validation passed");
        } catch (RuntimeException e) {
            System.err.println("❌ Configuration validation failed: " + e.getMessage());
            throw e;
        }
    }
}
