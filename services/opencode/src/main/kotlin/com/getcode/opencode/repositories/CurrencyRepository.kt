package com.getcode.opencode.repositories

import com.getcode.ed25519.Ed25519
import com.getcode.opencode.model.ui.DiscoverCategory
import com.getcode.opencode.model.financial.LiveMintDataResponse
import com.getcode.opencode.model.ui.WindowedRange
import com.getcode.opencode.internal.network.streamers.ManagedMintStream
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.HistoricalMintData
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.moderation.ModerationAttestation
import com.getcode.opencode.model.ui.TokenBillCustomizations
import com.getcode.solana.keys.Mint
import kotlinx.coroutines.CoroutineScope

interface CurrencyRepository {
    fun streamMintData(
        scope: CoroutineScope,
        mints: List<Mint>,
        tag: String? = null,
        onUpdate: (LiveMintDataResponse) -> Unit,
    ): ManagedMintStream

    suspend fun getMintMetadata(addresses: List<Mint>): Result<List<MintMetadata>>

    suspend fun getHistoricalMintData(
        mint: Mint,
        currencyCode: CurrencyCode,
        windowedRange: WindowedRange
    ): Result<List<HistoricalMintData>>

    suspend fun discoverTokens(
        category: DiscoverCategory
    ): Result<List<Token>>

    suspend fun checkTokenAvailability(name: String): Result<Boolean>
    suspend fun launchToken(
        name: ModerationAttestation.Text,
        symbol: ModerationAttestation.Text? = null,
        description: ModerationAttestation.Text? = null,
        bill: TokenBillCustomizations?,
        icon: ModerationAttestation.Image? = null,
        owner: Ed25519.KeyPair
    ): Result<Mint>
}