package com.getcode.opencode.internal.domain.mapping

import com.codeinc.opencode.gen.currency.v1.CurrencyService
import com.codeinc.opencode.gen.currency.v1.launchpadMetadataOrNull
import com.getcode.opencode.internal.network.extensions.toPublicKey
import com.getcode.opencode.mapper.Mapper
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.usdc
import com.getcode.opencode.model.financial.usdf
import com.getcode.opencode.model.ui.BillBackground
import com.getcode.opencode.model.ui.TokenBillCustomizations
import com.getcode.solana.keys.Mint
import javax.inject.Inject
import kotlin.time.Instant

internal class MintMapper @Inject constructor(
    private val vmMetadataMapper: VmMetadataMapper,
    private val launchpadMetadataMapper: LaunchpadMetadataMapper,
) : Mapper<CurrencyService.Mint, MintMetadata> {
    override fun map(from: CurrencyService.Mint): MintMetadata {
        val mint = from.address.toPublicKey()
        val vmMetadata = vmMetadataMapper.map(from.vmMetadata)
        val launchpadMetadata = from.launchpadMetadataOrNull?.let {
            launchpadMetadataMapper.map(it)
        }

        // Handle the provided `createdAt` and if it's valid use it, otherwise
        // do a mint check against USDC and return a well known mint date for it
        // otherwise return null
        val mintDate = from.createdAt.seconds.takeIf { it > 0 }
            .let { at ->
                if (at == null && mint == Mint.usdf) {
                    Token.usdf.createdAt
                } else {
                    at?.let { Instant.fromEpochMilliseconds(it * 1_000L) }
                }
            }

        return MintMetadata(
            address = mint,
            decimals = from.decimals,
            name = from.name,
            createdAt = mintDate,
            symbol = from.symbol,
            description = from.description,
            imageUrl = from.imageUrl,
            vmMetadata = vmMetadata,
            launchpadMetadata = launchpadMetadata,
            billCustomizations = customizationLookup(mint),
        )
    }
}

private val customizationLookupTable = mapOf(
    // Jeffy
    Mint("52MNGpgvydSwCtC2H4qeiZXZ1TxEuRVCRGa8LAfk2kSj") to TokenBillCustomizations(
        background = BillBackground.Gradient(
            listOf("#FFEEBA7F", "#FF783100")
        ),
        icon = null,
//        icon = "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".toByteArray(),
        texture = null,
    ),
    // Knicks Night
    Mint("497Wy6cY9BjWBiaDHzJ7TcUZqF2gE1Qm7yXtSj1vSr5W") to TokenBillCustomizations(
        background = BillBackground.Gradient(
            listOf("#FFFF7D0C", "#FFFF9A3C", "#FF1443FF")
        ),
        icon = null,
        texture = null,
    ),
    // Farmer Coin
    Mint("2o4PFbDZ73BihFraknfVTQeUtELKAeVUL4oa6bkrYU3A") to TokenBillCustomizations(
        background = BillBackground.Gradient(
            listOf("#FFCBDF7A", "#FFAEAE0D", "#FF5F5F01")
        ),
        icon = null,
        texture = null,
    )
)

private fun customizationLookup(mint: Mint): TokenBillCustomizations? {
    return customizationLookupTable[mint]
}