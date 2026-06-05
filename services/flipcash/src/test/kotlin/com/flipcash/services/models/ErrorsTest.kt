package com.flipcash.services.models

import com.getcode.utils.CodeServerError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

class ErrorsTest {

    // -- LoginError --

    @Test
    fun `LoginError subtypes are Throwable`() {
        assertIs<Throwable>(LoginError.InvalidTimestamp())
        assertIs<Throwable>(LoginError.Denied())
        assertIs<Throwable>(LoginError.Unrecognized())
        assertIs<Throwable>(LoginError.Other())
    }

    @Test
    fun `LoginError subtypes are CodeServerError`() {
        assertIs<CodeServerError>(LoginError.InvalidTimestamp())
        assertIs<CodeServerError>(LoginError.Denied())
        assertIs<CodeServerError>(LoginError.Unrecognized())
        assertIs<CodeServerError>(LoginError.Other())
    }

    @Test
    fun `LoginError InvalidTimestamp has expected message`() {
        assertEquals("Invalid timestamp", LoginError.InvalidTimestamp().message)
    }

    @Test
    fun `LoginError Other preserves cause`() {
        val root = RuntimeException("something broke")
        val error = LoginError.Other(root)
        assertSame(root, error.cause)
        assertEquals("something broke", error.message)
    }

    @Test
    fun `LoginError Other with null cause has null message`() {
        val error = LoginError.Other(null)
        assertNull(error.cause)
        assertNull(error.message)
    }

    // -- RegisterError --

    @Test
    fun `RegisterError InvalidSignature has expected message`() {
        assertEquals("Invalid signature", RegisterError.InvalidSignature().message)
    }

    @Test
    fun `RegisterError subtypes are CodeServerError`() {
        assertIs<CodeServerError>(RegisterError.InvalidSignature())
        assertIs<CodeServerError>(RegisterError.Denied())
        assertIs<CodeServerError>(RegisterError.Unrecognized())
        assertIs<CodeServerError>(RegisterError.Other())
    }

    @Test
    fun `RegisterError Other preserves cause`() {
        val root = IllegalStateException("bad state")
        val error = RegisterError.Other(root)
        assertSame(root, error.cause)
        assertEquals("bad state", error.message)
    }

    // -- EmailVerificationError --

    @Test
    fun `EmailVerificationError has expected variants`() {
        assertIs<EmailVerificationError>(EmailVerificationError.Denied())
        assertIs<EmailVerificationError>(EmailVerificationError.RateLimited())
        assertIs<EmailVerificationError>(EmailVerificationError.InvalidEmailAddress())
        assertIs<EmailVerificationError>(EmailVerificationError.InvalidVerificationCode())
        assertIs<EmailVerificationError>(EmailVerificationError.NoVerification())
        assertIs<EmailVerificationError>(EmailVerificationError.Unrecognized())
        assertIs<EmailVerificationError>(EmailVerificationError.Other())
    }

    @Test
    fun `EmailVerificationError messages are correct`() {
        assertEquals("Rate limited", EmailVerificationError.RateLimited().message)
        assertEquals("Invalid email address", EmailVerificationError.InvalidEmailAddress().message)
        assertEquals("Invalid verification code", EmailVerificationError.InvalidVerificationCode().message)
        assertEquals("No verification", EmailVerificationError.NoVerification().message)
    }

    // -- PhoneVerificationError --

    @Test
    fun `PhoneVerificationError UnsupportedPhoneType has expected message`() {
        assertEquals("Unsupported phone type", PhoneVerificationError.UnsupportedPhoneType().message)
    }

    @Test
    fun `PhoneVerificationError subtypes are CodeServerError`() {
        assertIs<CodeServerError>(PhoneVerificationError.Denied())
        assertIs<CodeServerError>(PhoneVerificationError.RateLimited())
        assertIs<CodeServerError>(PhoneVerificationError.InvalidPhoneNumber())
        assertIs<CodeServerError>(PhoneVerificationError.UnsupportedPhoneType())
        assertIs<CodeServerError>(PhoneVerificationError.InvalidVerificationCode())
        assertIs<CodeServerError>(PhoneVerificationError.NoVerification())
    }

    // -- PlacePoolBetError --

    @Test
    fun `PlacePoolBetError has specific variants with expected messages`() {
        assertEquals("Pool not found", PlacePoolBetError.PoolNotFound().message)
        assertEquals("Pool closed", PlacePoolBetError.PoolClosed().message)
        assertEquals("Bet already made", PlacePoolBetError.BetAlreadyMade().message)
        assertEquals("Max bets received", PlacePoolBetError.MaxBetsReceived().message)
        assertEquals("Bet outcome solidified", PlacePoolBetError.BetOutcomeSolidified().message)
    }

    @Test
    fun `PlacePoolBetError subtypes are CodeServerError`() {
        assertIs<CodeServerError>(PlacePoolBetError.PoolNotFound())
        assertIs<CodeServerError>(PlacePoolBetError.PoolClosed())
        assertIs<CodeServerError>(PlacePoolBetError.BetAlreadyMade())
        assertIs<CodeServerError>(PlacePoolBetError.MaxBetsReceived())
    }

    // -- GetMessageError --

