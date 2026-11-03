package io.github.taehunkim.ondolsaem;

import io.github.taehunkim.ondolsaem.IClientCallback;
import io.github.taehunkim.ondolsaem.ILease;
import io.github.taehunkim.ondolsaem.ModelInfo;

/**
 * Entry point of the OndolSaem model host (bound service, action OndolSaem.ACTION_BIND).
 * Contract rule: methods are only ever appended (explicit transaction ids), never reordered or removed;
 * check apiVersion() before calling anything newer than API 1.
 * Errors arrive as IllegalStateException encoded by HostErrors (codes in OndolSaem.Errors), or SecurityException.
 */
interface IOndolSaem {
    int apiVersion() = 1;
    /** The active model of every category the host knows. */
    ModelInfo[] categories() = 2;
    /** Null for an unknown category. */
    @nullable ModelInfo activeModel(String category) = 3;
    /** IEmbeddingService, IChatService, IOcrService or IAsrService; null for an unknown category. */
    @nullable IBinder getCapability(String category) = 4;
    void registerClient(IClientCallback callback) = 5;
    void unregisterClient(IClientCallback callback) = 6;
    /**
     * Loads the category's model now and keeps it loaded until the lease is released or [clientToken] dies
     * (any Binder object of the client; the host watches it so a crashed client does not pin the model).
     */
    ILease acquire(String category, IBinder clientToken) = 7;
}
