package com.flipcash.app.tipping.internal

import com.flipcash.app.core.AppRoute
import com.flipcash.app.tipping.internal.NewChatViewModel.Choice
import com.flipcash.app.tipping.internal.NewChatViewModel.Outcome
import com.getcode.opencode.model.financial.Fiat
import com.getcode.opencode.model.financial.HolderMetrics
import com.getcode.opencode.model.financial.MintMetadata
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.TokenWithBalance
import com.getcode.opencode.model.financial.VmMetadata
import com.getcode.solana.keys.Mint
import com.getcode.solana.keys.PublicKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

/** Where each New Chat row leads, and the community row's gate in each of its three states. */
@OptIn(ExperimentalCoroutinesApi::class)
class NewChatViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun token(address: Mint, symbol: String) = MintMetadata(
        address = address,
        decimals = 6,
        name = symbol,
        symbol = symbol,
        createdAt = null,
        description = "",
        imageUrl = "",
        vmMetadata = VmMetadata(
            vm = PublicKey.fromBase58("11111111111111111111111111111111"),
            authority = PublicKey.fromBase58("11111111111111111111111111111111"),
            lockDurationInDays = 21,
        ),
        launchpadMetadata = null,
        billCustomizations = null,
        socialLinks = emptyList(),
        holderMetrics = HolderMetrics.None,
    )

    private val dollars = TokenWithBalance(token(Mint.usdf, "USDF"), Fiat(25.0))
    private val community = TokenWithBalance(token(Mint(ByteArray(32) { 1 }.toList()), "BAD"), Fiat(5.0))

    private fun viewModel(balances: Flow<List<TokenWithBalance>>) =
        NewChatViewModel(tokenBalances = balances, preferredRate = flowOf(Rate.oneToOne))

    @Test
    fun `dollars alone is no community currency`() {
        assertEquals(Outcome.NoCommunityCurrencies, viewModel(flowOf(listOf(dollars))).choose(Choice.CommunityGroup))
    }

    @Test
    fun `a community currency opens the community form`() {
        assertEquals(
            Outcome.Open(AppRoute.Messaging.NewGroup()),
            viewModel(flowOf(listOf(dollars, community))).choose(Choice.CommunityGroup),
        )
    }

    @Test
    fun `an unknown gate opens the form rather than the sheet`() {
        assertEquals(
            Outcome.Open(AppRoute.Messaging.NewGroup()),
            viewModel(MutableSharedFlow()).choose(Choice.CommunityGroup),
        )
    }

    @Test
    fun `the other rows open their screens ungated`() {
        val vm = viewModel(flowOf(listOf(dollars)))
        assertEquals(Outcome.Open(AppRoute.Messaging.FindByUsername), vm.choose(Choice.FindByUsername))
        assertEquals(Outcome.Open(AppRoute.Messaging.NewGroup(isPrivate = true)), vm.choose(Choice.PrivateGroup))
    }
}
