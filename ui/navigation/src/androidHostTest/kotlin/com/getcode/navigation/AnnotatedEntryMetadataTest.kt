package com.getcode.navigation

import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import org.junit.Test
import kotlin.test.assertEquals

private data object DemoWrapSheet : Sheet, WrapContentSheet

/**
 * [annotatedEntry]'s `metadata` replaces single keys of the type-derived metadata, so one
 * registration can present a [WrapContentSheet] at full height without changing the route's type.
 */
class AnnotatedEntryMetadataTest {

    private fun metadataOf(override: Map<String, Any>): Map<String, Any> =
        entryProvider<NavKey> {
            annotatedEntry<DemoWrapSheet>(metadata = override) { }
        }(DemoWrapSheet).metadata

    @Test
    fun `without an override the entry carries the type's metadata`() {
        assertEquals(DemoWrapSheet::class.metadata(), metadataOf(emptyMap()))
        assertEquals(true, metadataOf(emptyMap())[NavMetadataKeys.IsWrapContentSheet.key])
    }

    @Test
    fun `an override replaces only the keys it names`() {
        val metadata = metadataOf(mapOf(NavMetadataKeys.IsWrapContentSheet.key to false))

        assertEquals(false, metadata[NavMetadataKeys.IsWrapContentSheet.key])
        assertEquals(true, metadata[NavMetadataKeys.IsSheet.key])
        assertEquals(
            DemoWrapSheet::class.metadata() - NavMetadataKeys.IsWrapContentSheet.key,
            metadata - NavMetadataKeys.IsWrapContentSheet.key,
        )
    }
}
