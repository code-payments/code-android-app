package com.flipcash.services.internal.network.services

import com.flipcash.services.internal.network.api.PhoneVerificationApi
import com.flipcash.services.models.ContactMethod
import com.flipcash.services.models.PhoneVerificationError
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.model.core.errors.ValidationException
import dev.bmcreations.protovalidate.FieldViolation
import dev.bmcreations.protovalidate.ProtoValidationException
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import com.codeinc.flipcash.gen.phone.v1.PhoneVerificationService as RpcPhoneService

class PhoneVerificationServiceTest {

    private val api = mockk<PhoneVerificationApi>()
    private val service = PhoneVerificationService(api)
    private val owner = mockk<Ed25519.KeyPair>(relaxed = true)
    private val phone = ContactMethod.Phone("+15551234567")

    // region sendVerificationCode

    @Test
    fun `sendVerificationCode OK returns success`() = runTest {
        coEvery { api.sendVerificationCode(any(), any()) } returns
            sendCodeResponse(RpcPhoneService.SendVerificationCodeResponse.Result.OK)

        val result = service.sendVerificationCode(phone, owner)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `sendVerificationCode DENIED returns Denied error`() = runTest {
        coEvery { api.sendVerificationCode(any(), any()) } returns
            sendCodeResponse(RpcPhoneService.SendVerificationCodeResponse.Result.DENIED)

        val result = service.sendVerificationCode(phone, owner)

        assertTrue(result.isFailure)
        assertIs<PhoneVerificationError.Denied>(result.exceptionOrNull())
    }

    @Test
    fun `sendVerificationCode exception returns Other error with cause`() = runTest {
        coEvery { api.sendVerificationCode(any(), any()) } throws RuntimeException("network failure")

        val result = service.sendVerificationCode(phone, owner)

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<PhoneVerificationError.Other>(error)
        assertNotNull(error.cause)
    }

    @Test
    fun `sendVerificationCode ProtoValidationException returns ValidationException`() = runTest {
        val violation = FieldViolation(field = "phone_number", rule = "string.pattern", message = "invalid format")
        coEvery { api.sendVerificationCode(any(), any()) } throws ProtoValidationException(listOf(violation))

        val result = service.sendVerificationCode(phone, owner)

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<ValidationException>(error)
        assertEquals("phone_number", error.violations.first().field)
        assertEquals("invalid format", error.violations.first().message)
    }

    // endregion

    // region checkVerificationCode

    @Test
    fun `checkVerificationCode OK returns success`() = runTest {
        coEvery { api.checkVerificationCode(any(), any(), any()) } returns
            checkCodeResponse(RpcPhoneService.CheckVerificationCodeResponse.Result.OK)

        val result = service.checkVerificationCode(phone, "123456", owner)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `checkVerificationCode INVALID_CODE returns InvalidVerificationCode error`() = runTest {
        coEvery { api.checkVerificationCode(any(), any(), any()) } returns
            checkCodeResponse(RpcPhoneService.CheckVerificationCodeResponse.Result.INVALID_CODE)

        val result = service.checkVerificationCode(phone, "000000", owner)

        assertTrue(result.isFailure)
        assertIs<PhoneVerificationError.InvalidVerificationCode>(result.exceptionOrNull())
    }

    @Test
    fun `checkVerificationCode ProtoValidationException returns ValidationException`() = runTest {
        val violation = FieldViolation(field = "code", rule = "string.len", message = "must be 6 digits")
        coEvery { api.checkVerificationCode(any(), any(), any()) } throws ProtoValidationException(listOf(violation))

        val result = service.checkVerificationCode(phone, "bad", owner)

        assertTrue(result.isFailure)
        assertIs<ValidationException>(result.exceptionOrNull())
    }

    // endregion

    // region unlink

    @Test
    fun `unlink OK returns success`() = runTest {
        coEvery { api.unlink(any(), any()) } returns
            unlinkResponse(RpcPhoneService.UnlinkResponse.Result.OK)

        val result = service.unlink(phone, owner)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `unlink ProtoValidationException returns ValidationException`() = runTest {
        val violation = FieldViolation(field = "phone_number", rule = "required", message = "required")
        coEvery { api.unlink(any(), any()) } throws ProtoValidationException(listOf(violation))

        val result = service.unlink(phone, owner)

        assertTrue(result.isFailure)
        assertIs<ValidationException>(result.exceptionOrNull())
    }

    // endregion

    // region helpers

    private fun sendCodeResponse(
        result: RpcPhoneService.SendVerificationCodeResponse.Result,
    ): RpcPhoneService.SendVerificationCodeResponse =
        RpcPhoneService.SendVerificationCodeResponse.newBuilder()
            .setResult(result)
            .build()

    private fun checkCodeResponse(
        result: RpcPhoneService.CheckVerificationCodeResponse.Result,
    ): RpcPhoneService.CheckVerificationCodeResponse =
        RpcPhoneService.CheckVerificationCodeResponse.newBuilder()
            .setResult(result)
            .build()

    private fun unlinkResponse(
        result: RpcPhoneService.UnlinkResponse.Result,
    ): RpcPhoneService.UnlinkResponse =
        RpcPhoneService.UnlinkResponse.newBuilder()
            .setResult(result)
            .build()

    // endregion
}
