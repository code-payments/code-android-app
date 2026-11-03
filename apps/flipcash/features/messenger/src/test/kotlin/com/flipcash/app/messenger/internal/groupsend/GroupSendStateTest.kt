package com.flipcash.app.messenger.internal.groupsend

import com.flipcash.services.models.chat.ChatMember
import com.getcode.opencode.model.core.ID
import com.getcode.opencode.model.financial.Fiat
import com.flipcash.services.models.UserProfile
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GroupSendStateTest {
    private fun id(i: Int): ID = List(32) { i.toByte() }
    private val viewer = id(99)
    private val dollar = Fiat(1.0)

    @Test
    fun `total is the amount times the people checked`() {
        val state = GroupSendState(viewer, amount = dollar, selected = listOf(id(1), id(2)))
        assertEquals(Fiat(2.0), state.total)
        assertEquals(Fiat(0.0), state.copy(selected = emptyList()).total)
        assertNull(GroupSendState(viewer).total)
    }

    @Test
    fun `candidates leave the sender out`() {
        val roster = listOf(id(1), viewer, id(2)).map { ChatMember(it, mockk<UserProfile>(relaxed = true), emptyList()) }
        assertEquals(listOf(id(1), id(2)), GroupSendState(viewer).candidates(roster).map { it.userId })
    }

    @Test
    fun `ten can be checked and an eleventh is refused, in check order`() {
        val ten = (1..10).map { id(it) }
        val state = GroupSendState(viewer, amount = dollar, selected = ten)
        assertNull(state.toggled(id(11)))
        assertEquals(ten.drop(1), state.toggled(ten.first()))
        assertEquals(ten.drop(1).plusElement(ten.first()), state.copy(selected = ten.drop(1)).toggled(ten.first()))
    }
}
