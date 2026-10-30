package com.getcode.opencode.model.core

/**
 * The polymorphic payload carried by an [OpenCodePayload]. Cash/gift-card codes carry a
 * [com.getcode.opencode.model.financial.Fiat] amount; tip codes carry a [UserId].
 *
 * Not `sealed`: [com.getcode.opencode.model.financial.Fiat] implements it from another package,
 * which a sealed interface would forbid.
 */
interface PayloadValue

/**
 * The recipient's user id carried by a tip code. Serialized as the raw 16-byte id
 * ([OpenCodePayload.USER_ID_LENGTH]) and used to resolve the recipient when the code is scanned.
 */
data class UserId(val value: ID) : PayloadValue
