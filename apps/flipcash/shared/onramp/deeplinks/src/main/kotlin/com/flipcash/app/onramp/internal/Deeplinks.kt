package com.flipcash.app.onramp.internal

import android.net.Uri
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.encryption.boxSeal
import com.flipcash.app.core.encryption.toPublicKey
import com.flipcash.app.core.onramp.deeplinks.OnRampDeeplinkOrigin
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.getcode.solana.keys.base58
import com.getcode.utils.base58
import com.ionspin.kotlin.crypto.box.BoxKeyPair
import com.ionspin.kotlin.crypto.secretbox.crypto_secretbox_NONCEBYTES
import com.ionspin.kotlin.crypto.util.LibsodiumRandom
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

internal val BoxKeyPair.curvePublicKey: String?
    get() = publicKey.toPublicKey().base58()

internal fun buildConnectDeeplink(
    provider: OnRampProvider.UsesDeeplinks,
    curvePublicKey: String?,
    origin: AppRoute?,
): Uri? {
    val deeplinkOrigin = OnRampDeeplinkOrigin.fromRoute(origin)
    val originEncoded = deeplinkOrigin?.forUri() ?: "unknown"
    val redirectUrl = "${provider.redirectUrlPrefix}/connected?origin=$originEncoded"
    return Uri.Builder()
        .scheme("https")
        .authority(provider.authority)
        .path("ul/v1/connect")
        .appendQueryParameter("app_url", "https://app.flipcash.com")
        .appendQueryParameter("dapp_encryption_public_key", curvePublicKey)
        .appendQueryParameter("cluster", "mainnet-beta")
        .appendQueryParameter("redirect_link", redirectUrl)
        .build()
}

internal fun buildTransactionDeeplink(
    provider: OnRampProvider.UsesDeeplinks,
    curvePublicKey: String?,
    encryptionPublicKey: List<Byte>,
    unsignedTransaction: List<Byte>,
    session: String?,
    secretKey: List<Byte>,
    origin: AppRoute?,
): Uri? {
    val nonce = LibsodiumRandom.buf(crypto_secretbox_NONCEBYTES).map { it.toByte() }
    val payload = mapOf(
        "transaction" to unsignedTransaction.base58,
        "session" to session
    )

    val message = Json.encodeToString(payload).boxSeal(
        privateKey = secretKey,
        publicKey = encryptionPublicKey,
        nonce = nonce,
    ).getOrNull() ?: return null

    val deeplinkOrigin = OnRampDeeplinkOrigin.fromRoute(origin)
    val originEncoded = deeplinkOrigin?.forUri() ?: "unknown"

    val redirectUrl = "${provider.redirectUrlPrefix}/signed?origin=$originEncoded"
    return Uri.Builder()
        .scheme("https")
        .authority(provider.authority)
        .path("ul/v1/signTransaction")
        .appendQueryParameter("app_url", "https://app.flipcash.com")
        .appendQueryParameter("dapp_encryption_public_key", curvePublicKey)
        .appendQueryParameter("cluster", "mainnet-beta")
        .appendQueryParameter("nonce", nonce.base58)
        .appendQueryParameter("payload", message.base58)
        .appendQueryParameter("redirect_link", redirectUrl)
        .build()
}
