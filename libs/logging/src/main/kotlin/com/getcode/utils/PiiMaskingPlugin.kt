package com.getcode.utils

import java.net.URI

class PiiMaskingPlugin : TraceLogPlugin {
    companion object {
        // E.164 (+1234567890) and parenthesized area code ((123) 456-7890)
        private val PHONE_REGEX = Regex("""(?:\+\d[\d\s\-().]{7,14}\d|\(\d{3}\)\s*\d{3}[\s\-]?\d{4})""")

        // Standard email pattern
        private val EMAIL_REGEX = Regex("""[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}""")

        // Solana public keys: base58, 32-44 chars
        private val SOLANA_KEY_REGEX = Regex("""\b[1-9A-HJ-NP-Za-km-z]{32,44}\b""")

        // JWT tokens: three base64url segments separated by dots
        private val JWT_REGEX = Regex("""eyJ[a-zA-Z0-9_-]+\.eyJ[a-zA-Z0-9_-]+\.[a-zA-Z0-9_-]+""")

        // URLs with a scheme (scheme://...) up to next whitespace
        private val URL_REGEX = Regex("""[a-zA-Z][a-zA-Z0-9+\-.]*://\S+""")
    }

    private fun maskUrl(raw: String): String {
        val stripped = try {
            val uri = URI(raw)
            val base = buildString {
                append(uri.scheme)
                append("://")
                if (uri.authority != null) append(uri.authority)
                if (uri.path != null) append(uri.path)
            }
            base
        } catch (_: Exception) {
            raw.substringBefore('?').substringBefore('#')
        }
        return "[URL:$stripped]"
    }

    override fun process(line: String): String? {
        var result = line
        result = JWT_REGEX.replace(result, "[JWT]")
        result = URL_REGEX.replace(result) { maskUrl(it.value) }
        result = EMAIL_REGEX.replace(result, "[EMAIL]")
        result = SOLANA_KEY_REGEX.replace(result) { match ->
            val key = match.value
            if (key.length >= 8) {
                "[KEY:${key.take(4)}...${key.takeLast(4)}]"
            } else {
                "[KEY]"
            }
        }
        result = PHONE_REGEX.replace(result, "[PHONE]")
        return result
    }
}
