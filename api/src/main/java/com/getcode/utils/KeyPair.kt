package com.getcode.utils

import android.util.Base64
import com.getcode.crypt.Sha256Hash
import com.getcode.ed25519.Ed25519
import com.getcode.ed25519.Ed25519.KeyPair
import com.getcode.solana.keys.Seed32
import timber.log.Timber

fun deriveRendezvousKey(from: ByteArray): KeyPair {
    return Ed25519.createKeyPair(Base64.encodeToString(Sha256Hash.hash(from), Base64.DEFAULT))
}