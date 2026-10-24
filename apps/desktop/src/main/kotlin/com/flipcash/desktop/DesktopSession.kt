package com.flipcash.desktop

import com.flipcash.app.devicelink.SeedVault
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.init
import com.flipcash.services.controllers.AccountController
import com.flipcash.services.user.AuthState
import com.flipcash.services.user.UserManager
import com.getcode.crypt.MnemonicPhrase
import java.io.File

/**
 * Signs the desktop in from a seed: the one a phone linked into the [SeedVault], or, for development,
 * a recovery phrase in `FLIPCASH_DEV_MNEMONIC` (set by Gradle from `flipcash.test.mnemonic` in
 * local.properties, only when present). The dev phrase is never written to the vault or logged.
 *
 * Kept in the desktop app: [FlipcashDatabase.init] takes a [File], and the app module is JVM-only.
 */
internal object DesktopSession {
    private const val VARIABLE = "FLIPCASH_DEV_MNEMONIC"

    /** The dev phrase's entropy, or null when none is configured. */
    fun devEntropy(): String? {
        val words = System.getenv(VARIABLE)?.trim()?.split(Regex("\\s+"))?.takeIf { it.size > 1 } ?: return null
        return MnemonicPhrase.newInstance(words)?.getBase64EncodedEntropy()
            ?: run { println("dev session: $VARIABLE is not a 12 or 24 word phrase"); null }
    }

    /** The same steps `AuthManager.login` walks for an account that has finished onboarding. */
    suspend fun signIn(
        entropyB64: String,
        userManager: UserManager,
        accountController: AccountController,
        dataDir: File,
    ): Result<Unit> {
        userManager.establish(entropyB64)
        return accountController.login().map { accountId ->
            FlipcashDatabase.init(dataDir, entropyB64)
            userManager.set(accountId = accountId)
            userManager.set(AuthState.Ready)
        }
    }
}

internal suspend fun DesktopGraph.signIn(entropyB64: String): Result<Unit> =
    DesktopSession.signIn(entropyB64, userManager, accountController, DesktopModule.appDir)

/**
 * Signs in at launch with the dev phrase if one is configured, else with the seed a phone linked
 * earlier. Returns false when neither is there, and the app shows the link screen. Run [startup] first.
 */
internal suspend fun DesktopGraph.restoreSession(): Boolean {
    DesktopSession.devEntropy()?.let { entropy ->
        return signIn(entropy)
            .onSuccess { println("session: signed in (dev phrase)") }
            .onFailure { println("session: dev sign-in failed (${it.message})") }
            .isSuccess
    }
    val stored = seedVault.read() ?: return false.also { println("session: not linked") }
    return signIn(stored)
        .onSuccess { println("session: signed in (linked seed)") }
        .onFailure { println("session: linked sign-in failed (${it.message})") }
        .isSuccess
}
