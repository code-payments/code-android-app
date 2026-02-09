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
            listOf("#FFACBEDD", "#FF3E70BC", "#FF093372")
        ),
        icon = null,
        texture = null,
    ),
    Mint.float to TokenBillCustomizations(
        background = BillBackground.Gradient(
            listOf("#FFC88967", "#FFAF9F9E", "#FFBB4F21",)
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
    Mint.badboys to TokenBillCustomizations(
        background = BillBackground.Gradient(
            listOf("#AAAAAA", "#2C2C2C")
        ),
        icon = null,
        texture = null,
    ),
    Mint.sandbox to TokenBillCustomizations(
        background = BillBackground.Gradient(
            listOf("#AAAAAA", "#2C2C2C")
        ),
        icon = null,
        texture = null,
    )
)

private fun customizationLookup(mint: Mint): TokenBillCustomizations? {
    return customizationLookupTable[mint]
}

private val Mint.Companion.jeffy: Mint
    get() = Mint("54ggcQ23uen5b9QXMAns99MQNTKn7iyzq4wvCW6e8r25")

private val Mint.Companion.bogey: Mint
    get() = Mint("3AhBb1fpDTp1F9hPkZjRPDejXBM9S5vfpVdvn66vLYnT")

private val Mint.Companion.marketCoin: Mint
    get() = Mint("311m6Sb1814PfAxkEcqq6MNdBiVZLr8VWuAWDSC72euW")

private val Mint.Companion.xp: Mint
    get() = Mint("6oZnhB1FPrUaDfhRCVZnbVWNKVx9wgj84vKGH7eMpzXL")

private val Mint.Companion.float: Mint
    get() = Mint("5APqK9YUZupKt7rRUrpYy6WV3RPuxA71ZtKJffDUMdPP")

private val Mint.Companion.bits: Mint
    get() = Mint("A3e8dzb1y4gqGP2cnCS3UU8dm5YNrFpZBpjjdoZdtfnB")

private val Mint.Companion.badboys: Mint
    get() = Mint("64dkhPKhdjc2xg3NLyDjC14wiXHLnGXHHUxJnqZVugJt")

private val Mint.Companion.sandbox: Mint
    get() = Mint("2psDP3LAvbNzfvBYNMs9ieMpsD8PVzyQsKNfZrjEKoDN")