    @Test
    fun `GetMessageError subtypes are CodeServerError`() {
        assertIs<CodeServerError>(GetMessageError.Denied())
        assertIs<CodeServerError>(GetMessageError.NotFound())
        assertIs<CodeServerError>(GetMessageError.Unrecognized())
        assertIs<CodeServerError>(GetMessageError.Other())
    }

    // -- GetMessagesError --

    @Test
    fun `GetMessagesError subtypes are CodeServerError`() {
        assertIs<CodeServerError>(GetMessagesError.Denied())
        assertIs<CodeServerError>(GetMessagesError.NotFound())
        assertIs<CodeServerError>(GetMessagesError.Unrecognized())
        assertIs<CodeServerError>(GetMessagesError.Other())
    }

    // -- FlipcashSendMessageError --

    @Test
    fun `FlipcashSendMessageError subtypes are CodeServerError`() {
        assertIs<CodeServerError>(FlipcashSendMessageError.Denied())
        assertIs<CodeServerError>(FlipcashSendMessageError.Unrecognized())
        assertIs<CodeServerError>(FlipcashSendMessageError.Other())
    }

    // -- AdvancePointerError --

    @Test
    fun `AdvancePointerError has expected variants`() {
        assertEquals("Denied", AdvancePointerError.Denied().message)
        assertEquals("Message not found", AdvancePointerError.MessageNotFound().message)
        assertIs<CodeServerError>(AdvancePointerError.Unrecognized())
    }

    // -- NotifyIsTypingError --

    @Test
    fun `NotifyIsTypingError subtypes are CodeServerError`() {
        assertIs<CodeServerError>(NotifyIsTypingError.Denied())
        assertIs<CodeServerError>(NotifyIsTypingError.Unrecognized())
        assertIs<CodeServerError>(NotifyIsTypingError.Other())
    }

    // -- GetChatError --

    @Test
    fun `GetChatError subtypes are CodeServerError`() {
        assertIs<CodeServerError>(GetChatError.Denied())
        assertIs<CodeServerError>(GetChatError.NotFound())
        assertIs<CodeServerError>(GetChatError.Unrecognized())
        assertIs<CodeServerError>(GetChatError.Other())
    }

    @Test
    fun `GetChatError has expected messages`() {
        assertEquals("Denied", GetChatError.Denied().message)
        assertEquals("Not found", GetChatError.NotFound().message)
    }

    // -- GetDmChatFeedError --

    @Test
    fun `GetDmChatFeedError subtypes are CodeServerError`() {
        assertIs<CodeServerError>(GetDmChatFeedError.Denied())
        assertIs<CodeServerError>(GetDmChatFeedError.NotFound())
        assertIs<CodeServerError>(GetDmChatFeedError.Unrecognized())
        assertIs<CodeServerError>(GetDmChatFeedError.Other())
    }

    @Test
    fun `GetDmChatFeedError Other preserves cause`() {
        val root = RuntimeException("feed broke")
        val error = GetDmChatFeedError.Other(root)
        assertSame(root, error.cause)
        assertEquals("feed broke", error.message)
    }

    // -- StreamEventsError --

    @Test
    fun `StreamEventsError subtypes are CodeServerError`() {
        assertIs<CodeServerError>(StreamEventsError.Denied())
        assertIs<CodeServerError>(StreamEventsError.InvalidTimestamp())
        assertIs<CodeServerError>(StreamEventsError.Unrecognized())
        assertIs<CodeServerError>(StreamEventsError.Other())
    }

    @Test
    fun `StreamEventsError has expected messages`() {
        assertEquals("Denied", StreamEventsError.Denied().message)
        assertEquals("Invalid timestamp", StreamEventsError.InvalidTimestamp().message)
    }

    @Test
    fun `StreamEventsError Other preserves cause`() {
        val root = RuntimeException("stream broke")
        val error = StreamEventsError.Other(root)
        assertSame(root, error.cause)
        assertEquals("stream broke", error.message)
    }

    // -- GetJwtError --

    @Test
    fun `GetJwtError has expected variants with correct messages`() {
        assertEquals("Unsupported provider", GetJwtError.UnsupportedProvider().message)
        assertEquals("Invalid api key", GetJwtError.InvalidApiKey().message)
        assertEquals("Phone verification required", GetJwtError.PhoneVerificationRequired().message)
        assertEquals("Email verification required", GetJwtError.EmailVerificationRequired().message)
        assertEquals("Denied", GetJwtError.Denied().message)
    }

    @Test
    fun `GetJwtError subtypes are CodeServerError`() {
        assertIs<CodeServerError>(GetJwtError.UnsupportedProvider())
        assertIs<CodeServerError>(GetJwtError.InvalidApiKey())
        assertIs<CodeServerError>(GetJwtError.PhoneVerificationRequired())
        assertIs<CodeServerError>(GetJwtError.EmailVerificationRequired())
        assertIs<CodeServerError>(GetJwtError.Denied())
        assertIs<CodeServerError>(GetJwtError.Unrecognized())
        assertIs<CodeServerError>(GetJwtError.Other())
    }

    @Test
    fun `GetJwtError Other preserves cause`() {
        val root = RuntimeException("jwt failure")
        val error = GetJwtError.Other(root)
        assertSame(root, error.cause)
        assertEquals("jwt failure", error.message)
    }
}
