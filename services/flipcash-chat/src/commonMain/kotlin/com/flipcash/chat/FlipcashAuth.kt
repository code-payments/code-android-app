package com.flipcash.chat

import com.codeinc.flipcash.wire.common.v1.Auth
import com.codeinc.flipcash.wire.common.v1.PublicKey
import com.codeinc.flipcash.wire.common.v1.Signature
import com.getcode.ed25519kmp.Ed25519
import okio.ByteString.Companion.toByteString

/**
 * `common.v1.Auth` for a request, as Android's `authenticate()` builds it: [owner] signs the
 * request's bytes with `auth` unset (protobuf-java's `buildPartial()` before `setAuth`).
 */
internal fun authFor(unsignedRequest: ByteArray, owner: Ed25519.KeyPair): Auth = Auth(
    key_pair = Auth.KeyPair(
        pub_key = PublicKey(value_ = owner.publicKeyBytes.toByteString()),
        signature = Signature(value_ = Ed25519.sign(unsignedRequest, owner).toByteString()),
    ),
)
