package io.github.taehunkim.ondolsaem;

/** The model a category currently serves. `id` is empty when the category has no usable model. */
parcelable ModelInfo {
    String category;
    String id;
    String displayName;
    /** Bumped by the host when the same id produces different outputs (clients treat it like a new model). */
    int version;
    /** Downloaded / available, so calls can succeed. */
    boolean ready;
    /** Embedding: can embed audio. */
    boolean audio;
    /** Chat: accepts images. */
    boolean vision;
}
