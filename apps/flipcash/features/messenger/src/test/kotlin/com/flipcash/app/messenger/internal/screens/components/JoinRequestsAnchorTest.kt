package com.flipcash.app.messenger.internal.screens.components

import com.flipcash.app.messenger.internal.ChatViewModel
import com.flipcash.services.models.chat.LobbyMember
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JoinRequestsAnchorTest {

    private val and = { head: String, last: String -> "$head and $last" }

    @Test
    fun `no names is empty`() = assertEquals("", joinNames(emptyList(), and))

    @Test
    fun `one name stands alone`() = assertEquals("Jasper", joinNames(listOf("Jasper"), and))

    @Test
    fun `two names are joined by and`() =
        assertEquals("Jasper and Tam", joinNames(listOf("Jasper", "Tam"), and))

    @Test
    fun `the last of several is joined by and, the rest by commas`() =
        assertEquals(
            "Jasper, Vincentjeezus24 and Tam",
            joinNames(listOf("Jasper", "Vincentjeezus24", "Tam"), and),
        )

    @Test
    fun `the host sees the anchor while someone is waiting`() {
        val state = ChatViewModel.State(isLobbyHost = true, joinRequests = listOf(mockk<LobbyMember>()))
        assertTrue(state.showsJoinRequestsAnchor)
    }

    @Test
    fun `the anchor hides at zero`() {
        val state = ChatViewModel.State(isLobbyHost = true, joinRequests = emptyList())
        assertFalse(state.showsJoinRequestsAnchor)
    }

    @Test
    fun `only the host sees the anchor`() {
        val state = ChatViewModel.State(isLobbyHost = false, joinRequests = listOf(mockk<LobbyMember>()))
        assertFalse(state.showsJoinRequestsAnchor)
    }
}
