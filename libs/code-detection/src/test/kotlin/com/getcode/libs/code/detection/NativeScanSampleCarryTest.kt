package com.getcode.libs.code.detection

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
}
