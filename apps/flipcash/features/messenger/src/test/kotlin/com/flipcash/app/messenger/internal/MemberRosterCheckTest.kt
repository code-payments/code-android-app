package com.flipcash.app.messenger.internal

import com.flipcash.app.analytics.RecordingAnalytics
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.shared.chat.GroupRosters
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The member sheet's membership answer: the held roster, one read when none is held, or none. */
class MemberRosterCheckTest {

    private val chatId = ChatId(ByteArray(32) { 3 }.toList())
    private val held = listOf(ChatMember(listOf(5), UserProfile.Empty, emptyList()))
    private val rosters = MutableStateFlow<Map<ChatId, List<ChatMember>>>(emptyMap())
    private val groupRosters = mockk<GroupRosters>(relaxed = true) { every { state } returns rosters }

    private fun viewModel() = MemberModerationViewModel(
        mockk(relaxed = true), mockk(relaxed = true), groupRosters, RecordingAnalytics(), mockk(relaxed = true),
        GroupRestrictionWrites(),
    )

    @Test
    fun `a held roster is used without a read`() = runTest {
        rosters.value = mapOf(chatId to held)
        assertEquals(held, viewModel().ensureRoster(chatId))
        coVerify(exactly = 0) { groupRosters.load(any()) }
    }

    @Test
    fun `no roster held is read before the answer`() = runTest {
        coEvery { groupRosters.load(chatId) } returns Result.success(held)
        assertNull(viewModel().cachedRoster(chatId))
        assertEquals(held, viewModel().ensureRoster(chatId))
        coVerify(exactly = 1) { groupRosters.load(chatId) }
    }

    @Test
    fun `a failed read leaves membership unconfirmed`() = runTest {
        coEvery { groupRosters.load(chatId) } returns Result.failure(IllegalStateException("denied"))
        assertNull(viewModel().ensureRoster(chatId))
    }
}
