package com.flipcash.services.internal.network.services

import com.flipcash.services.internal.network.api.EmailVerificationApi
import com.flipcash.services.models.ContactMethod
import com.flipcash.services.models.EmailVerificationError
import com.getcode.ed25519kmp.Ed25519
import com.getcode.opencode.model.core.errors.ValidationException
import dev.bmcreations.protovalidate.FieldViolation
import dev.bmcreations.protovalidate.ProtoValidationException
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import com.codeinc.flipcash.gen.email.v1.EmailVerificationService as RpcEmailService

class EmailVerificationServiceTest {

    private val api = mockk<EmailVerificationApi>()
    private val service = EmailVerificationService(api)
    private val owner = mockk<Ed25519.KeyPair>(relaxed = true)
    private val email = ContactMethod.Email("eve@example.com")

    // region linkUnverified

    @Test
    fun `linkUnverified OK returns success`() = runTest {
        coEvery { api.linkUnverified(any(), any()) } returns
            linkUnverifiedResponse(RpcEmailService.LinkUnverifiedResponse.Result.OK)

        val result = service.linkUnverified(email, owner)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `linkUnverified DENIED returns Denied error`() = runTest {
        coEvery { api.linkUnverified(any(), any()) } returns
            linkUnverifiedResponse(RpcEmailService.LinkUnverifiedResponse.Result.DENIED)

        val result = service.linkUnverified(email, owner)

        assertIs<EmailVerificationError.Denied>(result.exceptionOrNull())
    }

    @Test
    fun `linkUnverified unknown result returns Unrecognized error`() = runTest {
        coEvery { api.linkUnverified(any(), any()) } returns
            RpcEmailService.LinkUnverifiedResponse.newBuilder().setResultValue(99).build()

        val result = service.linkUnverified(email, owner)

        assertIs<EmailVerificationError.Unrecognized>(result.exceptionOrNull())
    }

    @Test
    fun `linkUnverified ProtoValidationException returns ValidationException`() = runTest {
        val violation = FieldViolation(field = "email_address", rule = "required", message = "required")
        coEvery { api.linkUnverified(any(), any()) } throws ProtoValidationException(listOf(violation))

        val result = service.linkUnverified(email, owner)

        assertIs<ValidationException>(result.exceptionOrNull())
    }

    // endregion

    // region helpers

    private fun linkUnverifiedResponse(
        result: RpcEmailService.LinkUnverifiedResponse.Result,
    ): RpcEmailService.LinkUnverifiedResponse =
        RpcEmailService.LinkUnverifiedResponse.newBuilder()
            .setResult(result)
            .build()

    // endregion
}
