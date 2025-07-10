package com.flipcash.app.balance.internal.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.flipcash.app.balance.internal.BalanceViewModel
import com.flipcash.app.core.feed.ActivityFeedMessage
import com.flipcash.app.core.feed.MessageMetadata
import com.flipcash.app.core.feed.MessageState
import com.flipcash.app.theme.FlipcashDesignSystem
import com.getcode.ed25519.Ed25519
import com.getcode.opencode.compose.ExchangeStub
import com.getcode.opencode.compose.LocalExchange
import com.getcode.opencode.model.financial.CurrencyCode
import com.getcode.opencode.model.financial.LocalFiat
import com.getcode.opencode.model.financial.Rate
import com.getcode.opencode.model.financial.toFiat
import com.getcode.solana.keys.PublicKey
import com.getcode.theme.CodeTheme
import com.getcode.utils.decodeBase58
import kotlinx.datetime.Instant

@Composable
internal fun FeedItem(
    message: ActivityFeedMessage,
    canViewDetails: Boolean,
    isExpanded: Boolean,
    modifier: Modifier = Modifier,
    dispatch: (BalanceViewModel.Event) -> Unit,
) {
    val elevation by animateDpAsState(if (isExpanded) 8.dp else 0.dp)

    Column(
        modifier = modifier,
    ) {
        Surface(
            color = CodeTheme.colors.background,
            elevation = elevation,
        ) {
            FeedItemSummary(
                message = message,
                canViewDetails = canViewDetails,
                modifier = Modifier
                    .fillMaxWidth(),
                dispatch = dispatch,
            )
        }

        AnimatedContent(
            targetState = isExpanded,
            modifier = Modifier.fillMaxWidth(),
            transitionSpec = {
                slideInVertically { -it } togetherWith slideOutVertically { -it }
            }
        ) { expanded ->
            if (expanded) {
                FeedItemDetails(
                    message = message,
                    modifier = Modifier.fillMaxWidth(),
                    dispatch = dispatch
                )
            } else {
                Spacer(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}


private val cadUsdRate = Rate(fx = 1.371881, currency = CurrencyCode.CAD)
private val usdCadRate = Rate(fx = 1.0 / 1.371881, currency = CurrencyCode.CAD)
private val oneDollarCad = 1.00.toFiat(CurrencyCode.CAD)

val rates = mapOf(
    CurrencyCode.CAD to cadUsdRate,
    CurrencyCode.USD to usdCadRate
)
private val oneDollarLocalized = LocalFiat(
    usdc = oneDollarCad.convertingTo(usdCadRate),
    converted = oneDollarCad,
)
private val sampleItem = ActivityFeedMessage(
    id = "3GHjGey5F3fVProC3mYpiBpi7dCegFNz3wYtHSTiQnPt".decodeBase58().toList(),
    text = "Gave",
    amount = oneDollarLocalized,
    timestamp = Instant.parse("2025-06-03T16:25:00-04:00"),
    state = MessageState.COMPLETED,
    metadata = MessageMetadata.GaveUsdc
)

@Preview
@Composable
private fun Preview_CollapsedItem() {
    FlipcashDesignSystem {
        CompositionLocalProvider(
            LocalExchange provides ExchangeStub(
                providedRates = rates,
                context = LocalContext.current
            )
        ) {
            Box(modifier = Modifier.background(CodeTheme.colors.background)) {
                FeedItem(
                    message = sampleItem,
                    isExpanded = false,
                    canViewDetails = true,
                ) { }
            }
        }
    }
}

@Preview
@Composable
private fun Preview_ExpandedItem() {
    FlipcashDesignSystem {
        CompositionLocalProvider(
            LocalExchange provides ExchangeStub(
                providedRates = rates,
                context = LocalContext.current
            )
        ) {
            Box(modifier = Modifier.background(CodeTheme.colors.background)) {
                FeedItem(
                    message = sampleItem,
                    isExpanded = true,
                    canViewDetails = true,
                ) { }
            }
        }
    }
}