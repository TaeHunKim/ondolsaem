package io.github.taehunkim.ondolsaem.service

import android.content.Context
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Process
import io.github.taehunkim.ondolsaem.R
import java.util.concurrent.ConcurrentHashMap

/**
 * Only own apps may use the host. A caller is allowed when one of its packages is signed with the host's own key,
 * or with one of the certificates in `R.array.allowed_signers` (SHA-256, hex), so a debug client can use a release
 * host and the other way round. Checked on every call; results are cached per uid.
 *
 * There is deliberately no custom permission: a client installed before the host would never be granted it.
 */
class CallerCheck(
    private val context: Context,
    private val allowed: List<ByteArray> = context.resources.getStringArray(R.array.allowed_signers).mapNotNull(::hex),
) {
    private val cache = ConcurrentHashMap<Int, String>()
    private val denied = ConcurrentHashMap<Int, Long>()

    /** The calling package; throws [SecurityException] for anyone else. */
    fun require(): String {
        val uid = Binder.getCallingUid()
        if (uid == Process.myUid()) return context.packageName
        cache[uid]?.let { return it }
        denied[uid]?.let { if (System.currentTimeMillis() - it < 10_000) throw SecurityException("uid $uid is not allowed to use OndolSaem") }
        val token = Binder.clearCallingIdentity()
        try {
            val pm = context.packageManager
            val pkgs = pm.getPackagesForUid(uid).orEmpty()
            val ok = pkgs.firstOrNull { pkg ->
                pm.checkSignatures(context.packageName, pkg) == PackageManager.SIGNATURE_MATCH ||
                    allowed.any { cert -> runCatching { pm.hasSigningCertificate(pkg, cert, PackageManager.CERT_INPUT_SHA256) }.getOrDefault(false) }
            }
            if (ok == null) {
                denied[uid] = System.currentTimeMillis()
                throw SecurityException("${pkgs.joinToString()} is not allowed to use OndolSaem")
            }
            cache[uid] = ok
            return ok
        } finally {
            Binder.restoreCallingIdentity(token)
        }
    }

    companion object {
        fun hex(s: String): ByteArray? {
            val t = s.replace(":", "").replace(" ", "").trim()
            if (t.length != 64) return null
            return ByteArray(32) { t.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        }
    }
}
