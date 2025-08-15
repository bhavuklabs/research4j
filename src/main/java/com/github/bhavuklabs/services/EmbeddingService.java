package com.github.bhavuklabs.services;


public interface EmbeddingService {
    
    
    float[] generateEmbeddings(String text);
    
    
    int getDimensions();
    
    
    default float[][] generateBatchEmbeddings(String[] texts) {
        float[][] embeddings = new float[texts.length][];
        for (int i = 0; i < texts.length; i++) {
            embeddings[i] = generateEmbeddings(texts[i]);
        }
        return embeddings;
    }
}
