package com.getcode.opencode.model.core

/**
 * The polymorphic payload carried by an [OpenCodePayload]. Cash/gift-card codes carry a
 * [com.getcode.opencode.model.financial.Fiat] amount; tip codes carry a [Username].
 *
 * Not `sealed`: [com.getcode.opencode.model.financial.Fiat] implements it from another package,
 * which a sealed interface would forbid.
 */
interface PayloadValue

/**
 * The username that uniquely represents a user's tip code. Serialized into the scan code (max
 * [OpenCodePayload.USERNAME_LENGTH] bytes) and used to resolve the recipient when the code is
 * scanned.
 */
data class Username(val value: String) : PayloadValue
