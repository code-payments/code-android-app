package com.flipcash.services.user

import com.getcode.crypt.DerivedKey
import com.getcode.opencode.controllers.AccountController
import com.getcode.opencode.events.Events
import com.getcode.opencode.managers.MnemonicManager
import com.getcode.opencode.model.accounts.AccountCluster
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.usdf
import com.getcode.opencode.utils.generate
import com.getcode.solana.keys.PublicKey
import com.hoc081098.channeleventbus.ChannelEventBus
import com.mixpanel.android.mpmetrics.MixpanelAPI
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class UserManagerTest {

    private val mnemonicManager: MnemonicManager = mockk(relaxed = true)
    private val mixpanelAPI: MixpanelAPI = mockk(relaxed = true)
    private val eventBus: ChannelEventBus = mockk(relaxed = true)
    private val accountController: AccountController = mockk(relaxed = true)

    @Before
    fun setUp() {
        mockkObject(DerivedKey)
        every { DerivedKey.derive(any(), any()) } returns mockk(relaxed = true)

        // Token.usdf (extension on MintMetadata.Companion) triggers native Ed25519 code;
        // mock it to avoid JNI errors in JVM tests.
        mockkStatic("com.getcode.opencode.model.financial.MintMetadataKt")
        every { MintMetadata.usdf } returns mockk(relaxed = true)

        // RandomId (in IDKt) triggers PublicKey.generate() → Ed25519 native during
        // class init. Mock the generate function so clear() can reference NoId safely.
        mockkStatic("com.getcode.opencode.utils.PublicKeyKt")
        every { PublicKey.generate() } returns PublicKey(List(32) { 0 })

        mockkObject(AccountCluster)
        every { AccountCluster.newInstance(any(), any()) } returns mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun createUserManager() = UserManager(
        mnemonicManager = mnemonicManager,
        mixpanelAPI = mixpanelAPI,
        eventBus = eventBus,
        accountController = accountController,
    )

    private fun UserManager.withCluster(): UserManager {
        establish("fakeEntropy")
        return this
    }

    @Test
    fun `initial auth state is Unknown`() {
        val um = createUserManager()
        assertEquals(AuthState.Unknown, um.authState)
    }

    @Test
    fun `set Onboarding updates authState`() {
        val um = createUserManager()
        um.set(AuthState.Onboarding(resumePoint = AuthState.ResumePoint.PostAccessKey))
        assertIs<AuthState.Onboarding>(um.authState)
        assertEquals(
            AuthState.ResumePoint.PostAccessKey,
            (um.authState as AuthState.Onboarding).resumePoint
        )
    }

    @Test
    fun `set Ready fires OnLoggedIn when transitioning from non-Ready`() {
        val um = createUserManager().withCluster()

        um.set(AuthState.Ready)

        verify { eventBus.send(match<Events.OnLoggedIn> { true }) }
    }

    @Test
    fun `set Ready does not fire OnLoggedIn when already Ready`() {
        val um = createUserManager().withCluster()
        um.set(AuthState.Ready)
        clearMocks(eventBus, answers = false)

        um.set(AuthState.Ready)

        verify(exactly = 0) { eventBus.send(match<Events.OnLoggedIn> { true }) }
    }

    @Test
    fun `set Ready fires UpdateLimits`() {
        val um = createUserManager().withCluster()

        um.set(AuthState.Ready)

        verify { eventBus.send(match<Events.UpdateLimits> { true }) }
    }

    @Test
    fun `set Authenticating fires UpdateLimits but not OnLoggedIn`() {
        val um = createUserManager().withCluster()

        um.set(AuthState.Authenticating)

        verify { eventBus.send(match<Events.UpdateLimits> { true }) }
        verify(exactly = 0) { eventBus.send(match<Events.OnLoggedIn> { true }) }
    }

    @Test
    fun `OnLoggedIn not fired when accountCluster is null`() {
        val um = createUserManager()

        um.set(AuthState.Ready)

        verify(exactly = 0) { eventBus.send(match<Events.OnLoggedIn> { true }) }
        verify(exactly = 0) { eventBus.send(match<Events.UpdateLimits> { true }) }
    }

    @Test
    fun `clear resets to LoggedOut`() {
        val um = createUserManager()
        um.set(AuthState.Ready)

        um.clear()

        assertEquals(AuthState.LoggedOut, um.authState)
        assertNull(um.accountCluster)
        assertNull(um.entropy)
        assertNull(um.userFlags)
    }
}
