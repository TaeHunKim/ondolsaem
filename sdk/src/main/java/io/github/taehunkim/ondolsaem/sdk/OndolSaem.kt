package io.github.taehunkim.ondolsaem.sdk

/** Constants shared by the OndolSaem host and its client apps. */
object OndolSaem {
    /** Package name of the host app. */
    const val PACKAGE = "io.github.taehunkim.ondolsaem"

    /** Intent action of the host's bound service. */
    const val ACTION_BIND = "$PACKAGE.action.BIND"

    /**
     * Sent by the host to every known client package (explicit, via setPackage) when a category's model changes.
     * Extras: [EXTRA_CATEGORY], [EXTRA_MODEL_ID]. Anyone could send this action, so receivers only treat it as a
     * hint to re-check the host ([OndolSaemClient.activeModel]); the SDK's [ModelChangedReceiver] does that.
     */
    const val ACTION_MODEL_CHANGED = "$PACKAGE.action.MODEL_CHANGED"
    const val EXTRA_CATEGORY = "category"
    const val EXTRA_MODEL_ID = "model_id"

    /** Highest API level this SDK understands; compare with IOndolSaem.apiVersion(). */
    const val API_VERSION = 1

    /** Requests a user is waiting for (chat, search queries) run before [PRIORITY_BACKGROUND] ones (indexing). */
    const val PRIORITY_INTERACTIVE = 0
    const val PRIORITY_BACKGROUND = 1

    /** Model categories (stable string ids). New categories are added here, never renamed. */
    object Categories {
        const val EMBEDDING = "embedding"
        const val LLM = "llm"
        const val OCR = "ocr"
        const val ASR = "asr"
    }

    /** Error codes of [HostCallException] (sent over Binder by [HostErrors]). */
    object Errors {
        /** The category has no downloaded model (or the model lacks the requested capability). */
        const val MODEL_NOT_READY = 1
        /** The model failed to load or to run. */
        const val MODEL_FAILED = 2
        /** Input rejected (too long, unreadable image, ...). */
        const val BAD_INPUT = 3
        /** The request was cancelled. */
        const val CANCELLED = 4
    }
}
