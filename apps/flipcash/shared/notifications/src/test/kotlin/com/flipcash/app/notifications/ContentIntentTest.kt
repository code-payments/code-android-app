package com.flipcash.app.notifications

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import com.flipcash.services.models.NotificationPayload
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ContentIntentTest {

    private val context = RuntimeEnvironment.getApplication()
    private val launcher = ComponentName(context, "com.flipcash.app.LauncherActivity")

    // Not created: the tap path needs none of the injected dependencies.
    private val service = Robolectric.buildService(NotificationService::class.java).get()

    init {
        shadowOf(context.packageManager).apply {
            addActivityIfNotPresent(launcher)
            addIntentFilterForActivity(
                launcher,
                IntentFilter(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_LAUNCHER) },
            )
        }
    }

    @Test
    fun `a push with no payload opens the app without navigating`() {
        // The test push from the notification status page carries no payload.
        val pendingIntent = with(service) { context.buildContentIntent(chatType = null, navigation = null) }

        val shadow = shadowOf(pendingIntent)
        assertTrue(shadow.isActivityIntent)
        assertEquals(launcher, shadow.savedIntent.component)
        assertNull(shadow.savedIntent.data)
    }

    @Test
    fun `a payload that does not decode is read as no payload`() {
        assertNull(NotificationPayload.fromEncoded("not a payload"))
    }
}
