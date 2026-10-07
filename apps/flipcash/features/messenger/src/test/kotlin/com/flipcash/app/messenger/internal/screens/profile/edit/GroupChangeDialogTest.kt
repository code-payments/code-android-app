package com.flipcash.app.messenger.internal.screens.profile.edit

import androidx.test.core.app.ApplicationProvider
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import android.content.Context
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The iOS `GroupChangeDialogTests`: the prompt in front of every group edit, per field. */
@RunWith(RobolectricTestRunner::class)
class GroupChangeDialogTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val resources = mockk<ResourceHelper>().also {
        every { it.getString(any()) } answers { context.getString(firstArg()) }
    }

    @Before
    fun setUp() = BottomBarManager.clear()

    @After
    fun tearDown() = BottomBarManager.clear()

    private val expected = mapOf(
        GroupChangeField.Name to ("Group Name" to "This will change the group name for everyone in it"),
        GroupChangeField.Picture to ("Group Picture" to "This will change the group picture for everyone in it"),
        GroupChangeField.Cover to ("Group Cover" to "This will change the group cover for everyone in it"),
        GroupChangeField.Description to ("Group Description" to "This will change the group description for everyone in it"),
    )

    @Test
    fun `each field names itself in the title and the action, and says who it affects`() {
        expected.forEach { (field, copy) ->
            BottomBarManager.clear()
            showGroupChangeConfirmation(resources, field) {}

            val message = BottomBarManager.messages.value.single()
            assertEquals("Change ${copy.first}?", message.title, field.name)
            assertEquals(copy.second, message.subtitle, field.name)
            assertEquals("Change ${copy.first}", message.actions.first().text.text, field.name)
            assertTrue(message.title.contains("Group"))
            assertFalse(message.subtitle.contains("your"))
        }
    }

    @Test
    fun `the change action is the only one and cancel follows it`() {
        showGroupChangeConfirmation(resources, GroupChangeField.Name) {}

        val message = BottomBarManager.messages.value.single()
        assertEquals(1, message.actions.size)
        assertTrue(message.showCancel)
    }

    @Test
    fun `only the change action runs the handler`() {
        var confirmed = 0
        showGroupChangeConfirmation(resources, GroupChangeField.Name) { confirmed++ }

        BottomBarManager.clear()
        assertEquals(0, confirmed)

        showGroupChangeConfirmation(resources, GroupChangeField.Name) { confirmed++ }
        BottomBarManager.messages.value.last().actions.first().onClick()
        assertEquals(1, confirmed)
    }
}
