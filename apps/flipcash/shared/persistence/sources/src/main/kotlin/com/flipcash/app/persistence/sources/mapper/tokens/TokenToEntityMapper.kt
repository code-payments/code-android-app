package com.flipcash.app.persistence.sources.mapper.tokens

import android.util.Base64
import com.flipcash.app.persistence.converters.BillBackgroundSerialized
import com.flipcash.app.persistence.converters.BillCustomizationsSerialized
import com.flipcash.app.persistence.converters.BillTextureSerialized
import com.flipcash.app.persistence.converters.SocialLinkSerialized
import com.flipcash.app.persistence.embedded.LaunchpadMetadataEmbedded
import com.flipcash.app.persistence.embedded.VmMetadataEmbedded
import com.flipcash.app.persistence.entities.TokenEntity
import com.getcode.opencode.mapper.Mapper
import com.getcode.opencode.model.financial.LaunchpadMetadata
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.SocialLink
import com.getcode.opencode.model.ui.BillBackground
import com.getcode.opencode.model.ui.TokenBillCustomizations
import com.getcode.solana.keys.base58
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

class TokenToEntityMapper @Inject constructor() : Mapper<MintMetadata, TokenEntity> {

    private val json = Json { encodeDefaults = true }

    override fun map(from: MintMetadata): TokenEntity {
        return TokenEntity(
            address = from.address.base58(),
            decimals = from.decimals,
            name = from.name,
            symbol = from.symbol,
            description = from.description,
            imageUrl = from.imageUrl,
            createdAt = from.createdAt?.toEpochMilliseconds(),
            vmMetadata = from.vmMetadata.toEmbedded(),
            launchpadMetadata = from.launchpadMetadata?.toEmbedded(),
            socialLinks = from.socialLinks
                .takeIf { it.isNotEmpty() }
                ?.map { it.toSerialized() }
                ?.let { json.encodeToString(it) },
            billCustomizationsJson = from.billCustomizations
                ?.toSerialized()
                ?.let { json.encodeToString(it) },
        )
    }
}

private fun com.getcode.opencode.model.financial.VmMetadata.toEmbedded() = VmMetadataEmbedded(
    vm = vm.base58(),
    authority = authority.base58(),
    lockDurationInDays = lockDurationInDays,
)

private fun LaunchpadMetadata.toEmbedded() = LaunchpadMetadataEmbedded(
    currencyConfig = currencyConfig.base58(),
    liquidityPool = liquidityPool.base58(),
    seed = seed.base58(),
    authority = authority.base58(),
    mintVault = mintVault.base58(),
    coreMintVault = coreMintVault.base58(),
    currentCirculatingSupplyQuarks = currentCirculatingSupplyQuarks,
    sellFeeBps = sellFeeBps,
    priceAmount = price.decimalValue,
    marketCapAmount = marketCap.decimalValue,
)

private fun SocialLink.toSerialized(): SocialLinkSerialized = when (this) {
    is SocialLink.Website -> SocialLinkSerialized.Website(url)
    is SocialLink.X -> SocialLinkSerialized.X(username)
}

private fun TokenBillCustomizations.toSerialized() = BillCustomizationsSerialized(
    background = when (val bg = background) {
        is BillBackground.Solid -> BillBackgroundSerialized.Solid(bg.colorHex)
        is BillBackground.Gradient -> BillBackgroundSerialized.Gradient(bg.colors)
    },
    texture = texture?.let {
        BillTextureSerialized(
            index = it.index,
            blendMode = it.blendMode.name,
            strength = it.strength,
        )
    },
    icon = icon?.let { Base64.encodeToString(it, Base64.NO_WRAP) },
)