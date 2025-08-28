package com.flipcash.app.onramp.internal

import android.net.Uri
import com.flipcash.app.core.encryption.boxSeal
import com.flipcash.app.core.phantom.PhantomDeeplinkOrigin
import com.getcode.utils.base58
import com.ionspin.kotlin.crypto.secretbox.crypto_secretbox_NONCEBYTES
import com.ionspin.kotlin.crypto.util.LibsodiumRandom
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal fun buildConnectDeeplink(state: PhantomDepositState): Uri {
    val origin = PhantomDeeplinkOrigin.fromScreenProvider(state.origin)
    val originEncoded = origin?.forUri() ?: "unknown"
    val redirectUrl = "https://app.flipcash.com/phantom/connected?origin=$originEncoded"
    return Uri.Builder()
        .scheme("https")
        .authority("phantom.app")
        .path("ul/v1/connect")
        .appendQueryParameter("app_url", "https://flipcash.com")
        .appendQueryParameter("dapp_encryption_public_key", state.curvePublicKey)
        .appendQueryParameter("cluster", "mainnet-beta")
        .appendQueryParameter("redirect_link", redirectUrl)
        .build()
}

internal fun buildTransactionDeeplink(state: PhantomDepositState): Uri? {
    val publicKey = state.phantomEncryptionPublicKey ?: return null
    val transaction = state.unsignedTransaction ?: return null
    val nonce = LibsodiumRandom.buf(crypto_secretbox_NONCEBYTES).map { it.toByte() }
    val payload = mapOf(
        "transaction" to transaction.serialize().base58,
        "session" to state.walletConnection?.session
    )

    val message = Json.encodeToString(payload).boxSeal(
        privateKey = state.keyPair.secretKey.map { it.toByte() }.toList(),
        publicKey = publicKey,
        nonce = nonce,
    ).getOrNull() ?: return null

    val origin = PhantomDeeplinkOrigin.fromScreenProvider(state.origin)
    val originEncoded = origin?.forUri() ?: "unknown"

    val redirectUrl = "https://app.flipcash.com/phantom/signed?origin=$originEncoded"
    return Uri.Builder()
        .scheme("https")
        .authority("phantom.app")
        .path("ul/v1/signTransaction")
        .appendQueryParameter("dapp_encryption_public_key", state.curvePublicKey)
        .appendQueryParameter("cluster", "mainnet-beta")
        .appendQueryParameter("nonce", nonce.base58)
        .appendQueryParameter("payload", message.base58)
        .appendQueryParameter("redirect_link", redirectUrl)
        .build()
}