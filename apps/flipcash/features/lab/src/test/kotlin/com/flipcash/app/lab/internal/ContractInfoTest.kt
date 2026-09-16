package com.flipcash.app.lab.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContractInfoTest {

    @Test
    fun `both contracts are listed, in a stable order`() {
        val names = contractInfo().map { it.name }
        assertEquals(listOf("ocp", "flipcash2"), names)
    }

    @Test
    fun `detail joins the version and the short commit`() {
        val row = ContractInfo(name = "ocp", version = "0.5.0", commit = "82202912", isLocal = false)
        assertEquals("0.5.0 · 82202912", row.detail)
        assertFalse(row.isLocal)
    }

    @Test
    fun `a local contract keeps LOCAL in the detail and flags itself`() {
        val row = ContractInfo(name = "ocp", version = "0.5.0", commit = "LOCAL", isLocal = true)
        assertEquals("0.5.0 · LOCAL", row.detail)
        assertTrue(row.isLocal)
    }

    @Test
    fun `the real packages report a non-empty version and commit`() {
        contractInfo().forEach { row ->
            assertTrue(row.version.isNotEmpty(), "${row.name} has no version")
            assertTrue(row.commit.isNotEmpty(), "${row.name} has no commit")
        }
    }
}
