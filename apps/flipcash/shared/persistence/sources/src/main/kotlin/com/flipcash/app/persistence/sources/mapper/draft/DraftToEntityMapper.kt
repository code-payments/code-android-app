package com.flipcash.app.persistence.sources.mapper.draft

import com.flipcash.app.core.tokens.CurrencyCreatorDraft
import com.flipcash.app.persistence.converters.BillBackgroundSerialized
import com.flipcash.app.persistence.converters.BillCustomizationsSerialized
import com.flipcash.app.persistence.converters.BillTextureSerialized
import com.flipcash.app.persistence.converters.ModerationAttestationSerialized
import com.flipcash.app.persistence.converters.ModerationAttestationsSerialized
import com.flipcash.app.persistence.entities.CurrencyCreatorDraftEntity
import com.flipcash.services.models.ModerationResult
import com.getcode.opencode.mapper.Mapper
import com.getcode.opencode.model.ui.BillBackground
import com.getcode.opencode.model.ui.TokenBillCustomizations
import com.getcode.solana.keys.base58
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

class DraftToEntityMapper @Inject constructor() : Mapper<CurrencyCreatorDraft, CurrencyCreatorDraftEntity> {

    private val json = Json { encodeDefaults = true }

    override fun map(from: CurrencyCreatorDraft): CurrencyCreatorDraftEntity {
        return CurrencyCreatorDraftEntity(
            id = from.id,
            name = from.name,
            description = from.description,
            iconUri = from.iconUri?.toString(),
            billCustomizations = from.customizations?.toSerialized()?.let { json.encodeToString(it) },
            attestations = json.encodeToString(from.toAttestationsSerialized()),
            currentStep = json.encodeToString(from.currentStep),
            createdMint = from.createdMint?.base58(),
            savedAt = from.savedAtMillis,
        )
    }
}

private fun CurrencyCreatorDraft.toAttestationsSerialized() = ModerationAttestationsSerialized(
    name = nameAttestation.toSerialized(),
    icon = iconAttestation.toSerialized(),
    description = descriptionAttestation.toSerialized(),
)

private fun ModerationResult.Attestation.toSerialized() = ModerationAttestationSerialized(
    rawValue = rawValue,
    hashBytes = hash.bytes.toList(),
    timestampEpochMs = timestamp.toEpochMilliseconds(),
    userId = userId,
    attestorBytes = attestor.bytes,
    signatureBytes = signature.bytes,
)

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
    icon = icon?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) },
)
