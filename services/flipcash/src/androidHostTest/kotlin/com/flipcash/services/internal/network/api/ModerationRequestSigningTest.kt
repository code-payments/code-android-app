package com.flipcash.services.internal.network.api

import com.codeinc.flipcash.gen.common.v1.Common
import com.codeinc.flipcash.gen.moderation.v1.Model
import com.codeinc.flipcash.gen.moderation.v1.ModerationService
import com.codeinc.flipcash.gen.moderation.v1.validate
import com.flipcash.services.models.ImageContentKind
import com.flipcash.services.models.TextContentKind
import dev.bmcreations.protovalidate.ValidationResult
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The server binds a moderation request's `kind` into the attestation and verifies the request's
 * signature against the whole message minus the auth, so `kind` has to be in the bytes handed to
 * the signer. These assert on those bytes, so no signing key is needed. They also check every kind
 * the app can send passes the request's `kind` rule, which allows only text kinds on text and only
 * the icon kind on images.
 */
class ModerationRequestSigningTest {

    private val defaultAuth = Common.Auth.getDefaultInstance()

    private fun signedText(kind: TextContentKind): ModerationService.ModerateTextRequest {
        var signed: ByteArray? = null
        moderateTextRequest("Flipcash", kind) {
            signed = buildPartial().toByteArray()
            defaultAuth
        }
        return ModerationService.ModerateTextRequest.parseFrom(
            signed ?: error("the authenticator was never invoked")
        )
    }

    private fun signedImage(kind: ImageContentKind): ModerationService.ModerateImageRequest {
        var signed: ByteArray? = null
        moderateImageRequest(byteArrayOf(1, 2, 3), kind) {
            signed = buildPartial().toByteArray()
            defaultAuth
        }
        return ModerationService.ModerateImageRequest.parseFrom(
            signed ?: error("the authenticator was never invoked")
        )
    }

    private fun ValidationResult.kindViolations(): List<String> =
        (this as? ValidationResult.Invalid)?.violations.orEmpty()
            .map { it.field }
            .filter { it == "kind" }

    @Test
    fun `a currency name kind is signed`() {
        assertEquals(Model.ContentKind.CURRENCY_NAME, signedText(TextContentKind.CurrencyName).kind)
    }

    @Test
    fun `a currency symbol kind is signed`() {
        assertEquals(Model.ContentKind.CURRENCY_SYMBOL, signedText(TextContentKind.CurrencySymbol).kind)
    }

    @Test
    fun `a currency description kind is signed`() {
        assertEquals(
            Model.ContentKind.CURRENCY_DESCRIPTION,
            signedText(TextContentKind.CurrencyDescription).kind,
        )
    }

    @Test
    fun `a currency icon kind is signed`() {
        assertEquals(Model.ContentKind.CURRENCY_ICON, signedImage(ImageContentKind.CurrencyIcon).kind)
    }

    @Test
    fun `the moderated text is signed`() {
        assertEquals("Flipcash", signedText(TextContentKind.CurrencyName).text)
    }

    @Test
    fun `every text kind passes the request's kind rule`() {
        TextContentKind.entries.forEach { kind ->
            val request = moderateTextRequest("Flipcash", kind) { defaultAuth }
            assertTrue(request.validate().kindViolations().isEmpty(), "$kind was refused")
        }
    }

    @Test
    fun `every image kind passes the request's kind rule`() {
        ImageContentKind.entries.forEach { kind ->
            val request = moderateImageRequest(byteArrayOf(1, 2, 3), kind) { defaultAuth }
            assertTrue(request.validate().kindViolations().isEmpty(), "$kind was refused")
        }
    }
}
