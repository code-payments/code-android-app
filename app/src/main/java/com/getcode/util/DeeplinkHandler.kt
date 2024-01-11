package com.getcode.util

import android.content.Intent
import android.net.Uri
import cafe.adriel.voyager.core.screen.Screen
import com.getcode.manager.SessionManager
import com.getcode.models.DeepLinkPaymentRequest
import com.getcode.navigation.screens.HomeScreen
import com.getcode.navigation.screens.LoginScreen
import com.getcode.network.repository.urlDecode
import com.getcode.utils.base64EncodedData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.ExperimentalSerializationApi
import timber.log.Timber
import javax.inject.Inject

/**
 * This class is used to manage intent state across navigation.
 *
 * This hack is in place because determining the users authenticated
 * state takes a long time. The app will try to handle incoming intents
 * before the authentication state is determined.
 * This class caches the incoming deeplink intent so the navigation controller
 * does not override the intent sent by handleDeepLink(intent) in the main activity.
 *
 * If this was not in place the app would try to handle the deeplink before the
 * authentication state is complete and override navigation - dropping the intent
 * in favour of the latest request in the navigation graph.
 */
class DeeplinkHandler @Inject constructor() {
    var debounceIntent: Intent? = null
        set(value) {
            field = value
            intent.value = value
        }

    val intent = MutableStateFlow(debounceIntent)

    fun checkIntent(intent: Intent): Intent? {
        Timber.d("checking intent=${intent.data}")
        handle(intent) ?: return null
        return intent
    }

    fun handle(intent: Intent? = debounceIntent): Pair<Type, List<Screen>>? {
        val uri = intent?.data ?: return null
        val type = uri.deeplinkType ?: return null
        return when (type) {
            is Type.Login -> {
                type to listOf(LoginScreen(type.link))
            }

            is Type.Cash -> {
                type to listOf(HomeScreen(cashLink = type.link))
            }

            is Type.Sdk -> {
                Timber.d("sdk=${type.payload}")
                type to listOf(HomeScreen(requestPayload = type.payload))
            }

            is Type.Unknown -> null
        }
    }

    private val Uri.deeplinkType: Type?
        get() = when (val segment = lastPathSegment) {
            "login" -> {
                if (SessionManager.isAuthenticated() == true) {
                    null
                } else {
                    var entropy = fragments[Key.entropy]
                    if (entropy == null) {
                        entropy = this.getQueryParameter("data")
                    }

                    Type.Login(entropy.also { Timber.d("entropy=$it") })
                }
            }

            "cash", "c" -> Type.Cash(fragments[Key.entropy])
            "payment-request-modal-desktop", "payment-request-modal-mobile" -> {
                Type.Sdk(fragments[Key.payload]?.urlDecode())
            }
            else -> Type.Unknown(path = segment)
        }

    private val Uri.fragments: Map<Key, String>
        get() {
            return this.toString().split("/")
                .mapNotNull { fragment ->
                    val data = Key.entries
                        .map { key -> key to "${key.value}=" }
                        .filter { (key, prefix) -> fragment.startsWith(prefix) }
                        .firstNotNullOfOrNull { (key, prefix) -> key to fragment.removePrefix(prefix) }

                    data ?: return@mapNotNull null
                }.associate { (key, value) -> key to value }
        }

    sealed interface Type {
        data class Login(val link: String?) : Type
        data class Cash(val link: String?) : Type
        data class Sdk(val payload: String?) : Type
        data class Unknown(val path: String?) : Type
    }

    @Suppress("ClassName")
    sealed interface Key {
        val value: String

        data object entropy : Key {
            override val value: String = "e"
        }

        data object payload : Key {
            override val value: String = "p"
        }

        // unused
        data object key : Key {
            override val value: String = "k"
        }

        // unused
        data object data : Key {
            override val value: String = "d"
        }

        companion object {
            val entries = listOf(entropy, payload, key, data)
        }
    }
}