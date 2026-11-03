package com.flipcash.shared.chat.ui

import kotlin.test.Test

class LinkDetectionVectorJvmTest {
    @Test
    fun `spans match the cross-platform vectors`() {
        val json = javaClass.classLoader!!.getResourceAsStream("link_detection.json")!!
            .bufferedReader().use { it.readText() }
        assertSpansMatchVectors(json)
    }
}
