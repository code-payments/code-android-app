package com.getcode.util

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import cafe.adriel.voyager.core.screen.Screen
import com.getcode.manager.SessionManager
import com.getcode.navigation.screens.HomeScreen
import com.getcode.navigation.screens.LoginScreen
import kotlinx.coroutines.flow.MutableStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

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
@Singleton
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
        return when (val type = uri.deeplinkType) {
            is Type.Login -> {
                type to listOf(LoginScreen(type.link))
            }
            is Type.Cash -> {
                Timber.d("cashlink=${type.link}")
                type to listOf(HomeScreen(cashLink = type.link))
            }
            Type.Sdk -> null
            is Type.Unknown -> null
        }
    }

    private val Uri.deeplinkType: Type
        get() = when (val segment = lastPathSegment) {
        "login" -> {
            var entropy = getEntropy()
            if (entropy == null) {
                entropy = this.getQueryParameter("data")
            }

            Type.Login(entropy)
        }
        "cash", "c" -> Type.Cash(getEntropy())
        else -> Type.Unknown(path = segment)
    }

    private fun Uri.getEntropy(): String? {
        val fragment = fragment ?: return null

        return Key.entries
            .map { key -> "/${key.value}=" }
            .filter { prefix -> fragment.startsWith(prefix) }
            .firstNotNullOfOrNull { prefix -> fragment.removePrefix(prefix) }
    }

    sealed interface Type {
        data class Login(val link: String?): Type
        data class Cash(val link: String?): Type
        data object Sdk: Type
        data class Unknown(val path: String?): Type
    }

    @Suppress("ClassName")
    sealed interface Key {
        val value: String
        data object entropy: Key {
            override val value: String = "e"
        }
        data object payload: Key {
            override val value: String = "p"
        }
        // unused
        data object key: Key {
            override val value: String = "k"
        }
        // unused
        data object data: Key {
            override val value: String = "d"
        }

        companion object {
            val entries = listOf(entropy, payload, key, data)
        }
    }
}