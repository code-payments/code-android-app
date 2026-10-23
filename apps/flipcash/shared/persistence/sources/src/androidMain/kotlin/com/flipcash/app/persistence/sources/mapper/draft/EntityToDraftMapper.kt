package com.flipcash.app.persistence.sources.mapper.draft

import android.net.Uri
import android.util.Base64
import com.flipcash.app.core.tokens.CurrencyCreatorDraft
import com.flipcash.app.core.tokens.CurrencyCreatorStep
import com.flipcash.app.persistence.converters.BillBackgroundSerialized
import com.flipcash.app.persistence.converters.BillCustomizationsSerialized
import com.flipcash.app.persistence.converters.ModerationAttestationSerialized
import com.flipcash.app.persistence.converters.ModerationAttestationsSerialized
import com.flipcash.app.persistence.entities.CurrencyCreatorDraftEntity
import com.flipcash.services.models.ModerationResult
import com.getcode.crypt.Sha256Hash
import com.getcode.opencode.mapper.Mapper
import com.getcode.opencode.model.ui.BillBackground
import com.getcode.opencode.model.ui.BillTexture
import com.getcode.opencode.model.ui.BlendMode
import com.getcode.opencode.model.ui.TokenBillCustomizations
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.Signature
import kotlinx.serialization.json.Json
import kotlin.time.Instant
import javax.inject.Inject

class EntityToDraftMapper @Inject constructor() : Mapper<CurrencyCreatorDraftEntity, CurrencyCreatorDraft> {

    private val json = Json { ignoreUnknownKeys = true }

    override fun map(from: CurrencyCreatorDraftEntity): CurrencyCreatorDraft {
        val attestations = from.attestations
            ?.let { runCatching { json.decodeFromString<ModerationAttestationsSerialized>(it) }.getOrNull() }

        val customizations = from.billCustomizations
            ?.let { runCatching { json.decodeFromString<BillCustomizationsSerialized>(it) }.getOrNull() }
            ?.toDomain()

        val currentStep = runCatching {
            json.decodeFromString<CurrencyCreatorStep>(from.currentStep)
        }.getOrDefault(CurrencyCreatorStep.NameSelection())

        return CurrencyCreatorDraft(
            id = from.id,
            name = from.name,
            description = from.description,
            iconUri = from.iconUri?.let { Uri.parse(it) },
            customizations = customizations,
            currentStep = currentStep,
            nameAttestation = attestations?.name?.toDomain() ?: ModerationResult.Attestation.Empty,
            iconAttestation = attestations?.icon?.toDomain() ?: ModerationResult.Attestation.Empty,
            descriptionAttestation = attestations?.description?.toDomain() ?: ModerationResult.Attestation.Empty,
            createdMint = from.createdMint?.let { Mint(it) },
            savedAtMillis = from.savedAt,
        )
    }
}

private fun ModerationAttestationSerialized.toDomain(): ModerationResult.Attestation {
    if (rawValue.isEmpty()) return ModerationResult.Attestation.Empty
    return ModerationResult.Attestation(
        rawValue = rawValue,
        hash = Sha256Hash.wrap(hashBytes.toByteArray()),
        timestamp = Instant.fromEpochMilliseconds(timestampEpochMs),
        userId = userId,
        attestor = PublicKey(attestorBytes),
        signature = Signature(signatureBytes),
    )
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
