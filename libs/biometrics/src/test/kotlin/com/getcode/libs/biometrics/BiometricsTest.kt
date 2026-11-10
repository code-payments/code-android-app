package com.getcode.libs.biometrics

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BiometricsTest {

    @Test
    fun `a device with no authenticator cannot prompt`() {
        assertFalse(Biometrics.canPromptWith(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE))
        assertFalse(Biometrics.canPromptWith(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED))
        assertFalse(Biometrics.canPromptWith(BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED))
    }

    @Test
    fun `a transient or unknown status still prompts`() {
        assertTrue(Biometrics.canPromptWith(BiometricManager.BIOMETRIC_SUCCESS))
        assertTrue(Biometrics.canPromptWith(BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE))
        assertTrue(Biometrics.canPromptWith(BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED))
        assertTrue(Biometrics.canPromptWith(BiometricManager.BIOMETRIC_STATUS_UNKNOWN))
    }

    @Test
    fun `prompt error codes map to their errors`() {
        assertEquals(BiometricsError.HardwareUnavailable, BiometricsError.fromValue(BiometricPrompt.ERROR_HW_UNAVAILABLE))
        assertEquals(BiometricsError.UnableToProcess, BiometricsError.fromValue(BiometricPrompt.ERROR_UNABLE_TO_PROCESS))
        assertEquals(BiometricsError.Timeout, BiometricsError.fromValue(BiometricPrompt.ERROR_TIMEOUT))
        assertEquals(BiometricsError.NoSpace, BiometricsError.fromValue(BiometricPrompt.ERROR_NO_SPACE))
        assertEquals(BiometricsError.Cancelled, BiometricsError.fromValue(BiometricPrompt.ERROR_CANCELED))
        assertEquals(BiometricsError.Vendor, BiometricsError.fromValue(BiometricPrompt.ERROR_VENDOR))
        assertEquals(BiometricsError.Lockout, BiometricsError.fromValue(BiometricPrompt.ERROR_LOCKOUT))
        assertEquals(BiometricsError.LockoutPermanent, BiometricsError.fromValue(BiometricPrompt.ERROR_LOCKOUT_PERMANENT))
        assertEquals(BiometricsError.UserCancelled, BiometricsError.fromValue(BiometricPrompt.ERROR_USER_CANCELED))
        assertEquals(BiometricsError.NoBiometrics, BiometricsError.fromValue(BiometricPrompt.ERROR_NO_BIOMETRICS))
        assertEquals(BiometricsError.HardwareNotPresent, BiometricsError.fromValue(BiometricPrompt.ERROR_HW_NOT_PRESENT))
        assertEquals(BiometricsError.NegativeButton, BiometricsError.fromValue(BiometricPrompt.ERROR_NEGATIVE_BUTTON))
        assertEquals(BiometricsError.NoDeviceCredential, BiometricsError.fromValue(BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL))
        assertEquals(BiometricsError.Unknown, BiometricsError.fromValue(BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED))
    }

    @Test
    fun `only errors that leave no authenticator are unrecoverable`() {
        val unrecoverable = BiometricsError.entries.filter { it.isUnrecoverable }.toSet()
        assertEquals(
            setOf(
                BiometricsError.NoBiometrics,
                BiometricsError.HardwareNotPresent,
                BiometricsError.NoDeviceCredential,
            ),
            unrecoverable,
        )
    }

    @Test
    fun `lockouts and transient errors are not dismissals`() {
        assertFalse(BiometricsError.Lockout.isDismissal)
        assertFalse(BiometricsError.LockoutPermanent.isDismissal)
        assertFalse(BiometricsError.HardwareUnavailable.isDismissal)
        assertTrue(BiometricsError.UserCancelled.isDismissal)
        assertTrue(BiometricsError.Cancelled.isDismissal)
        assertTrue(BiometricsError.NegativeButton.isDismissal)
    }
}
