package com.flipcash.app.tokens.ui

import com.flipcash.app.core.MainCoroutineRule
import com.flipcash.app.core.dispatchers.TestDispatchers
import com.flipcash.app.funding.PurchaseMethodController
import com.flipcash.app.shareable.ShareSheetController
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.services.controllers.CommunityController
import com.flipcash.services.models.Community
import com.flipcash.services.models.CommunityLeader
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.ChatId
import com.flipcash.services.models.chat.ChatMetadata
import com.flipcash.services.models.chat.ChatType
import com.flipcash.services.models.chat.RosterSummary
import com.flipcash.shared.transactionhistory.ActivityFeedCoordinator
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.controllers.AccountController
import com.getcode.opencode.exchange.Exchange
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.DataSource
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.Token
import com.getcode.opencode.model.financial.TokenResult
import com.getcode.solana.keys.Mint
import com.getcode.util.resources.FakeResourceHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Spec "Community Leader on Currency Info", view-model cases 1-4, 7 and 8. */
@OptIn(ExperimentalCoroutinesApi::class)
class TokenInfoViewModelCommunityLeaderTest {

    @get:Rule
    var mainCoroutineRule = MainCoroutineRule(UnconfinedTestDispatcher())

    private val tokenCoordinator = mockk<TokenCoordinator>(relaxed = true)
    private val exchange = mockk<Exchange>(relaxed = true)
    private val accountController = mockk<AccountController>(relaxed = true)
    private val communityController = mockk<CommunityController>()

    private val currency = Mint(ByteArray(32) { 2 }.toList())

    private val leader = UserProfile.Empty.copy(
        displayName = "Jeff Yanta",
        username = "jeff",
        userId = List(16) { 7 },
    )

    private val chat = ChatMetadata(
        chatId = ChatId(ByteArray(32)),
        type = ChatType.GROUP,
        members = emptyList(),
        lastMessage = null,
        lastActivity = Instant.fromEpochSeconds(0),
        rosterSummary = RosterSummary(memberCount = 3, version = 1),
        holdingsSummary = null,
    )

    private fun community(leader: UserProfile?, chats: List<ChatMetadata> = listOf(chat)) =
        Result.success(
            Community(
                mint = currency,
                name = "Jeffy",
                leader = leader?.let(::CommunityLeader),
                topHolderChats = chats,
            )
        )

    @Before
    fun setUp() {
        BottomBarManager.clear()
        every { exchange.observePreferredRate() } returns
            MutableStateFlow(Rate(fx = 1.0, currency = CurrencyCode.USD))
        every { accountController.observeHasAccountFor(any()) } returns MutableStateFlow(false)
        every { tokenCoordinator.tokenBalances } returns MutableStateFlow(emptyList())
        every { tokenCoordinator.balanceForToken(any<Mint>()) } returns MutableStateFlow(Fiat.Zero)
        every { tokenCoordinator.appreciationForToken(any()) } returns MutableStateFlow(Fiat.Zero)
        coEvery { tokenCoordinator.getHistoricalMarketCapData(any(), any(), any()) } returns
            Result.success(emptyList())
        listOf(Mint.usdf, currency).forEach { mint ->
            val token = mockk<Token>(relaxed = true) { every { address } returns mint }
            every { tokenCoordinator.cachedToken(mint) } returns token
            coEvery { tokenCoordinator.getTokenMetadata(mint) } returns
                Result.success(TokenResult(token, DataSource.Cache))
        }
    }

    @After
    fun tearDown() {
        BottomBarManager.clear()
    }

    private fun TestScope.viewModel() = TokenInfoViewModel(
        accountController = accountController,
        tokenCoordinator = tokenCoordinator,
        exchange = exchange,
        shareController = mockk<ShareSheetController>(relaxed = true),
        resources = FakeResourceHelper(),
        purchaseMethodController = mockk<PurchaseMethodController>(relaxed = true),
        feedCoordinator = mockk<ActivityFeedCoordinator>(relaxed = true),
        communityController = communityController,
        analytics = com.flipcash.app.analytics.RecordingAnalytics(),
        dispatchers = TestDispatchers(testScheduler),
    )

    private fun TestScope.open(vm: TokenInfoViewModel, mint: Mint) {
        vm.dispatchEvent(TokenInfoViewModel.Event.OnMintProvided(mint))
        advanceUntilIdle()
    }

    @Test
    fun `a leader with a name is shown`() = runTest(mainCoroutineRule.dispatcher) {
        coEvery { communityController.getCommunity(currency) } returns community(leader)
        val vm = viewModel()
        open(vm, currency)

        assertEquals(leader, vm.stateFlow.value.communityLeader)
        assertEquals(1, vm.stateFlow.value.communityChats.size)
        coVerify(exactly = 1) { communityController.getCommunity(currency) }
    }

    @Test
    fun `the leader is shown without any ranked chats`() = runTest(mainCoroutineRule.dispatcher) {
        coEvery { communityController.getCommunity(currency) } returns community(leader, chats = emptyList())
        val vm = viewModel()
        open(vm, currency)

        assertEquals(leader, vm.stateFlow.value.communityLeader)
        assertTrue(vm.stateFlow.value.communityChats.isEmpty())
    }

    @Test
    fun `no leader shows no section`() = runTest(mainCoroutineRule.dispatcher) {
        coEvery { communityController.getCommunity(currency) } returns community(leader = null)
        val vm = viewModel()
        open(vm, currency)

        assertNull(vm.stateFlow.value.communityLeader)
    }

    @Test
    fun `a leader without a profile shows no section`() = runTest(mainCoroutineRule.dispatcher) {
        // The repository maps a leader with no profile to an empty one.
        coEvery { communityController.getCommunity(currency) } returns community(UserProfile.Empty)
        val vm = viewModel()
        open(vm, currency)

        assertNull(vm.stateFlow.value.communityLeader)
    }

    @Test
    fun `a failed call shows no section`() = runTest(mainCoroutineRule.dispatcher) {
        coEvery { communityController.getCommunity(currency) } returns Result.failure(Throwable("boom"))
        val vm = viewModel()
        open(vm, currency)

        assertNull(vm.stateFlow.value.communityLeader)
    }

    @Test
    fun `Dollars shows no section and makes no call`() = runTest(mainCoroutineRule.dispatcher) {
        coEvery { communityController.getCommunity(currency) } returns community(leader)
        val vm = viewModel()
        // Open a currency with a leader first, so a leftover leader would show up here.
        open(vm, currency)
        open(vm, Mint.usdf)

        assertNull(vm.stateFlow.value.communityLeader)
        coVerify(exactly = 0) { communityController.getCommunity(Mint.usdf) }
    }
}
