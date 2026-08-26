package com.flipcash.app.funding.internal

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.money.formatted
import com.flipcash.app.funding.PurchaseMethod
import com.flipcash.app.funding.PurchaseMethodMetadata
import com.flipcash.app.funding.PurchaseMethodState
import com.flipcash.shared.funding.R
import com.getcode.manager.BottomBarAction
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.model.financial.Fiat
import com.getcode.theme.CodeTheme
import com.getcode.util.resources.ResourceHelper

/**
 * The "Add Money With" method rows — each a card (title + subtitle + trailing icon) rendered via the
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
            cardAction(
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
                cardAction(
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
        cardAction(
            title = resources.getString(R.string.label_phantom),
            subtitle = resources.getString(R.string.subtitle_depositPhantom),
            iconRes = R.drawable.ic_phantom_wallet,
            testTag = "purchase_method_phantom",
            onClick = { onClick(PurchaseMethod.PhantomWallet) },
        )
    )
    if (state.canUseOtherWallets) {
        add(
            cardAction(
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

private fun cardAction(
    title: String,
    subtitle: String,
    iconRes: Int,
    tintIcon: Boolean = true,
    testTag: String? = null,
    onClick: () -> Unit,
): BottomBarAction = BottomBarAction(
    text = AnnotatedString(title),
    inlineContentMap = emptyMap(),
    testTag = testTag,
    onClick = onClick,
    content = {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = CodeTheme.typography.textMedium,
                color = Color.White,
            )
            Text(
                text = subtitle,
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
            )
        }
        if (tintIcon) {
            // Monochrome glyph (phantom, QR, reserves) — tint white and size square.
            Icon(
                modifier = Modifier.size(32.dp),
                painter = painterResource(iconRes),
                tint = Color.White,
                contentDescription = null,
            )
        } else {
            // Brand mark (Google Pay) — keep its own colours; constrain height, let width wrap.
            Image(
                modifier = Modifier.height(28.dp),
                painter = painterResource(iconRes),
                contentDescription = null,
            )
        }
    },
)
