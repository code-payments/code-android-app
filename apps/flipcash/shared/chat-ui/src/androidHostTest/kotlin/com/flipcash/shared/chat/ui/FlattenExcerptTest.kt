package com.flipcash.shared.chat.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class FlattenExcerptTest {

    @Test
    fun `line breaks and the space around them fold to one space`() {
        assertEquals("v2.4 is out - Message links - Fixes", flattenExcerpt("v2.4 is out\n\n- Message links  \n  - Fixes"))
    }

    @Test
    fun `text without line breaks is unchanged`() {
        assertEquals("one  line", flattenExcerpt("one  line"))
    }
}
