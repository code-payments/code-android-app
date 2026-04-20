package com.getcode.opencode.model.financial

import com.getcode.opencode.model.moderation.ModerationAttestation
import com.getcode.opencode.model.ui.TokenBillCustomizations

data class TokenCreateRequest(
    val name: ModerationAttestation.Text,
    val symbol: ModerationAttestation.Text? = null,
    val description: ModerationAttestation.Text?,
    val bill: TokenBillCustomizations?,
    val icon: ModerationAttestation.Image?,
)
