package io.github.taehunkim.ondolsaem;

/** What a client tells the host when it connects (IOndolSaem.hello). */
parcelable ClientInfo {
    /** OndolSaem.API_VERSION of the client's SDK. */
    int apiVersion;
    /** SDK release, e.g. "0.1.0" (for display and logs). */
    String sdkVersion;
}
