package com.flipcash.app.search.internal

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchMatchingTest {

    @Test
    fun `a blank query shows Recent instead of results`() {
        assertNull(SearchMatching.query(""))
        assertNull(SearchMatching.query("   "))
    }

    @Test
    fun `the query is trimmed and folded`() {
        assertEquals("jose", SearchMatching.query("  José "))
    }

    @Test
    fun `matching ignores case and accents on both sides`() {
        val query = SearchMatching.query("JOSE")!!
        assertTrue(SearchMatching.matches(query, "José Ramírez"))
        assertTrue(SearchMatching.matches(SearchMatching.query("josé")!!, "JOSE"))
    }

    @Test
    fun `any field can match and null fields are skipped`() {
        assertTrue(SearchMatching.matches("ted", null, "teddy"))
        assertFalse(SearchMatching.matches("ted", null, "alice"))
    }
}
