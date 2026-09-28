package com.flipcash.shared.chat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.sources.ChatMemberDataSource
import com.flipcash.app.persistence.sources.mapper.chat.ChatEntityMapper
import com.flipcash.services.controllers.EventStreamingController
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMember
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.ChatUpdate
import com.flipcash.services.models.chat.MetadataUpdate
import com.flipcash.shared.chat.internal.ChatStateHolder
import com.flipcash.shared.chat.internal.delegates.EventStreamDelegate
import com.getcode.opencode.model.core.ID
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * A `FullRefresh` carries the chat's metadata, and for a group its `members` are only a page of
 * the roster. Replacing the cached members with that page deleted everyone else the device held,
 * so a group whose last message came from one of them lost the sender's name in the Chats list
 * preview. Runs against the real member table, because the bug is in what the write keeps.
 */
@RunWith(RobolectricTestRunner::class)
class FullRefreshMembersTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val chatId = ChatId("aabbccdd")

    private val a: ID = listOf(1)
    private val b: ID = listOf(2)
    private val c: ID = listOf(3)

    private val memberDataSource = ChatMemberDataSource(ChatEntityMapper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before
    fun setUp() {
        FlipcashDatabase.init(context, "full-refresh-members-test")
        runBlocking { memberDataSource.clear() }
    }

    @After
    fun tearDown() {
        scope.cancel()
        FlipcashDatabase.closeDb()
    }

    private fun member(id: ID, name: String) = ChatMember(
        userId = id,
        userProfile = UserProfile(
            displayName = name,
            socialAccounts = emptyList(),
            phoneNumber = null,
            email = null,
        ),
        pointers = emptyList(),
    )

    private fun fullRefresh(type: ChatType, members: List<ChatMember>) = ChatUpdate(
        chatId = chatId,
        metadataUpdates = listOf(
            MetadataUpdate.FullRefresh(
                ChatMetadata(
                    chatId = chatId,
                    type = type,
                    members = members,
                    lastMessage = null,
                    lastActivity = Instant.fromEpochSeconds(1_000),
                ),
            ),
        ),
    )

    /** Streams [update] through the delegate, then waits for [renamedA] to land. */
    private suspend fun apply(update: ChatUpdate, renamedA: String) {
        val streaming = mockk<EventStreamingController>(relaxed = true)
        every { streaming.isConnected } returns true
        every { streaming.chatUpdates } returns flowOf(update)

        EventStreamDelegate(
            eventStreamingController = streaming,
            messagingController = mockk(relaxed = true),
            metadataDataSource = mockk(relaxed = true),
            messageDataSource = mockk(relaxed = true),
            memberDataSource = memberDataSource,
            tokenCoordinator = mockk(relaxed = true),
            userManager = mockk(relaxed = true),
            stateHolder = ChatStateHolder(),
            analytics = mockk(relaxed = true),
            exchange = mockk(relaxed = true),
        ).apply {
            initialize(scope)
            open()
        }

        // The refresh renames A, and both writes commit A with the rest of the change in one
        // transaction — so once A's new name is visible, whatever the write did to B and C is too.
        withTimeout(5_000) {
            memberDataSource.observeMembers(chatId).first { members ->
                members.any { it.userId == a && it.userProfile.displayName == renamedA }
            }
        }
    }

    private suspend fun storedNames(): Map<ID, String> =
        memberDataSource.getMembersForChat(chatId).associate { it.userId to it.userProfile.displayName }

    @Test
    fun `a group refresh keeps the members its page leaves out`() = runBlocking {
        memberDataSource.upsert(chatId, listOf(member(a, "A"), member(b, "B"), member(c, "C")))

        apply(fullRefresh(ChatType.GROUP, listOf(member(a, "A2"))), renamedA = "A2")

        // C is who the Chats list names when C sent the last message; the row reads the members
        // it is handed off this table.
        assertEquals(mapOf(a to "A2", b to "B", c to "C"), storedNames())
    }

    @Test
    fun `a DM refresh still replaces its members`() = runBlocking {
        memberDataSource.upsert(chatId, listOf(member(a, "A"), member(b, "B")))

        apply(fullRefresh(ChatType.CONTACT_DM, listOf(member(a, "A2"))), renamedA = "A2")

        assertEquals(mapOf(a to "A2"), storedNames())
    }
}
