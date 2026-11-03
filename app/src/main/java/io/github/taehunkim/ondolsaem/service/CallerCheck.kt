package io.github.taehunkim.ondolsaem.service

import android.content.Context
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Process
import io.github.taehunkim.ondolsaem.R
import io.github.taehunkim.ondolsaem.sdk.HostErrors
import io.github.taehunkim.ondolsaem.sdk.OndolSaem
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Who may use the host. Own apps always may: a caller signed with the host's own key, or with one of the
 * certificates in `R.array.allowed_signers` (SHA-256, hex), so a debug client can use a release host and the other
 * way round. Any other app needs the user's approval ([Approvals]): its first call records a request, tells the
 * user ([onRequest]) and is refused with [OndolSaem.Errors.NOT_APPROVED] until the user allows it. Checked on every
 * call; allowed callers are cached per uid until a decision changes.
 *
 * There is deliberately no custom permission: a client installed before the host would never be granted it.
 */
class CallerCheck(
    private val context: Context,
    private val approvals: Approvals? = null,
    private val allowed: List<ByteArray> = context.resources.getStringArray(R.array.allowed_signers).mapNotNull(::hex),
) {
    private val cache = ConcurrentHashMap<Int, String>()

    /** A new approval request was recorded for this package. */
    var onRequest: (pkg: String) -> Unit = {}

    init { approvals?.onChange = { cache.clear() } }

    /**
     * The calling package; otherwise throws [SecurityException] whose message carries [OndolSaem.Errors.NOT_APPROVED]
     * in the [HostErrors] format (older clients still see a plain refusal).
     */
    fun require(): String {
        val uid = Binder.getCallingUid()
        if (uid == Process.myUid()) return context.packageName
        cache[uid]?.let { return it }
        val token = Binder.clearCallingIdentity()
        try {
            val pm = context.packageManager
            val pkgs = pm.getPackagesForUid(uid).orEmpty()
            val own = pkgs.firstOrNull { pkg ->
                pm.checkSignatures(context.packageName, pkg) == PackageManager.SIGNATURE_MATCH ||
                    allowed.any { cert -> runCatching { pm.hasSigningCertificate(pkg, cert, PackageManager.CERT_INPUT_SHA256) }.getOrDefault(false) }
            }
            if (own != null) { cache[uid] = own; return own }
            val pkg = pkgs.minOrNull() ?: throw refused("uid $uid has no package")
            val store = approvals ?: throw refused("$pkg is not allowed to use OndolSaem")
            val cert = certSha256(pkg) ?: throw refused("$pkg has no signing certificate")
            when (decide(store.get(pkg), cert)) {
                Decision.ALLOW -> { cache[uid] = pkg; return pkg }
                Decision.DENY -> throw refused("$pkg was denied access to OndolSaem")
                Decision.ASK -> {
                    if (store.request(pkg, cert)) onRequest(pkg)
                    throw refused("$pkg is waiting for approval in OndolSaem")
                }
                Decision.WAIT -> throw refused("$pkg is waiting for approval in OndolSaem")
            }
        } finally {
            Binder.restoreCallingIdentity(token)
        }
    }

    private fun refused(message: String) =
        SecurityException(HostErrors.encode(OndolSaem.Errors.NOT_APPROVED, message).message)

    private fun certSha256(pkg: String): String? = runCatching {
        val info = context.packageManager.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
        val signers = info.signingInfo?.apkContentsSigners ?: return null
        // several signers are rare; a stable digest of all of them, sorted
        signers.map { toHex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) }.sorted().joinToString(",")
    }.getOrNull()

    enum class Decision { ALLOW, DENY, ASK, WAIT }

    companion object {
        /** What to do with a third-party caller whose certificate digest is [cert], given the stored decision. */
        fun decide(stored: Approval?, cert: String): Decision = when {
            stored == null || stored.certSha256 != cert -> Decision.ASK // unknown, or re-signed with another key
            stored.state == Approval.State.ALLOWED -> Decision.ALLOW
            stored.state == Approval.State.DENIED -> Decision.DENY
            else -> Decision.WAIT
        }

        fun toHex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

        fun hex(s: String): ByteArray? {
            val t = s.replace(":", "").replace(" ", "").trim()
            if (t.length != 64) return null
            return ByteArray(32) { t.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        }
    }
}
