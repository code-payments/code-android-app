package com.flipcash.app.onramp.internal.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.app.core.encryption.boxOpen
import com.flipcash.app.core.encryption.toPublicKey
import com.flipcash.app.core.phantom.PhantomConnectionResult
import com.flipcash.app.core.phantom.PhantomDeeplinkOrigin
import com.flipcash.app.core.phantom.PhantomEncryptedData
import com.getcode.opencode.model.financial.Fiat
import com.getcode.solana.keys.base58
import com.ionspin.kotlin.crypto.box.Box
import kotlinx.serialization.json.Json

class PhantomDepositState {
    internal var origin by mutableStateOf<PhantomDeeplinkOrigin?>(null)
    private val keyPair = Box.keypair()
    val curvePublicKey: String?
        get() = keyPair.publicKey.toPublicKey().base58()

    var deeplinkState by mutableStateOf(PhantomDeeplinkState.IDLE)
    var amount by mutableStateOf<Fiat?>(null)

    internal var encryptedData: PhantomEncryptedData? = null

    private var connectionResult: PhantomConnectionResult? = null

    fun decrypt(connectionResult: PhantomConnectionResult) {
        this.connectionResult = connectionResult
        try {
            val data = connectionResult.encryptedData.boxOpen(
                privateKey = keyPair.secretKey.map { it.toByte() },
                publicKey = connectionResult.encryptionPublicKey,
                nonce = connectionResult.nonce,
            ).map { String(it.toByteArray()) }
                .fold(
                    onSuccess = {
                        runCatching { Json.decodeFromString<PhantomEncryptedData>(it) }
                            .onFailure { error ->
                                error.printStackTrace()
                                deeplinkState = PhantomDeeplinkState.CONNECTION_ERROR
                            }
                    },
                    onFailure = {
                        it.printStackTrace()
                        deeplinkState = PhantomDeeplinkState.CONNECTION_ERROR
                        Result.failure(it)
                    }
                ).getOrThrow()

            encryptedData = data
            deeplinkState = PhantomDeeplinkState.CONNECTED
        } catch (e: Exception) {
            e.printStackTrace()
            deeplinkState = PhantomDeeplinkState.CONNECTION_ERROR
        }
    }

    fun setOrigin(provider: NavScreenProvider?) {
        origin = PhantomDeeplinkOrigin.fromScreenProvider(provider)
    }

    fun start() {
        deeplinkState = if (encryptedData != null) {
            PhantomDeeplinkState.CONNECTED
        } else {
            PhantomDeeplinkState.STARTED
        }
    }
}

enum class PhantomDeeplinkState {
    IDLE,
    STARTING,
    STARTED,
    CONNECTING,
    CONNECTED,
    TRANSACTING,
    TRANSACTED,
    CONNECTION_ERROR,
    TRANSACTION_ERROR,
}

@Composable
fun rememberPhantomDepositState(): PhantomDepositState {
    return remember { PhantomDepositState() }
}

val LocalPhantomDepositState =
    compositionLocalOf<PhantomDepositState> { throw IllegalStateException() }