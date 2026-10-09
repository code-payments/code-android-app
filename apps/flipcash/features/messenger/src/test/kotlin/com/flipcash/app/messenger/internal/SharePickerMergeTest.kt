package com.flipcash.app.messenger.internal

import kotlin.test.Test
import kotlin.test.assertEquals

class SharePickerMergeTest {

    private fun List<String>.merge(previous: List<String>?) = mergeStable(previous) { it.first() }

    @Test
    fun `no previous list is taken as is`() {
        assertEquals(listOf("b1", "a1"), listOf("b1", "a1").merge(null))
    }

    @Test
    fun `rows keep their old positions and take new contents`() {
        assertEquals(listOf("b2", "a2"), listOf("a2", "b2").merge(listOf("b1", "a1")))
    }

    @Test
    fun `gone rows are dropped and new rows are appended in order`() {
        assertEquals(listOf("b1", "c1", "d1"), listOf("c1", "b1", "d1").merge(listOf("a1", "b1")))
    }
}
