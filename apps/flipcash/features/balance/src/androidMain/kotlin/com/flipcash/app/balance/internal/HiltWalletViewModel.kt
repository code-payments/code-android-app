package com.flipcash.app.balance.internal

import com.flipcash.app.analytics.FlipcashAnalytics
import com.flipcash.app.funding.PurchaseMethodController
import com.flipcash.app.tokens.TokenCoordinator
import com.flipcash.app.tokens.WalletRevealCoordinator
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.user.UserManager
import com.flipcash.shared.transactionhistory.ActivityFeedCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Hilt entry point for the shared [WalletViewModel]. */
@HiltViewModel
internal class HiltWalletViewModel @Inject constructor(
    userManager: UserManager,
    userFlags: UserFlagsCoordinator,
    dispatchers: DispatcherProvider,
    purchaseMethodController: PurchaseMethodController,
    analytics: FlipcashAnalytics,
    feedCoordinator: ActivityFeedCoordinator,
    tokenCoordinator: TokenCoordinator,
    walletReveal: WalletRevealCoordinator,
) : WalletViewModel(
    userManager = userManager,
    userFlags = userFlags,
    dispatchers = dispatchers,
    purchaseMethodController = purchaseMethodController,
    analytics = analytics,
    feedCoordinator = feedCoordinator,
    tokenCoordinator = tokenCoordinator,
    walletReveal = walletReveal,
)
