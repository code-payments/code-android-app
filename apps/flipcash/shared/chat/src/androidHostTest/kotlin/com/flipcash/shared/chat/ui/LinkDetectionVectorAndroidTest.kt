package com.flipcash.shared.chat.ui

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LinkDetectionVectorAndroidTest {
    @Test
    fun `spans match the cross-platform vectors`() {
        val json = javaClass.classLoader!!.getResourceAsStream("link_detection.json")!!
            .bufferedReader().use { it.readText() }
        assertSpansMatchVectors(json)
    }
}
