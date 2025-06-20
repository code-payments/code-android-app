package com.flipcash.services.internal.network.extensions


import com.codeinc.flipcash.gen.common.v1.Common
import com.codeinc.flipcash.gen.common.v1.Common.UserId
import com.codeinc.flipcash.gen.common.v1.Common.Signature
import com.flipcash.services.internal.extensions.toPublicKey
import com.getcode.ed25519.Ed25519
import com.codeinc.flipcash.gen.activity.v1.Model as ActivityModels
import com.codeinc.flipcash.gen.pool.v1.Model as PoolModels
import com.getcode.opencode.model.core.ID
import com.getcode.solana.keys.PublicKey

internal fun ActivityModels.NotificationId.toId(): ID = value.toByteArray().toList()
internal fun UserId.toId(): ID = value.toByteArray().toList()
internal fun PoolModels.PoolId.toId(): ID = value.toByteArray().toList()
internal fun PoolModels.BetId.toId(): ID = value.toByteArray().toList()
internal fun Signature.toPublicKey(): PublicKey = value.toByteArray().toPublicKey()
internal fun Signature.toKeyPair(): Ed25519.KeyPair = Ed25519.createKeyPair(value.toByteArray())
internal fun Common.PublicKey.toPublicKey(): PublicKey = value.toByteArray().toPublicKey()
