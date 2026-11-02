package io.github.taehunkim.ondolsaem.sdk

/** Constants shared by the OndolSaem host and its client apps. */
object OndolSaem {
    /** Package name of the host app. */
    const val PACKAGE = "io.github.taehunkim.ondolsaem"

    /** Intent action of the host's bound service. */
    const val ACTION_BIND = "$PACKAGE.action.BIND"

    /** Highest API level this SDK understands; compare with IOndolSaem.apiVersion(). */
    const val API_VERSION = 1
}
