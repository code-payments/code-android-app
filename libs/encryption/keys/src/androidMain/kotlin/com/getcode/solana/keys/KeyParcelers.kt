package com.getcode.solana.keys

import android.os.Parcel
import com.getcode.vendor.Base58
import kotlinx.parcelize.Parceler

/**
 * [PublicKey] and [Mint] used to implement [android.os.Parcelable] directly, writing and reading
 * the base58 string (see the old `writeToParcel`/`Parcel` constructor). An `androidMain` source
 * set can't add a supertype to a `commonMain` class, so that Parcelable conformance moves here as
 * a [Parceler], applied at holder sites via `@TypeParceler`. The parcel format is unchanged: still
 * exactly one string, base58-encoded.
 */
object PublicKeyParceler : Parceler<PublicKey> {
    override fun create(parcel: Parcel): PublicKey = PublicKey(parcel.readString().orEmpty())

    override fun PublicKey.write(parcel: Parcel, flags: Int) {
        parcel.writeString(Base58.encode(bytes.toByteArray()))
    }
}

object MintParceler : Parceler<Mint> {
    override fun create(parcel: Parcel): Mint = Mint(parcel.readString().orEmpty())

    override fun Mint.write(parcel: Parcel, flags: Int) {
        parcel.writeString(Base58.encode(bytes.toByteArray()))
    }
}
