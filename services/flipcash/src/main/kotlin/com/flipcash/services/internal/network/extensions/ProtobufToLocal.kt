package com.flipcash.services.internal.network.extensions


import com.codeinc.flipcash.gen.common.v1.Common
import com.codeinc.flipcash.gen.common.v1.Common.UserId
import com.flipcash.services.internal.extensions.toMint
import com.flipcash.services.internal.extensions.toPublicKey
import com.getcode.opencode.model.core.ID
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import com.codeinc.flipcash.gen.activity.v1.Model as ActivityModels

internal fun ActivityModels.NotificationId.toId(): ID = value.toByteArray().toList()
internal fun UserId.toId(): ID = value.toByteArray().toList()
internal fun Common.PublicKey.toPublicKey(): PublicKey = value.toByteArray().toPublicKey()
internal fun Common.PublicKey.toMint(): Mint = value.toByteArray().toMint()