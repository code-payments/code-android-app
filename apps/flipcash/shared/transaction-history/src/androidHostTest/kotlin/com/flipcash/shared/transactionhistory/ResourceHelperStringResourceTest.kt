package com.flipcash.shared.transactionhistory

import androidx.test.core.app.ApplicationProvider
import com.flipcash.shared.transactionhistory.generated.resources.Res
import com.flipcash.shared.transactionhistory.generated.resources.title_activity_sentTo
import com.getcode.util.resources.AndroidResources
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

/**
 * The transaction mappers build display text outside composition from a Compose [Res] string. On Android
 * that goes back to `R` by the resource's key, so this checks the key still finds the real Android string.
 */
@RunWith(RobolectricTestRunner::class)
class ResourceHelperStringResourceTest {

    @Test
    fun `compose string resource resolves through Android resources`() {
        val resources = AndroidResources(ApplicationProvider.getApplicationContext())

        assertEquals("Sent to Ann", resources.getString(Res.string.title_activity_sentTo, "Ann"))
    }
}
