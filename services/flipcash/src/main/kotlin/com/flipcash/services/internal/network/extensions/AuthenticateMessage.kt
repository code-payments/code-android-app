package com.flipcash.services.internal.network.extensions

import com.codeinc.flipcash.gen.common.v1.Common
import com.getcode.ed25519.Ed25519
import com.google.protobuf.GeneratedMessageLite
import java.io.ByteArrayOutputStream

/**
 * Builds the request with an [Common.Auth] that covers every field set on it.
 *
 * The signature is taken over the builder as it stands, and the server verifies it against the
 * whole request minus the auth field. A field set after signing is therefore in what the server
 * checks and missing from what the client signed, and the call comes back `UNAUTHENTICATED` — as
 * GetBlobs did, where a late access context meant re-minting another user's avatar URL always
 * failed.
 *
 * Ordering is the caller's problem only if the caller can get it wrong. This signs and builds in
 * one step, so there is no builder left to set a field on. Prefer it to [authenticate].
 *
 * @param attachAuth assigns the auth onto the request, almost always `{ setAuth(it) }`. It takes
 *   the builder as a receiver because the generated request types share no `setAuth` supertype.
 */
internal fun <M : GeneratedMessageLite<M?, B?>, B : GeneratedMessageLite.Builder<M?, B?>> GeneratedMessageLite.Builder<M, B>.buildAuthenticated(
    owner: Ed25519.KeyPair,
    attachAuth: B.(Common.Auth) -> Unit,
): M {
    val auth = authenticate(owner)

    @Suppress("UNCHECKED_CAST")
    (this as B).attachAuth(auth)

    return build()!!
}

/**
 * Signs the builder as it stands and returns the auth to assign onto it.
 *
 * Anything set after this call is outside the signature, which the type system does not enforce.
 * Use [buildAuthenticated] instead unless the auth genuinely cannot be the last thing assigned.
 */
internal fun <M : GeneratedMessageLite<M?, B?>, B : GeneratedMessageLite.Builder<M?, B?>> GeneratedMessageLite.Builder<M, B>.authenticate(owner: Ed25519.KeyPair): Common.Auth {
    // dump message up until this point into a ByteArray
    val bos = ByteArrayOutputStream()
    this.buildPartial().writeTo(bos)

    /**
     * sign message up to this point with owner and convert to [com.codeinc.flipchat.gen.common.v1.Signature]
     */
    val signature = Ed25519.sign(bos.toByteArray(), owner).asSignature()
    // build Auth.Keypair sub model
    val keyPairModel = Common.Auth.KeyPair.newBuilder()
        .setPubKey(owner.asPublicKey())
        .apply { setSignature(signature) }
        .build()

    // return Auth model
    return Common.Auth.newBuilder()
        .setKeyPair(keyPairModel)
        .build()
}
