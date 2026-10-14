package com.flipcash.app.updates.internal

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import com.flipcash.app.core.android.VersionInfo
import com.flipcash.app.userflags.Field
import com.flipcash.app.userflags.FieldOverride
import com.flipcash.app.userflags.ResolvedFlag
import com.flipcash.app.userflags.ResolvedUserFlags
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.libs.coroutines.TestDispatcherProvider
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The forced IMMEDIATE path: the minimum-version gate, starting the update, and resuming it. */
@RunWith(RobolectricTestRunner::class)
class ForcedUpdateTest {

    private val currentVersion = 10
    private val fake = FakeAppUpdateManager(ApplicationProvider.getApplicationContext())
    private val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
    private val userFlags = mockk<UserFlagsCoordinator>(relaxed = true)

    private fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

    private fun flags(
        minimumVersion: Int?,
        override: FieldOverride<Int?> = FieldOverride.None,
        isStaff: Boolean = false,
    ) {
        val resolved = mockk<ResolvedUserFlags> {
            every { this@mockk.minimumVersion } returns ResolvedFlag(minimumVersion, override)
            every { this@mockk.isStaff } returns ResolvedFlag(isStaff, FieldOverride.None)
        }
        every { userFlags.resolvedFlags } returns MutableStateFlow(resolved)
    }

    private fun TestScope.controller() = GooglePlayAppUpdateController(
        context = activity,
        versionInfo = VersionInfo(versionCode = currentVersion),
        userFlags = userFlags,
        dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)),
        flexibleUpdates = FlexibleUpdates(fake, currentVersion, { true }, { true }),
        toastSeenStore = NoopSeenStore,
        appUpdateManager = fake,
    )

    /** Play answers on the main looper, so the check only lands once it runs. */
    private suspend fun GooglePlayAppUpdateController.check() {
        checkForUpdate()
        idleMain()
    }

    private suspend fun TestScope.start(controller: GooglePlayAppUpdateController): Result<Unit> {
        // Undispatched, so the flow has started before the looper runs Play's listeners.
        val result = async(start = CoroutineStart.UNDISPATCHED) { controller.startUpdate() }
        idleMain()
        return result.await()
    }

    @Test
    fun `at the minimum version nothing is forced`() = runTest {
        fake.setUpdateAvailable(currentVersion + 1)
        flags(minimumVersion = currentVersion)
        val controller = controller()

        controller.check()

        assertNull(controller.availableUpdate.value)
    }

    @Test
    fun `no minimum version from the server forces nothing`() = runTest {
        fake.setUpdateAvailable(currentVersion + 1)
        flags(minimumVersion = null)
        val controller = controller()

        controller.check()

        assertNull(controller.availableUpdate.value)
    }

    @Test
    fun `below the minimum version Play's update is offered`() = runTest {
        fake.setUpdateAvailable(currentVersion + 1)
        flags(minimumVersion = currentVersion + 1)
        val controller = controller()

        controller.check()

        val update = controller.availableUpdate.value
        assertEquals(UpdateAvailability.UPDATE_AVAILABLE, update?.updateAvailability)
        assertEquals(currentVersion + 1, update?.availableVersionCode)
    }

    @Test
    fun `a staff override above the current version shows the gate when Play has nothing`() = runTest {
        fake.setUpdateNotAvailable()
        flags(minimumVersion = null, override = FieldOverride.Value(currentVersion + 5))
        val controller = controller()

        controller.check()

        assertEquals(UpdateAvailability.UPDATE_AVAILABLE, controller.availableUpdate.value?.updateAvailability)
        // The gate shows, but there's no newer build for Play to install.
        assertIs<NoUpdateAvailableException>(start(controller).exceptionOrNull())
    }

    @Test
    fun `starting the update opens Play's immediate flow`() = runTest {
        fake.setUpdateAvailable(currentVersion + 1)
        flags(minimumVersion = currentVersion + 1)
        val controller = controller()
        controller.check()

        val result = start(controller)

        assertTrue(result.isSuccess)
        assertTrue(fake.isImmediateFlowVisible)
        assertFalse(fake.isConfirmationDialogVisible)
    }

    @Test
    fun `starting before a check fails`() = runTest {
        flags(minimumVersion = currentVersion + 1)

        assertIs<NoUpdateAvailableException>(start(controller()).exceptionOrNull())
    }

    @Test
    fun `an update Play only allows as flexible can't be forced`() = runTest {
        fake.setUpdateAvailable(currentVersion + 1, AppUpdateType.FLEXIBLE)
        flags(minimumVersion = currentVersion + 1)
        val controller = controller()
        controller.check()

        val result = start(controller)

        assertTrue(result.isFailure)
        assertFalse(fake.isImmediateFlowVisible)
    }

    @Test
    fun `an immediate update already under way is reported on the next check`() = runTest {
        fake.setUpdateAvailable(currentVersion + 1)
        flags(minimumVersion = currentVersion + 1)
        controller().run {
            check()
            start(this)
        }
        fake.userAcceptsUpdate()
        fake.downloadStarts()

        // The app comes back, e.g. after the user left Play's screen, and checks again.
        val resumed = controller()
        resumed.check()
        idleMain()

        assertEquals(
            UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS,
            resumed.availableUpdate.value?.updateAvailability,
        )
        // The resume branch then calls startUpdate(), but Play's fake reports IMMEDIATE as not
        // allowed while one is under way, so the "type not allowed" guard stops it. Whether real
        // Play does the same is unconfirmed; this asserts only the status the gate sees.
    }

    @Test
    fun `reset clears a staff override`() = runTest {
        fake.setUpdateNotAvailable()
        flags(minimumVersion = null, override = FieldOverride.Value(currentVersion + 5), isStaff = true)
        val controller = controller()
        controller.check()

        controller.reset()

        assertNull(controller.availableUpdate.value)
        verify { userFlags.clear(Field.MinimumVersion) }
    }

    @Test
    fun `reset does nothing for non-staff`() = runTest {
        fake.setUpdateNotAvailable()
        flags(minimumVersion = null, override = FieldOverride.Value(currentVersion + 5))
        val controller = controller()
        controller.check()

        controller.reset()

        assertEquals(UpdateAvailability.UPDATE_AVAILABLE, controller.availableUpdate.value?.updateAvailability)
        verify(exactly = 0) { userFlags.clear(any()) }
    }

    private object NoopSeenStore : UpdateToastSeenStore {
        override val lastShownVersionCode = MutableStateFlow<Int?>(null)
        override suspend fun markShown(versionCode: Int) = Unit
    }
}
