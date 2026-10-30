package com.flipcash.app.messenger.internal.invites

import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.ApproveNewMembersStore
import com.flipcash.shared.chat.HostInvite
import com.flipcash.shared.chat.HostInvites
import com.flipcash.shared.chat.InviteCreation
import com.getcode.manager.BottomBarManager
import com.getcode.util.resources.ResourceHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The host sheet's Approve New Members switch: only the host's tap turns it on. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class InviteHostSheetViewModelTest {

    private val chatId = ChatId(ByteArray(32) { 5 })
    private val hostInvites = mockk<HostInvites>(relaxed = true)
    private val resources = mockk<ResourceHelper>(relaxed = true) {
        every { getString(any<Int>()) } returns "text"
    }

    /** Every value written, in order, behind a switch that starts off. */
    private val writes = mutableListOf<Boolean>()
    private val stored = MutableStateFlow(false)
    private val approveStore = object : ApproveNewMembersStore {
        override fun observe(chatId: ChatId): Flow<Boolean> = stored
        override suspend fun set(chatId: ChatId, on: Boolean) {
            writes += on
            stored.value = on
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        BottomBarManager.clear()
        every { hostInvites.state } returns MutableStateFlow<Map<ChatId, List<HostInvite>>>(emptyMap())
        coEvery { hostInvites.load(chatId) } returns Result.success(emptyList())
        every { hostInvites.current(chatId) } returns null
    }

    @After
    fun tearDown() {
        BottomBarManager.clear()
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        InviteHostSheetViewModel(hostInvites, approveStore, resources, RecordingAnalytics())

    @Test
    fun `1e shares the plain link and leaves Approve New Members off`() = runTest {
        coEvery { hostInvites.currentOrCreate(chatId) } returns InviteCreation.NotAvailable
        val model = viewModel()
        model.open(chatId)

        model.onShare()
        BottomBarManager.messages.value.single().actions.first().onClick()

        assertEquals(InviteHostSheetViewModel.Effect.ShareJoin, model.effects.first())
        assertFalse(model.state.value.approve)
        assertTrue("nothing turned the switch on: $writes", writes.none { it })
    }

    @Test
    fun `the host's tap on the switch turns it on`() = runTest {
        val model = viewModel()
        model.open(chatId)

        model.onApproveChanged(true)

        assertTrue(model.state.value.approve)
        assertEquals(listOf(true), writes)
    }
}
