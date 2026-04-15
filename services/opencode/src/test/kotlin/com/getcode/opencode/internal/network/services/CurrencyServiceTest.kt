package com.getcode.opencode.internal.network.services

import com.codeinc.opencode.gen.common.v1.Model
import com.codeinc.opencode.gen.currency.v1.CurrencyService as CurrencyProto
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.internal.domain.mapping.HistoricalMintDataMapper
import com.getcode.opencode.internal.domain.mapping.LiveMintDataMapper
import com.getcode.opencode.internal.domain.mapping.MintMapper
import com.getcode.opencode.internal.manager.VerifiedProtoManager
import com.getcode.opencode.internal.network.api.CurrencyApi
import com.getcode.opencode.model.core.errors.LaunchTokenError
import com.getcode.opencode.model.core.errors.UpdateIconError
import com.getcode.opencode.model.core.errors.UpdateMetadataError
import com.getcode.opencode.model.financial.TokenUpdateRequest
import com.getcode.opencode.model.moderation.ModerationAttestation
import com.google.protobuf.ByteString
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CurrencyServiceTest {

    private val api = mockk<CurrencyApi>()
    private val mintMapper = mockk<MintMapper>(relaxed = true)
    private val historicalMintDataMapper = mockk<HistoricalMintDataMapper>(relaxed = true)
    private val liveMintDataMapper = mockk<LiveMintDataMapper>(relaxed = true)
    private val verifiedStateManager = mockk<VerifiedProtoManager>(relaxed = true)

    private val service = CurrencyService(
        api = api,
        mintMapper = mintMapper,
        historicalMintDataMapper = historicalMintDataMapper,
        liveMintDataMapper = liveMintDataMapper,
        verifiedStateManager = verifiedStateManager,
    )

    private val owner = mockk<Ed25519.KeyPair>(relaxed = true)

    // region launchNewToken

    @Test
    fun `launchNewToken OK returns success with Mint`() = runTest {
        coEvery { api.launchToken(any(), any(), any(), any(), any(), any()) } returns
            launchResponse(CurrencyProto.LaunchResponse.Result.OK, includeMint = true)

        val result = service.launchNewToken(
            name = makeName(),
            symbol = null,
            description = null,
            bill = null,
            icon = null,
            owner = owner,
        )

        assertTrue(result.isSuccess)
        assertNotNull(result.getOrNull())
    }

    @Test
    fun `launchNewToken DENIED returns Denied error`() = runTest {
        coEvery { api.launchToken(any(), any(), any(), any(), any(), any()) } returns
            launchResponse(CurrencyProto.LaunchResponse.Result.DENIED)

        val result = service.launchNewToken(
            name = makeName(),
            symbol = null,
            description = null,
            bill = null,
            icon = null,
            owner = owner,
        )

        assertTrue(result.isFailure)
        assertIs<LaunchTokenError.Denied>(result.exceptionOrNull())
    }

    @Test
    fun `launchNewToken NAME_EXISTS returns Exists error`() = runTest {
        coEvery { api.launchToken(any(), any(), any(), any(), any(), any()) } returns
            launchResponse(CurrencyProto.LaunchResponse.Result.NAME_EXISTS)

        val result = service.launchNewToken(
            name = makeName(),
            symbol = null,
            description = null,
            bill = null,
            icon = null,
            owner = owner,
        )

        assertTrue(result.isFailure)
        assertIs<LaunchTokenError.Exists>(result.exceptionOrNull())
    }

    @Test
    fun `launchNewToken INVALID_ICON returns InvalidIcon error`() = runTest {
        coEvery { api.launchToken(any(), any(), any(), any(), any(), any()) } returns
            launchResponse(CurrencyProto.LaunchResponse.Result.INVALID_ICON)

        val result = service.launchNewToken(
            name = makeName(),
            symbol = null,
            description = null,
            bill = null,
            icon = null,
            owner = owner,
        )

        assertTrue(result.isFailure)
        assertIs<LaunchTokenError.InvalidIcon>(result.exceptionOrNull())
    }

    @Test
    fun `launchNewToken exception returns Other error with cause`() = runTest {
        val exception = RuntimeException("network failure")
        coEvery { api.launchToken(any(), any(), any(), any(), any(), any()) } throws exception

        val result = service.launchNewToken(
            name = makeName(),
            symbol = null,
            description = null,
            bill = null,
            icon = null,
            owner = owner,
        )

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<LaunchTokenError.Other>(error)
        assertNotNull(error.cause)
    }

    // endregion

    // region updateIcon

    @Test
    fun `updateIcon OK returns success`() = runTest {
        coEvery { api.updateIcon(any(), any()) } returns
            updateIconResponse(CurrencyProto.UpdateIconResponse.Result.OK)

        val result = service.updateIcon(makeIconRequest(), owner)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `updateIcon NOT_FOUND returns NotFound error`() = runTest {
        coEvery { api.updateIcon(any(), any()) } returns
            updateIconResponse(CurrencyProto.UpdateIconResponse.Result.NOT_FOUND)

        val result = service.updateIcon(makeIconRequest(), owner)

        assertTrue(result.isFailure)
        assertIs<UpdateIconError.NotFound>(result.exceptionOrNull())
    }

    @Test
    fun `updateIcon DENIED returns Denied error`() = runTest {
        coEvery { api.updateIcon(any(), any()) } returns
            updateIconResponse(CurrencyProto.UpdateIconResponse.Result.DENIED)

        val result = service.updateIcon(makeIconRequest(), owner)

        assertTrue(result.isFailure)
        assertIs<UpdateIconError.Denied>(result.exceptionOrNull())
    }

    @Test
    fun `updateIcon INVALID_ICON returns InvalidIcon error`() = runTest {
        coEvery { api.updateIcon(any(), any()) } returns
            updateIconResponse(CurrencyProto.UpdateIconResponse.Result.INVALID_ICON)

        val result = service.updateIcon(makeIconRequest(), owner)

        assertTrue(result.isFailure)
        assertIs<UpdateIconError.InvalidIcon>(result.exceptionOrNull())
    }

    @Test
    fun `updateIcon exception returns Other error with cause`() = runTest {
        val exception = RuntimeException("network failure")
        coEvery { api.updateIcon(any(), any()) } throws exception

        val result = service.updateIcon(makeIconRequest(), owner)

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<UpdateIconError.Other>(error)
        assertNotNull(error.cause)
    }

    // endregion

    // region updateMetadata

    @Test
    fun `updateMetadata OK returns success`() = runTest {
        coEvery { api.updateMetadata(any(), any()) } returns
            updateMetadataResponse(CurrencyProto.UpdateMetadataResponse.Result.OK)

        val result = service.updateMetadata(makeMetadataRequest(), owner)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `updateMetadata NOT_FOUND returns NotFound error`() = runTest {
        coEvery { api.updateMetadata(any(), any()) } returns
            updateMetadataResponse(CurrencyProto.UpdateMetadataResponse.Result.NOT_FOUND)

        val result = service.updateMetadata(makeMetadataRequest(), owner)

        assertTrue(result.isFailure)
        assertIs<UpdateMetadataError.NotFound>(result.exceptionOrNull())
    }

    @Test
    fun `updateMetadata DENIED returns Denied error`() = runTest {
        coEvery { api.updateMetadata(any(), any()) } returns
            updateMetadataResponse(CurrencyProto.UpdateMetadataResponse.Result.DENIED)

        val result = service.updateMetadata(makeMetadataRequest(), owner)

        assertTrue(result.isFailure)
        assertIs<UpdateMetadataError.Denied>(result.exceptionOrNull())
    }

    @Test
    fun `updateMetadata exception returns Other error with cause`() = runTest {
        val exception = RuntimeException("network failure")
        coEvery { api.updateMetadata(any(), any()) } throws exception

        val result = service.updateMetadata(makeMetadataRequest(), owner)

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertIs<UpdateMetadataError.Other>(error)
        assertNotNull(error.cause)
    }

    // endregion

    // region helpers

    private fun makeName() = ModerationAttestation.Name("Test", emptyList())

    private fun makeIconRequest() = TokenUpdateRequest.Icon(
        token = mockk(relaxed = true),
        icon = ModerationAttestation.Image(ByteArray(4), emptyList()),
    )

    private fun makeMetadataRequest() = TokenUpdateRequest.Metadata(
        token = mockk(relaxed = true),
    )

    private fun fakeSolanaAccountId(): Model.SolanaAccountId =
        Model.SolanaAccountId.newBuilder()
            .setValue(ByteString.copyFrom(ByteArray(32)))
            .build()

    private fun launchResponse(
        result: CurrencyProto.LaunchResponse.Result,
        includeMint: Boolean = false,
    ): CurrencyProto.LaunchResponse =
        CurrencyProto.LaunchResponse.newBuilder()
            .setResult(result)
            .apply { if (includeMint) setMint(fakeSolanaAccountId()) }
            .build()

    private fun updateIconResponse(
        result: CurrencyProto.UpdateIconResponse.Result,
    ): CurrencyProto.UpdateIconResponse =
        CurrencyProto.UpdateIconResponse.newBuilder()
            .setResult(result)
            .build()

    private fun updateMetadataResponse(
        result: CurrencyProto.UpdateMetadataResponse.Result,
    ): CurrencyProto.UpdateMetadataResponse =
        CurrencyProto.UpdateMetadataResponse.newBuilder()
            .setResult(result)
            .build()

    // endregion
}
