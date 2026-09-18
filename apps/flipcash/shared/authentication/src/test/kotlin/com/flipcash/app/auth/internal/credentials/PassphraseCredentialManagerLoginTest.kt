package com.flipcash.app.auth.internal.credentials

import com.flipcash.app.auth.internal.accounts.BlockStoreAccountStore
import com.flipcash.app.auth.internal.accounts.BlockStoreBytes
import com.flipcash.libs.coroutines.TestDispatcherProvider
import com.flipcash.services.controllers.AccountController
import com.flipcash.services.user.UserManager
import com.getcode.opencode.model.core.ID
import com.getcode.utils.encodeBase64
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.random.Random
import kotlin.test.assertEquals

/**
 * `fromSelection` is set by every caller that already knows the entropy — the account list, and
 * the deeplink path that borrows the flag to skip the cached-id fast path. Reading it as "this
 * account is already in the list" is what left a deeplink login unlisted, so the store write must
 * not depend on it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PassphraseCredentialManagerLoginTest {

    private class FakeBlockStoreBytes : BlockStoreBytes {
        private var stored: ByteArray = ByteArray(0)
        override suspend fun read(): ByteArray = stored
        override suspend fun write(bytes: ByteArray): Boolean {
            stored = bytes
            return true
        }

        override suspend fun delete() {
            stored = ByteArray(0)
        }
    }

    private val entropy = Random(7).nextBytes(16).encodeBase64()
    private val userId: ID = List(32) { it.toByte() }

    private val accountStore = BlockStoreAccountStore(FakeBlockStoreBytes())
    private val accountController: AccountController = mockk(relaxed = true)
    private val userManager: UserManager = mockk(relaxed = true)

    private fun manager() = PassphraseCredentialManager(
        context = RuntimeEnvironment.getApplication(),
        accountController = accountController,
        userManager = userManager,
        accountStore = accountStore,
        dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher()),
    )

    @Test
    fun `a login records the account whether or not it came from the selection screen`() = runTest {
        coEvery { accountController.login() } returns Result.success(userId)

        manager().login(entropy, fromSelection = true)

        assertEquals(listOf(entropy), accountStore.all().map { it.entropy })
    }
}
