package com.flipcash.app.funding.internal

import com.flipcash.app.core.money.formatted
import com.flipcash.app.funding.PurchaseMethod
import com.flipcash.app.funding.PurchaseMethodMetadata
import com.flipcash.app.funding.PurchaseMethodState
import com.flipcash.app.funding.bottomBarCardAction
import com.flipcash.shared.funding.R
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.model.financial.Fiat
import com.getcode.util.resources.ResourceHelper

/**
 * The "Add Dollars With" method rows — each a card (title + subtitle + trailing icon) rendered via the
 * [BottomBarAction.content] slot.
 */
internal fun purchaseOptions(
    state: PurchaseMethodState,
    metadata: PurchaseMethodMetadata,
    resources: ResourceHelper,
    onClick: (PurchaseMethod) -> Unit,
): List<BottomBarAction> = buildList {
    if (state.coinbaseOnRampAvailable) {
        add(
            bottomBarCardAction(
                title = resources.getString(R.string.label_debitCard),
                subtitle = resources.getString(R.string.subtitle_depositDebitCard),
                iconRes = R.drawable.ic_google_pay_brand_mark,
                tintIcon = false,
                testTag = "purchase_method_coinbase",
                onClick = { onClick(PurchaseMethod.CoinbaseOnRamp) },
            )
        )
    }
    if (state.hasReserves && metadata.showReserves) {
        val minimumAmountNeeded = metadata.purchaseAmount ?: Fiat.MIN_VALUE
        if (state.reservesBalance.nativeAmount >= minimumAmountNeeded) {
            add(
                bottomBarCardAction(
                    title = resources.getString(R.string.label_cashReserves),
                    subtitle = state.reservesBalance.formatted(),
                    iconRes = R.drawable.ic_wallet,
                    testTag = "purchase_method_reserves",
                    onClick = { onClick(PurchaseMethod.CashReserves(state.reservesBalance)) },
                )
            )
        }
    }
    add(
        bottomBarCardAction(
            title = resources.getString(R.string.label_phantom),
            subtitle = resources.getString(R.string.subtitle_depositPhantom),
            iconRes = R.drawable.ic_phantom_wallet,
            testTag = "purchase_method_phantom",
            onClick = { onClick(PurchaseMethod.PhantomWallet) },
        )
    )
    if (state.canUseOtherWallets) {
        add(
            bottomBarCardAction(
                title = resources.getString(R.string.title_onrampProviderOtherWallet),
                subtitle = resources.getString(R.string.subtitle_depositOtherWallet),
                iconRes = R.drawable.ic_qr_code,
                testTag = "purchase_method_other_wallet",
                onClick = { onClick(PurchaseMethod.OtherWallet) },
            )
        )
    }
    add(
        BottomBarAction(
            text = resources.getString(R.string.action_dismiss),
            style = BottomBarManager.BottomBarButtonStyle.Text,
        )
    )
}
