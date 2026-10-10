package com.getcode.ui.scanner

import androidx.camera.core.CameraSelector
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ScannerCameraSelectionTest {

    private data class FakeCamera(val id: String, val lensFacing: Int)

    private val front = FakeCamera("front", CameraSelector.LENS_FACING_FRONT)
    private val back = FakeCamera("back", CameraSelector.LENS_FACING_BACK)
    private val external = FakeCamera("external", CameraSelector.LENS_FACING_EXTERNAL)

    @Test
    fun picksTheBackCameraWhenThereIsOne() {
        assertEquals(back, pickScannerCamera(listOf(front, back)) { it.lensFacing })
    }

    @Test
    fun fallsBackToTheOnlyFrontCamera() {
        // Infinix X688B: CameraX lists one camera, id 0, reporting LENS_FACING_FRONT.
        assertEquals(front, pickScannerCamera(listOf(front)) { it.lensFacing })
    }

    @Test
    fun fallsBackToTheFirstCameraWithoutABackOne() {
        assertEquals(external, pickScannerCamera(listOf(external, front)) { it.lensFacing })
    }

    @Test
    fun picksNothingWithoutCameras() {
        assertNull(pickScannerCamera(emptyList<FakeCamera>()) { it.lensFacing })
    }

    @Test
    fun bindWithRetryDoesNotRetryAnUnmatchedSelector() = runTest {
        var attempts = 0
        assertFailsWith<IllegalArgumentException> {
            bindWithRetry(retries = 3) {
                attempts++
                throw IllegalArgumentException("No available camera can be found")
            }
        }
        assertEquals(1, attempts)
    }

    @Test
    fun bindWithRetryRetriesOtherFailures() = runTest {
        var attempts = 0
        assertFailsWith<IllegalStateException> {
            bindWithRetry(retries = 3) {
                attempts++
                throw IllegalStateException("camera in use")
            }
        }
        assertEquals(3, attempts)
    }
}
