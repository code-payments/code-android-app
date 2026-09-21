package com.flipcash.reporting

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReportDescriptionTest {

    @Test
    fun `every reason has the token the server expects`() {
        assertEquals("spam", ReportReason.Spam.token)
        assertEquals("scam_or_fraud", ReportReason.ScamOrFraud.token)
        assertEquals("harassment", ReportReason.Harassment.token)
        assertEquals("sexual_content", ReportReason.SexualContent.token)
        assertEquals("violence", ReportReason.Violence.token)
        assertEquals("other", ReportReason.Other.token)
    }

    @Test
    fun `tokens are unique`() {
        val tokens = ReportReason.entries.map { it.token }
        assertEquals(tokens.size, tokens.toSet().size)
    }

    @Test
    fun `a reason with no details is the bare token`() {
        assertEquals("spam", ReportDescription.build(ReportReason.Spam, null))
        assertEquals("spam", ReportDescription.build(ReportReason.Spam, ""))
        assertEquals("spam", ReportDescription.build(ReportReason.Spam, "   "))
        assertEquals("spam", ReportDescription.build(ReportReason.Spam, "\n\t "))
    }

    @Test
    fun `details follow the token on the next line`() {
        assertEquals(
            "scam_or_fraud\nHe asked me to send cash to unlock a withdrawal",
            ReportDescription.build(
                ReportReason.ScamOrFraud,
                "He asked me to send cash to unlock a withdrawal",
            ),
        )
    }

    @Test
    fun `details are trimmed but internal newlines are kept`() {
        assertEquals(
            "other\nfirst\nsecond",
            ReportDescription.build(ReportReason.Other, "  first\nsecond  "),
        )
    }

    @Test
    fun `details are truncated at the cap`() {
        val long = "x".repeat(ReportDescription.MAX_DETAILS_LENGTH + 50)
        val built = ReportDescription.build(ReportReason.Other, long)
        assertEquals("other\n" + "x".repeat(ReportDescription.MAX_DETAILS_LENGTH), built)
        assertTrue(built.length < 8192, "must stay well under the contract's max_len")
    }

    @Test
    fun `the first line is always a known token`() {
        val tokens = ReportReason.entries.map { it.token }.toSet()
        for (reason in ReportReason.entries) {
            val built = ReportDescription.build(reason, "some details")
            assertTrue(built.substringBefore('\n') in tokens)
        }
    }
}
