package com.flipcash.app.persistence.sources.mapper.tokens

import android.util.Base64
import com.flipcash.app.persistence.converters.BillBackgroundSerialized
import com.flipcash.app.persistence.converters.BillCustomizationsSerialized
import com.flipcash.app.persistence.converters.SocialLinkSerialized
import com.flipcash.app.persistence.embedded.LaunchpadMetadataEmbedded
import com.flipcash.app.persistence.embedded.VmMetadataEmbedded
import com.flipcash.app.persistence.entities.TokenEntity
import com.getcode.opencode.mapper.Mapper
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.LaunchpadMetadata
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.SocialLink
import com.getcode.opencode.model.financial.VmMetadata
import com.getcode.opencode.model.ui.BillBackground
import com.getcode.opencode.model.ui.BillTexture
import com.getcode.opencode.model.ui.BlendMode
import com.getcode.opencode.model.ui.TokenBillCustomizations
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import kotlinx.serialization.json.Json
import kotlin.time.Instant
import javax.inject.Inject

class EntityToTokenMapper @Inject constructor() : Mapper<TokenEntity, MintMetadata> {

    private val json = Json { ignoreUnknownKeys = true }

    override fun map(from: TokenEntity): MintMetadata {
        return MintMetadata(
            address = Mint(from.address),
            decimals = from.decimals,
            name = from.name,
            symbol = from.symbol,
            description = from.description,
            imageUrl = from.imageUrl,
            createdAt = from.createdAt?.let { Instant.fromEpochMilliseconds(it) },
            vmMetadata = from.vmMetadata.toDomain(),
            launchpadMetadata = from.launchpadMetadata?.toDomain(),
            socialLinks = from.socialLinks
                ?.let { json.decodeFromString<List<SocialLinkSerialized>>(it) }
                ?.map { it.toDomain() }
                ?: emptyList(),
            billCustomizations = from.billCustomizationsJson
                ?.let { json.decodeFromString<BillCustomizationsSerialized>(it) }
                ?.toDomain(),
        )
    }
}

private fun VmMetadataEmbedded.toDomain() = VmMetadata(
    vm = PublicKey(vm),
    authority = PublicKey(authority),
    lockDurationInDays = lockDurationInDays,
)

private fun LaunchpadMetadataEmbedded.toDomain() = LaunchpadMetadata(
    currencyConfig = PublicKey(currencyConfig),
    liquidityPool = PublicKey(liquidityPool),
    seed = PublicKey(seed),
    authority = PublicKey(authority),
    mintVault = PublicKey(mintVault),
    coreMintVault = PublicKey(coreMintVault),
    currentCirculatingSupplyQuarks = currentCirculatingSupplyQuarks,
    sellFeeBps = sellFeeBps,
    price = Fiat(fiat = priceAmount),
    marketCap = Fiat(fiat = marketCapAmount),
)

private fun SocialLinkSerialized.toDomain(): SocialLink = when (this) {
    is SocialLinkSerialized.Website -> SocialLink.Website(url)
    is SocialLinkSerialized.X -> SocialLink.X(username)
}

private fun BillCustomizationsSerialized.toDomain() = TokenBillCustomizations(
    background = when (val bg = background) {
        is BillBackgroundSerialized.Solid -> BillBackground.Solid(bg.colorHex)
        is BillBackgroundSerialized.Gradient -> BillBackground.Gradient(bg.colors)
    },
    texture = texture?.let {
        BillTexture(
            index = it.index,
            blendMode = runCatching { BlendMode.valueOf(it.blendMode) }
                .getOrDefault(BlendMode.Normal),
            strength = it.strength,
        )
    },
    icon = icon?.let { Base64.decode(it, Base64.NO_WRAP) },
)