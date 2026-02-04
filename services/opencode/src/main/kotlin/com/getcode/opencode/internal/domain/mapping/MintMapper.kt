package com.getcode.opencode.internal.domain.mapping

import com.codeinc.opencode.gen.currency.v1.CurrencyService
import com.codeinc.opencode.gen.currency.v1.launchpadMetadataOrNull
import com.getcode.opencode.internal.network.extensions.toMint
import com.getcode.opencode.mapper.Mapper
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.Token
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
        val mint = from.address.toMint()
        val vmMetadata = vmMetadataMapper.map(from.vmMetadata)
        val launchpadMetadata = from.launchpadMetadataOrNull?.let {
            launchpadMetadataMapper.map(it)
        }

        // Handle the provided `createdAt` and if it's valid use it, otherwise
        // do a mint check against USDF and return a well known mint date for it
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
    Mint.jeffy to TokenBillCustomizations(
        background = BillBackground.Gradient(
            listOf("#FFEEBA7F", "#FF783100")
        ),
        icon = null,
        texture = null,
    ),
    Mint.bits to TokenBillCustomizations(
        background = BillBackground.Gradient(
            listOf("#FFC88967", "#FF3E70BC", "#FF093372")
        ),
        icon = null,
        texture = null,
    ),
    Mint.float to TokenBillCustomizations(
        background = BillBackground.Gradient(
            listOf("#FFCA4705", "#FFB49E9F", "#FFD38562",)
        ),
        icon = null,
        texture = null,
    ),
    Mint.xp to TokenBillCustomizations(
        background = BillBackground.Gradient(
            listOf("#FF4EAAC5", "#FFA99BD6", "#FF5621BB")
        ),
        icon = null,
        texture = null,
    ),
    Mint.marketCoin to TokenBillCustomizations(
        background = BillBackground.Gradient(
            listOf("#FFFFD574", "#FFD2954F", "#FF835E33")
        ),
        icon = null,
        texture = null,
    ),
    Mint.bogey to TokenBillCustomizations(
        background = BillBackground.Gradient(
            listOf("#FFABE7B7", "#FF6A8870", "#FF004D0F")
        ),
        icon = null,
        texture = null,
    ),
)

private fun customizationLookup(mint: Mint): TokenBillCustomizations? {
    return customizationLookupTable[mint]
}