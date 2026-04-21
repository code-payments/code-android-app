package com.flipcash.app.core.tokens

import android.net.Uri
import com.flipcash.services.models.ModerationResult
import com.getcode.opencode.model.ui.TokenBillCustomizations
import com.getcode.solana.keys.Mint

data class CurrencyCreatorDraft(
    val id: Long = 0,
    val name: String,
    val description: String,
    val iconUri: Uri?,
    val customizations: TokenBillCustomizations?,
    val currentStep: CurrencyCreatorStep,
    val nameAttestation: ModerationResult.Attestation,
    val iconAttestation: ModerationResult.Attestation,
    val descriptionAttestation: ModerationResult.Attestation,
    val createdMint: Mint?,
    val savedAtMillis: Long,
)
