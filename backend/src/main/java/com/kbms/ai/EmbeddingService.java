package com.kbms.ai;

/** Provider boundary for query/document vectors. Dimension is a deliberate, configured choice. */
public interface EmbeddingService {

    float[] embed(String text);

    int dimension();

    String model();
}
