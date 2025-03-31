package xyz.flipchat.app.oauth

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import xyz.flipchat.app.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom

private const val baseRedirectUri = "flipchat://app.flipchat.xyz/oauth"

sealed interface OAuthProvider {
    fun launchIntent(context: Context): Intent
    val redirectUri: String

    data object X : OAuthProvider {
        private val codeVerifier = generateCodeVerifier()
        internal val codeChallenge = generateCodeChallenge(codeVerifier)
        internal val randomStateString = generateRandomStateString()

        override val redirectUri: String = "$baseRedirectUri/x"

        override fun launchIntent(context: Context): Intent = Intent(
            context,
            PrivateOauthResultActivity::class.java
        ).apply {
            putExtra(PrivateOauthResultActivity.OAUTH_URI, buildAuthUri(this@X).toString())
            putExtra(PrivateOauthResultActivity.REDIRECT_URI, redirectUri)
        }

        fun exchangeAuthCodeForAccessToken(authCode: String?, onResult: (String?) -> Unit) {
            val clientId = BuildConfig.X_CLIENT_ID
            val requestBody = "grant_type=authorization_code" +
                    "&client_id=$clientId" +
                    "&code=$authCode" +
                    "&redirect_uri=$redirectUri" +
                    "&code_verifier=$codeVerifier"

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val url = URL("https://api.twitter.com/2/oauth2/token")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.setRequestProperty(
                        "Content-Type",
                        "application/x-www-form-urlencoded"
                    )
                    connection.doOutput = true

                    connection.outputStream.use { it.write(requestBody.toByteArray()) }

                    val response = connection.inputStream.bufferedReader().readText()
                    Timber.d("TwitterOAuth", "Access Token Response: $response")

                    val jsonObject = JSONObject(response)
                    val accessToken = jsonObject.optString("access_token")
                    onResult(accessToken)
                } catch (e: Exception) {
                    Timber.e("TwitterOAuth", "Token exchange failed", e)
                    onResult(null)
                }
            }
        }

    }
}


private fun generateCodeVerifier(): String {
    val secureRandom = SecureRandom()
    val bytes = ByteArray(32)
    secureRandom.nextBytes(bytes)
    return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}

private fun generateRandomStateString(): String {
    val secureRandom = SecureRandom()
    val bytes = ByteArray(32)
    secureRandom.nextBytes(bytes)
    return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}

fun generateCodeChallenge(codeVerifier: String): String {
    val bytes = codeVerifier.toByteArray(Charsets.US_ASCII)
    val messageDigest = MessageDigest.getInstance("SHA-256")
    val digest = messageDigest.digest(bytes)
    return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
}

private fun buildAuthUri(type: OAuthProvider): Uri {
    return when (type) {
        is OAuthProvider.X -> {
            Uri.parse("https://twitter.com/i/oauth2/authorize").buildUpon()
                .appendQueryParameter("client_id", BuildConfig.X_CLIENT_ID)
                .appendQueryParameter("redirect_uri", type.redirectUri)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("scope", "tweet.read users.read offline.access")
                .appendQueryParameter("code_challenge", type.codeChallenge)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("state", type.randomStateString)
                .build()
        }
    }
}

