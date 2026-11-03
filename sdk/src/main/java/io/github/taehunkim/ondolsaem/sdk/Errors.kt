package io.github.taehunkim.ondolsaem.sdk

import java.io.IOException

/** Base class of SDK failures. */
open class OndolSaemException(message: String?, cause: Throwable? = null) : IOException(message, cause)

/** The host app is not installed, refused the connection, or did not answer. */
class HostUnavailableException(val reason: Reason, message: String? = null, cause: Throwable? = null) :
    OndolSaemException(message ?: reason.name, cause) {
    enum class Reason { NOT_INSTALLED, NOT_ALLOWED, NO_ANSWER, TOO_OLD }
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
