package com.getcode.utils

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guards the overload resolution of [MetadataBuilder.to].
 *
 * When the parameter was a non-null `Any`, a nullable argument made the member
 * inapplicable and the call resolved to [kotlin.to] instead — building a [Pair]
 * that was discarded in statement position. The field was dropped silently, with
 * no error and at most an unused-expression warning. These tests fail if the
 * parameter is ever narrowed back to a non-null type.
 */
class MetadataBuilderTest {

    private fun build(block: MetadataBuilder.() -> Unit): Map<String, Any> =
        MetadataBuilder().apply(block).build()

    @Test
    fun `nullable String holding a value is recorded`() {
        val present: String? = "hello"

        val result = build { "value" to present }

        assertEquals(mapOf("value" to "hello"), result)
    }

    @Test
    fun `nullable String holding null is recorded as the placeholder`() {
        val absent: String? = null

        val result = build { "value" to absent }

        assertEquals(mapOf("value" to MetadataBuilder.NULL_PLACEHOLDER), result)
    }

    @Test
    fun `both nullable cases appear in the same map`() {
        val present: String? = "hello"
        val absent: String? = null

        val result = build {
            "present" to present
            "absent" to absent
        }

        assertEquals(
            mapOf("present" to "hello", "absent" to MetadataBuilder.NULL_PLACEHOLDER),
            result,
        )
    }

    @Test
    fun `non-null values are recorded unchanged`() {
        val result = build {
            "string" to "text"
            "int" to 1
            "boolean" to true
        }

        assertEquals(mapOf("string" to "text", "int" to 1, "boolean" to true), result)
    }

    @Test
    fun `nullable non-String types are recorded`() {
        val count: Int? = null
        val flag: Boolean? = false

        val result = build {
            "count" to count
            "flag" to flag
        }

        assertEquals(
            mapOf("count" to MetadataBuilder.NULL_PLACEHOLDER, "flag" to false),
            result,
        )
    }

    @Test
    fun `every pair reaches the map`() {
        val absent: String? = null

        val result = build {
            "a" to "one"
            "b" to absent
            "c" to 3
        }

        assertEquals(3, result.size)
    }

    @Test
    fun `a repeated key keeps the last value`() {
        val result = build {
            "key" to "first"
            "key" to "second"
        }

        assertEquals(mapOf("key" to "second"), result)
    }
}
