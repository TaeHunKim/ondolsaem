package io.github.taehunkim.ondolsaem.sdk

/**
 * Helpers for yes/no decisions ([OndolSaemClient.entail]). The decision model answers whether a statement follows
 * from a text, so a question like "is this note about travel?" becomes the statement "This text is about travel."
 */
object Decisions {
    /** Probability at or above which callers usually take the answer as yes. */
    const val YES = 0.70f

    /** Probability at or below which callers usually take the answer as no; between the two is unsure. */
    const val NO = 0.30f

    /**
     * The statement "this text is about [topic]" in Korean when [topic] or [description] contains Hangul, otherwise
     * in English. A [description] (what the topic covers) is appended in parentheses and helps short topics.
     */
    fun hypothesis(topic: String, description: String? = null): String {
        val t = topic.trim()
        val d = description?.trim()?.takeIf { it.isNotEmpty() }
        val korean = hasHangul(t) || (d != null && hasHangul(d))
        val subject = if (d == null) t else "$t($d)"
        return if (korean) "이 글은 ${subject}에 관한 내용이다." else "This text is about ${if (d == null) t else "$t ($d)"}."
    }

    fun hasHangul(s: String): Boolean = s.any { it in '가'..'힣' || it in 'ㄱ'..'ㆎ' }

    /** Yes, no or unsure for [p] by the [yes] and [no] limits. */
    fun verdict(p: Float, yes: Float = YES, no: Float = NO): Verdict = when {
        p >= yes -> Verdict.YES
        p <= no -> Verdict.NO
        else -> Verdict.UNSURE
    }

    enum class Verdict { YES, NO, UNSURE }
}
