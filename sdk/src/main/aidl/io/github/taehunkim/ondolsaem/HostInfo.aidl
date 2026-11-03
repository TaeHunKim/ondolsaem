package io.github.taehunkim.ondolsaem;

/** What the host answers to IOndolSaem.hello. */
parcelable HostInfo {
    /** Highest API level the host implements; methods of higher levels must not be called. */
    int apiVersion;
    /** Lowest client API level the host still serves (older clients are refused with CLIENT_TOO_OLD). */
    int minClientApi;
    /** Host app version, e.g. "0.1.0" (for display). */
    String appVersion;
}
