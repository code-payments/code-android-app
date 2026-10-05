package com.flipcash.app.blob

/** Picks which upload-policy entry governs a MIME type. */
object ChatMediaConstraints {

    /**
     * Index of the first entry in [patterns], in policy order, that matches [mimeType]: the
     * catch-all pattern matches anything, a type wildcard matches on the type alone, and anything
     * else is an exact `type/subtype`.
     * Null when [mimeType] has no `/` or nothing matches.
     *
     * Policy order is authoritative — a later, more specific entry never overrides an earlier
     * catch-all. Must agree with iOS (`test-vectors/chat_media.json`, `constraintSelection`).
     */
    fun firstMatchIndex(patterns: List<String>, mimeType: String): Int? {
        val slash = mimeType.indexOf('/')
        if (slash < 0) return null
        val type = mimeType.substring(0, slash)

        val index = patterns.indexOfFirst { pattern ->
            when {
                pattern == "*/*" -> true
                pattern.endsWith("/*") -> pattern.dropLast(2).equals(type, ignoreCase = true)
                else -> pattern.equals(mimeType, ignoreCase = true)
            }
        }
        return index.takeIf { it >= 0 }
    }
}
