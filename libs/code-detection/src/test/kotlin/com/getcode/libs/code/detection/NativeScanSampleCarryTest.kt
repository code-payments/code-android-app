package com.getcode.libs.code.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NativeScanSampleCarryTest {

    @Test
    fun `QrCode result carries no native sample by default`() {
        val result: CodeScanResult = CodeScanResult.QrCode(results = listOf("x"))
        assertNull(result.nativeScan)
    }

    @Test
    fun `interface exposes a nativeScan slot defaulting to null`() {
        val result: CodeScanResult = object : CodeScanResult {}
        assertNull(result.nativeScan)
    }

    @Test
    fun `sample fields round-trip`() {
        val sample = NativeScanSample(durationMs = 42, hit = true, width = 640, height = 480, quality = 3)
        assertEquals(42L, sample.durationMs)
        assertEquals(true, sample.hit)
    }
}
