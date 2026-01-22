package com.flipcash.app.onramp.internal

import android.net.Uri
import com.flipcash.app.core.encryption.boxSeal
import com.flipcash.app.core.onramp.deeplinks.OnRampDeeplinkOrigin
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.getcode.utils.base58
import com.ionspin.kotlin.crypto.secretbox.crypto_secretbox_NONCEBYTES
import com.ionspin.kotlin.crypto.util.LibsodiumRandom
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


private val OnRampProvider.UsesDeeplinks.authority: String
    get() = when (this) {
        OnRampProvider.Backpack -> "backpack.app"
        OnRampProvider.Phantom -> "phantom.app"
        OnRampProvider.Solflare -> "solflare.com"
    }

internal val OnRampProvider.UsesDeeplinks.packageName: String
    get() = when (this) {
        OnRampProvider.Backpack -> "app.backpack.mobile"
        OnRampProvider.Phantom -> "app.phantom"
        OnRampProvider.Solflare -> "com.solflare.mobile"
    }

private val OnRampProvider.UsesDeeplinks.redirectUrlPrefix: String
    // pathSegment[1] is fed into encryption pub key look up in the URI
    // backpack returns 'wallet_'
    get() = when (this) {
        OnRampProvider.Backpack -> "https://app.flipcash.com/external/wallet"
        OnRampProvider.Phantom -> "https://app.flipcash.com/external/phantom"
        OnRampProvider.Solflare -> "https://app.flipcash.com/external/solflare"
    }


internal fun buildConnectDeeplink(state: ExternalWalletDeeplinkState): Uri? {
    val provider = state.provider ?: return null

    val origin = OnRampDeeplinkOrigin.fromRoute(state.origin)
    val originEncoded = origin?.forUri() ?: "unknown"
    val redirectUrl = "${provider.redirectUrlPrefix}/connected?origin=$originEncoded"
    return Uri.Builder()
        .scheme("https")
        .authority(provider.authority)
        .path("ul/v1/connect")
        .appendQueryParameter("app_url", "https://flipcash.com")
        .appendQueryParameter("dapp_encryption_public_key", state.curvePublicKey)
        .appendQueryParameter("cluster", "mainnet-beta")
        .appendQueryParameter("redirect_link", redirectUrl)
        .build()
}

internal fun buildTransactionDeeplink(state: ExternalWalletDeeplinkState): Uri? {
    val provider = state.provider ?: return null

    val publicKey = state.phantomEncryptionPublicKey ?: return null
    val transaction = state.unsignedTransaction ?: return null
    val nonce = LibsodiumRandom.buf(crypto_secretbox_NONCEBYTES).map { it.toByte() }
    val payload = mapOf(
        "transaction" to transaction.base58,
        "session" to state.walletConnection?.session
    )

    val message = Json.encodeToString(payload).boxSeal(
        privateKey = state.keyPair.secretKey.map { it.toByte() }.toList(),
        publicKey = publicKey,
        nonce = nonce,
    ).getOrNull() ?: return null

    val origin = OnRampDeeplinkOrigin.fromRoute(state.origin)
    val originEncoded = origin?.forUri() ?: "unknown"

    val redirectUrl = "${provider.redirectUrlPrefix}/signed?origin=$originEncoded"
    return Uri.Builder()
        .scheme("https")
        .authority(provider.authority)
        .path("ul/v1/signTransaction")
        .appendQueryParameter("dapp_encryption_public_key", state.curvePublicKey)
        .appendQueryParameter("cluster", "mainnet-beta")
        .appendQueryParameter("nonce", nonce.base58)
        .appendQueryParameter("payload", message.base58)
        .appendQueryParameter("redirect_link", redirectUrl)
        .build()
}