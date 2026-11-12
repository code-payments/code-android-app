package com.flipcash.app.internal.ui.navigation

import com.flipcash.app.core.navigation.NavBarShell
import com.flipcash.app.featureflags.FeatureFlag
import com.flipcash.app.featureflags.FeatureFlagController
import com.flipcash.services.user.UserManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The shell this launch should open with, read from the stored values the live
 * `HomeViewModel.navBarShell` resolves once its flows load: the flag and the beta override from the
 * beta-flags store, and the staff bit from the user flags cached on the last launch, which the soft
 * login restores before [com.flipcash.services.user.AuthState.Ready]. The staff bit has no local
 * override, so the cached server value is the one the live gate uses.
 */
internal suspend fun FeatureFlagController.launchShell(userManager: UserManager): NavBarShell =
    NavBarShell.resolve(
        groupMoneyUi = get(FeatureFlag.GroupMoneyUi),
        betaOverride = getOverride(),
        isStaff = userManager.state.value.flags?.isStaff == true,
    )

/** How long a launch waits for the composed shell before it routes anyway. */
internal val LaunchShellTimeout: Duration = 1.seconds

/**
 * Suspends until [composed] gives [expected], so the first tab route never draws under the other
 * shell: the shell's flows start on Classic and catch up after the stores load. Gives up after
 * [timeout], because the live gate can disagree with the stored values and a launch must not hang.
 */
internal suspend fun awaitLaunchShell(
    expected: NavBarShell,
    composed: Flow<NavBarShell>,
    timeout: Duration = LaunchShellTimeout,
) {
    withTimeoutOrNull(timeout) { composed.first { it == expected } }
}
