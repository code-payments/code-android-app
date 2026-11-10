package com.flipcash.app.puppet.newchat

import com.flipcash.app.puppet.HostedRoutes
import com.flipcash.app.tipping.internal.NewChatViewModel
import com.flipcash.app.tipping.internal.NewChatViewModel.Choice
import com.flipcash.app.tipping.internal.NewChatViewModel.Outcome
import com.flipcash.libs.puppet.PuppetAction
import com.flipcash.libs.puppet.PuppetNode
import com.flipcash.libs.puppet.PuppetStack
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject

/**
 * The New Chat chooser as a Puppet node, pushed when Chats opens it. Each row is an action that
 * does what the screen does with [NewChatViewModel.choose]'s outcome: push the route, or present
 * the "No Community Currencies" sheet. The sheet shows as a node above New Chat, so a file can see
 * it and act on it; neither screen has state of its own a file asserts on.
 */
internal fun newChatNode(viewModel: NewChatViewModel, stack: PuppetStack, routes: HostedRoutes): PuppetNode {
    fun choose(choice: Choice) = when (val outcome = viewModel.choose(choice)) {
        is Outcome.Open -> routes.push(outcome.route)
        Outcome.NoCommunityCurrencies -> stack.push(noCommunityCurrenciesNode(stack, routes))
    }
    return PuppetNode(
        name = "NewChat",
        state = { buildJsonObject {} },
        actions = listOf(
            PuppetAction(FindByUsernameOpened.serializer()) { choose(Choice.FindByUsername) },
            PuppetAction(PrivateGroupOpened.serializer()) { choose(Choice.PrivateGroup) },
            PuppetAction(CommunityGroupOpened.serializer()) { choose(Choice.CommunityGroup) },
        ),
    )
}

/** Node 12370:77242. Explore closes the sheet and pushes the leaderboard onto New Chat's stack. */
private fun noCommunityCurrenciesNode(stack: PuppetStack, routes: HostedRoutes) = PuppetNode(
    name = "NoCommunityCurrencies",
    state = { buildJsonObject {} },
    actions = listOf(
        PuppetAction(ExploreCurrenciesOpened.serializer()) {
            stack.pop()
            routes.push(Outcome.NoCommunityCurrencies.explore)
        },
    ),
)

@Serializable @SerialName("FindByUsernameOpened") data object FindByUsernameOpened
@Serializable @SerialName("PrivateGroupOpened") data object PrivateGroupOpened
@Serializable @SerialName("CommunityGroupOpened") data object CommunityGroupOpened
@Serializable @SerialName("ExploreCurrenciesOpened") data object ExploreCurrenciesOpened
