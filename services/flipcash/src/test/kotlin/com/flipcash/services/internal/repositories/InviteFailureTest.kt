package com.flipcash.services.internal.repositories

import com.flipcash.services.models.JoinChatError
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InviteFailureTest {
    @Test
    fun `dead-invite join results are expected and stay out of error reporting`() {
        assertTrue(JoinChatError.InviteExhausted().isExpectedInviteFailure())
        assertTrue(JoinChatError.InviteExpired().isExpectedInviteFailure())
        assertTrue(JoinChatError.InviteRevoked().isExpectedInviteFailure())
    }

    @Test
    fun `other join failures are still reported`() {
        // DENIED with an invite points to a client bug, so it must reach error reporting.
        assertFalse(JoinChatError.Denied().isExpectedInviteFailure())
        assertFalse(JoinChatError.RulesNotSatisfied().isExpectedInviteFailure())
        assertFalse(JoinChatError.Unrecognized().isExpectedInviteFailure())
        assertFalse(JoinChatError.Other(RuntimeException()).isExpectedInviteFailure())
    }
}
