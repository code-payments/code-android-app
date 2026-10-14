package com.flipcash.app.updates.internal

import android.app.Activity
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.flipcash.app.core.android.VersionInfo
import com.flipcash.app.updates.OptionalUpdate
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.libs.coroutines.TestDispatcherProvider
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class FlexibleUpdatesTest {

    private val currentVersion = 10
    private val fake = FakeAppUpdateManager(ApplicationProvider.getApplicationContext())
    private var foreground = true

    private fun flexibleUpdates(isStoreBuild: Boolean = true) = FlexibleUpdates(
        manager = fake,
        currentVersionCode = currentVersion,
        isStoreBuild = { isStoreBuild },
        isInForeground = { foreground },
    )

    private fun idleMain() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun `reads priority and treats unknown staleness as zero`() = runTest {
        fake.setUpdateAvailable(currentVersion + 1)
        fake.setUpdatePriority(4)
        val updates = flexibleUpdates()

        updates.check()

        assertEquals(OptionalUpdate(versionCode = 11, priority = 4, stalenessDays = 0), updates.update.value)
    }

    @Test
    fun `reads staleness`() = runTest {
        fake.setUpdateAvailable(currentVersion + 1)
        fake.setClientVersionStalenessDays(8)
        val updates = flexibleUpdates()

        updates.check()

        assertEquals(8, updates.update.value?.stalenessDays)
    }

    @Test
    fun `no update when Play has none`() = runTest {
        fake.setUpdateNotAvailable()
        val updates = flexibleUpdates()

        updates.check()

        assertNull(updates.update.value)
    }

    @Test
    fun `non-store builds never offer an optional update`() = runTest {
        fake.setUpdateAvailable(currentVersion + 1)
        fake.setUpdatePriority(5)
        val updates = flexibleUpdates(isStoreBuild = false)

        updates.check()

        assertNull(updates.update.value)
    }

    @Test
    fun `a download finishing in the background installs straight away`() = runTest {
        fake.setUpdateAvailable(currentVersion + 1)
        val updates = flexibleUpdates()
        updates.check()

        assertTrue(updates.start(activity()).isSuccess)
        idleMain()
        assertTrue(fake.isConfirmationDialogVisible)
        fake.userAcceptsUpdate()
        fake.downloadStarts()
        foreground = false
        fake.downloadCompletes()

        assertTrue(updates.downloaded.value)
        assertTrue(fake.isInstallSplashScreenVisible)
    }

    @Test
    fun `a download finishing in the foreground waits for Restart`() = runTest {
        fake.setUpdateAvailable(currentVersion + 1)
        val updates = flexibleUpdates()
        updates.check()

        updates.start(activity())
        idleMain()
        fake.userAcceptsUpdate()
        fake.downloadStarts()
        fake.downloadCompletes()

        assertTrue(updates.downloaded.value)
        assertFalse(fake.isInstallSplashScreenVisible)

        updates.complete()

        assertTrue(fake.isInstallSplashScreenVisible)
    }

    @Test
    fun `starting with no update fails`() {
        assertTrue(flexibleUpdates().start(activity()).isFailure)
    }

    @Test
    fun `an optional update leaves the forced update empty, and shows its toast once`() = runTest {
        fake.setUpdateAvailable(currentVersion + 1)
        fake.setUpdatePriority(5)
        val seen = InMemorySeenStore()
        val controller = GooglePlayAppUpdateController(
            context = ApplicationProvider.getApplicationContext(),
            versionInfo = VersionInfo(versionCode = currentVersion),
            userFlags = mockk<UserFlagsCoordinator>(relaxed = true),
            dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)),
            flexibleUpdates = flexibleUpdates(),
            toastSeenStore = seen,
        )

        controller.checkForOptionalUpdate()

        assertNull(controller.availableUpdate.value)
        val update = controller.updateToast.value
        assertEquals(11, update?.versionCode)

        controller.markUpdateToastShown(update!!)

        assertNull(controller.updateToast.value)
        assertEquals(11, controller.optionalUpdate.value?.versionCode)
    }

    private fun activity(): Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    private class InMemorySeenStore : UpdateToastSeenStore {
        override val lastShownVersionCode = MutableStateFlow<Int?>(null)
        override suspend fun markShown(versionCode: Int) {
            lastShownVersionCode.value = versionCode
        }
    }
}
