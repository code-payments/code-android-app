package xyz.flipchat.app.oauth

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.content.IntentSanitizer

@Composable
fun rememberLauncherForOAuth(
    provider: OAuthProvider,
    onResult: (String?) -> Unit
): ManagedActivityResultLauncher<Intent, ActivityResult> {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                val data: Intent? = result.data
                data?.data?.let { uri ->
                    when (provider) {
                        is OAuthProvider.X -> {
                            println("uri=$uri")
                            val authCode = uri.getQueryParameter("code")
                            provider.exchangeAuthCodeForAccessToken(authCode) {
                                onResult(it)
                            }
                        }
                    }
                }
            }
            Activity.RESULT_CANCELED -> {
                println("OAuth flow canceled")
                val error = result.data?.data?.getQueryParameter("error")
                val errorDesc = result.data?.data?.getQueryParameter("error_description")
                println("Error: $error - $errorDesc")
            }

        }
    }

    return launcher
}

internal class PrivateOauthResultActivity: ComponentActivity() {

    companion object {
        const val OAUTH_URI = "oauth_uri"
        const val REDIRECT_URI = "redirect_uri"
    }

    private var redirectUri: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        println("onCreate OauthResultActivity")
        val oauthUriString = intent.extras?.getString(OAUTH_URI)
        if (oauthUriString == null) {
            println("No oauth URI provided")
            setResult(RESULT_CANCELED)
            finish()
        } else {
            val oauthUri = Uri.parse(oauthUriString)
            redirectUri = intent.extras?.getString(REDIRECT_URI).orEmpty()
            if (redirectUri.isEmpty()) {
                println("No redirect URI provided")
                setResult(RESULT_CANCELED)
                finish()
                return
            }

            startActivityForResult(
                Intent(Intent.ACTION_VIEW, oauthUri),
                99
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        println("onNewIntent from OAuth => ${intent.data}")
        val sanitizedIntent = IntentSanitizer.Builder()
            .allowAnyComponent()
            .allowData { it.toString().startsWith(redirectUri) }
            .build()
            .sanitizeByFiltering(intent)

        println("onNewIntent sanitized intent => ${sanitizedIntent.data}")
        setResult(RESULT_OK, sanitizedIntent)
        finish()
    }
}