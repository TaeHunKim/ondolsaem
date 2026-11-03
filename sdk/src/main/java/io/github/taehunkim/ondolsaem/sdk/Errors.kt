package io.github.taehunkim.ondolsaem.sdk

import java.io.IOException

/** Base class of SDK failures. */
open class OndolSaemException(message: String?, cause: Throwable? = null) : IOException(message, cause)

/** The host app is not installed, refused the connection, did not answer, or the versions do not fit. */
class HostUnavailableException(val reason: Reason, message: String? = null, cause: Throwable? = null) :
    OndolSaemException(message ?: reason.name, cause) {
    enum class Reason {
        NOT_INSTALLED,
        /** The host does not trust this app's signing key. */
        NOT_ALLOWED,
        NO_ANSWER,
        /** The host's API level is lower than this call (or this client) needs: update OndolSaem. */
        HOST_TOO_OLD,
        /** The host no longer serves this client's API level: update the client app. */
        CLIENT_TOO_OLD,
    }
}

/** The host answered with an error (see [OndolSaem.Errors]). */
class HostCallException(val code: Int, message: String?) : OndolSaemException(message)

/**
 * Binder only carries a few exception types and `ServiceSpecificException` is not public API, so host errors travel
 * as `IllegalStateException("ondolsaem:<code>:<message>")`.
 */
object HostErrors {
    private const val PREFIX = "ondolsaem:"

    /** Host side: the exception to throw from a Binder method. */
    fun encode(code: Int, message: String?): IllegalStateException = IllegalStateException("$PREFIX$code:${message ?: ""}")

    /** Client side: null when [e] is not a host error. */
    fun decode(e: IllegalStateException): HostCallException? {
        val m = e.message ?: return null
        if (!m.startsWith(PREFIX)) return null
        val rest = m.removePrefix(PREFIX)
        val code = rest.substringBefore(':').toIntOrNull() ?: return null
        return HostCallException(code, rest.substringAfter(':').ifEmpty { null })
    }
}
