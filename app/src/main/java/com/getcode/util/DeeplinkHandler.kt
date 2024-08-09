package com.getcode.util

import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import cafe.adriel.voyager.core.screen.Screen
import com.getcode.models.DeepLinkRequest
import com.getcode.models.encode
import com.getcode.navigation.screens.HomeScreen
import com.getcode.navigation.screens.LoginScreen
import com.getcode.network.repository.encodeBase64
import com.getcode.network.repository.urlDecode
import com.getcode.utils.TraceType
import com.getcode.utils.base64EncodedData
import com.getcode.utils.trace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class DeeplinkResult(
    val type: DeeplinkHandler.Type,
    val stack: List<Screen>,
)

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
            intent.value = value
            field = value
            trace("debounced intent data=${value?.data}", type = TraceType.Silent)
        }

    val intent = MutableStateFlow(debounceIntent)

    fun handle(intent: Intent? = debounceIntent): DeeplinkResult? {
        val uri = when {
            intent?.data != null -> intent.data
            intent?.getStringExtra(Intent.EXTRA_TEXT) != null -> {
                val sharedLink = intent.getStringExtra(Intent.EXTRA_TEXT)?.toUri() ?: return null
                sharedLink.resolveSharedEntity
            }

            else -> null
        } ?: return null

        return when (val type = uri.deeplinkType) {
            is Type.Login -> {
                DeeplinkResult(
                    type,
                    listOf(LoginScreen(type.link)),
                )
            }

            is Type.Cash -> {
                Timber.d("cash=${type.link}")
                DeeplinkResult(
                    type,
                    listOf(HomeScreen(cashLink = type.link)),
                )
            }

            is Type.Sdk -> {
                Timber.d("sdk=${type.payload}")
                val request = type.payload?.base64EncodedData()?.let { DeepLinkRequest.from(it) }
                DeeplinkResult(
                    type,
                    listOf(HomeScreen(request = request)),
                )
            }

            is Type.Tip -> {
                Timber.d("tipcard for ${type.username} on ${type.platform}")
                DeeplinkResult(
                    type,
                    listOf(
                        HomeScreen(
                            request = DeepLinkRequest.fromTipCardUsername(
                                type.platform,
                                type.username
                            )
                        )
                    ),
                )
            }

            is Type.Unknown -> null
        }
    }

    /**
     * Handles converting inbound shared content with possible deeplinks
     * e.g sharing a tweet to trigger a tipcard flow
     */
    private val Uri.resolveSharedEntity: Uri
        get() {
            // https://x.com/<username>/status/<tweetId>
            return when {
                this.host == "x.com" || this.host == "twitter.com" -> {
                    // convert shared tweets to owner's tip card
                    val username = pathSegments.firstOrNull() ?: return this
                    Uri.parse(Linkify.tipCard(username, "x"))
                }

                else -> this
            }
        }

    private val Uri.deeplinkType: Type
        get() {
            // check for tipcard URLs
            val components = pathSegments
            if (components.count() >= 2 && components[0] == "x" && components[1].isNotEmpty()) {
                return Type.Tip(components[0], components[1])
            }

            return when (val segment = lastPathSegment) {
                "login" -> {
                    var entropy = fragments[Key.entropy]
                    if (entropy == null) {
                        entropy = this.getQueryParameter("data")
                    }

                    Type.Login(entropy.also { Timber.d("entropy=$it") })
                }

                "cash", "c" -> Type.Cash(fragments[Key.entropy])

                // support all variations of SDK request triggers
                in Type.Sdk.regex -> Type.Sdk(fragments[Key.payload]?.urlDecode())
                else -> Type.Unknown(path = segment)
            }
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
        data class Tip(val platform: String, val username: String) : Type
        data class Sdk(val payload: String?) : Type {
            companion object {
                val regex = Regex("^(login|payment|tip)?-?request-(modal|page)-(mobile|desktop)\$")
            }
        }

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

private operator fun Regex.contains(text: String?): Boolean =
    text?.let { this.matches(it) } ?: false
