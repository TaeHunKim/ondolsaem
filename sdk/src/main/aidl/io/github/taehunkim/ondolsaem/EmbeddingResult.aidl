package io.github.taehunkim.ondolsaem;

/** An L2-normalized vector plus the identity of the model that produced it. */
parcelable EmbeddingResult {
    float[] vector;
    String modelId;
    int modelVersion;
}
