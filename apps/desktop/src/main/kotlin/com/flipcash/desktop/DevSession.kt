package com.flipcash.desktop

import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.app.persistence.init
import com.flipcash.services.controllers.AccountController
import com.flipcash.services.user.AuthState
import com.flipcash.services.user.UserManager
import com.getcode.crypt.MnemonicPhrase
import java.io.File

/**
 * Development-only sign-in until the link-from-phone flow lands: reads a recovery phrase from the
 * `FLIPCASH_DEV_MNEMONIC` environment variable (set by Gradle from `flipcash.test.mnemonic` in
 * local.properties, only when present) and walks the same steps `AuthManager.login` does for an
 * account that has finished onboarding. The phrase is never logged.
 */
internal object DevSession {
    private const val VARIABLE = "FLIPCASH_DEV_MNEMONIC"

    suspend fun signIn(
        userManager: UserManager,
        accountController: AccountController,
        dataDir: File,
    ): Result<Unit> {
        val words = System.getenv(VARIABLE)?.trim()?.split(Regex("\\s+"))
            ?.takeIf { it.size > 1 }
            ?: return Result.failure(IllegalStateException("$VARIABLE not set"))
        val entropy = MnemonicPhrase.newInstance(words)?.getBase64EncodedEntropy()
            ?: return Result.failure(IllegalArgumentException("$VARIABLE is not a 12 or 24 word phrase"))

        userManager.establish(entropy)
        return accountController.login().map { accountId ->
            FlipcashDatabase.init(dataDir, entropy)
            userManager.set(accountId = accountId)
            userManager.set(AuthState.Ready)
        }
    }
}

/** Signs in with the dev phrase when one is configured; otherwise the wallet stays signed out. Run [startup] first. */
internal suspend fun DesktopGraph.devSignIn() {
    DevSession.signIn(userManager, accountController, DesktopModule.appDir)
        .onSuccess { println("dev session: signed in") }
        .onFailure { println("dev session: not signed in (${it.message})") }
}
