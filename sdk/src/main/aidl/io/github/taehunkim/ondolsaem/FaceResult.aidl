package io.github.taehunkim.ondolsaem;

/** One face (API 2): box in 0..1 image coordinates, quality 0..1, L2-normalized identity vector. */
parcelable FaceResult {
    float left;
    float top;
    float right;
    float bottom;
    float score;
    float[] vector;
    String modelId;
    int modelVersion;
}
