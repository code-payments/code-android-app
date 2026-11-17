package com.flipcash.app.featureflags.internal

import androidx.test.core.app.ApplicationProvider
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.libs.coroutines.TestDispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Logout runs [InternalFeatureFlagController.reset]. The Group Money onboarding gate is read on
 * the signed-out intro screen as `GroupMoneyUi && beta override`, so both have to survive it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InternalFeatureFlagControllerTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val controller = InternalFeatureFlagController(
        context = ApplicationProvider.getApplicationContext(),
        dispatchers = TestDispatcherProvider(dispatcher),
    )

    // A flag that is still meant to be cleared on logout.
    private val clearedOnLogout = FeatureFlag.entries.first {
        !it.persistLogOut && !it.launched && !it.isOptionFlag && it != FeatureFlag.GroupMoneyUi
    }

    @Test
    fun `logout keeps the beta override and the Group Money flag`() = runTest(dispatcher) {
        controller.enableBetaFeatures()
        controller.set(FeatureFlag.GroupMoneyUi, true)
        controller.set(clearedOnLogout, true)
        testScheduler.advanceUntilIdle()

        controller.reset()
        testScheduler.advanceUntilIdle()

        assertTrue(controller.getOverride())
        assertTrue(controller.get(FeatureFlag.GroupMoneyUi))
        assertFalse(controller.get(clearedOnLogout))
    }

    @Test
    fun `clearing flags restored from a backup still clears the override and Group Money`() =
        runTest(dispatcher) {
            controller.enableBetaFeatures()
            controller.set(FeatureFlag.GroupMoneyUi, true)
            testScheduler.advanceUntilIdle()

            controller.clearRestoredFlags()
            testScheduler.advanceUntilIdle()

            assertFalse(controller.getOverride())
            assertFalse(controller.get(FeatureFlag.GroupMoneyUi))
        }
}
