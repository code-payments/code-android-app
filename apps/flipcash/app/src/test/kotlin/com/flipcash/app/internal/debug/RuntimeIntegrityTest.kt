package com.flipcash.app.internal.debug

import android.content.Context
import android.os.Build
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuntimeIntegrityTest {

    private val api34Probe = FrameworkMethod(
        owner = Context::class.java,
        name = "getDeviceId",
        parameterTypes = emptyList(),
        sinceSdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
    )

    @Test
    fun `a runtime missing a method it promises is dishonest`() {
        val honest = declaresMethodsForApiLevel(
            sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            declaresMethod = { false },
            probes = listOf(api34Probe),
        )

        assertFalse(honest)
    }

    @Test
    fun `a runtime providing everything it promises is honest`() {
        val honest = declaresMethodsForApiLevel(
            sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            declaresMethod = { true },
            probes = listOf(api34Probe),
        )

        assertTrue(honest)
    }

    @Test
    fun `an older runtime is not faulted for methods it never promised`() {
        var probed = false

        val honest = declaresMethodsForApiLevel(
            sdkInt = Build.VERSION_CODES.TIRAMISU,
            declaresMethod = { probed = true; false },
            probes = listOf(api34Probe),
        )

        assertTrue(honest)
        assertFalse(probed, "probes above the reported API level must not run")
    }

    @Test
    fun `one missing method among several is enough`() {
        val present = api34Probe.copy(name = "getPackageName")

        val honest = declaresMethodsForApiLevel(
            sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            declaresMethod = { it.name == present.name },
            probes = listOf(present, api34Probe),
        )

        assertFalse(honest)
    }

    @Test
    fun `reflective probe finds a method that exists and misses one that does not`() {
        assertTrue(declaresMethod(api34Probe.copy(name = "getPackageName")))
        assertFalse(declaresMethod(api34Probe.copy(name = "noSuchMethodAnywhere")))
    }
}
