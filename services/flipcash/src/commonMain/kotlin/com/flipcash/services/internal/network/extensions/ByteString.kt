package com.flipcash.services.internal.network.extensions

import com.google.protobuf.ByteString

internal fun List<Byte>.toByteString(): ByteString = ByteString.copyFrom(toByteArray())
internal fun ByteArray.toByteString(): ByteString = ByteString.copyFrom(this)
