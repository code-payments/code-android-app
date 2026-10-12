package com.flipcash.app.internal.debug

import android.provider.Settings
import com.flipcash.app.updates.ReleaseStage
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, application = android.app.Application::class)
class TestLabTest {

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `a Test Lab device reports the TestLab stage whatever the track`() {
        ReleaseStage.entries.forEach { stage ->
            assertEquals(TestLabStage, bugsnagReleaseStage(stage, onTestLab = true))
        }
        assertEquals(TestLabStage, bugsnagReleaseStage(null, onTestLab = true))
    }

    @Test
    fun `any other device reports its track`() {
        assertEquals("Internal", bugsnagReleaseStage(ReleaseStage.Internal, onTestLab = false))
        assertEquals("Production", bugsnagReleaseStage(ReleaseStage.Production, onTestLab = false))
    }

    @Test
    fun `an unresolved track on any other device is Unknown`() {
        assertEquals("Unknown", bugsnagReleaseStage(null, onTestLab = false))
    }

    @Test
    fun `the Test Lab setting marks the device`() {
        Settings.System.putString(context.contentResolver, "firebase.test.lab", "true")

        assertTrue(TestLab.isRunning(context))
    }

    @Test
    fun `a device without the setting is not Test Lab`() {
        assertFalse(TestLab.isRunning(context))
    }

    @Test
    fun `a setting other than true is not Test Lab`() {
        Settings.System.putString(context.contentResolver, "firebase.test.lab", "false")

        assertFalse(TestLab.isRunning(context))
    }
}
