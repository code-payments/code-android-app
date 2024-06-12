package com.getcode

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.painter.BitmapPainter
import com.getcode.analytics.AnalyticsService
import com.getcode.analytics.AnalyticsServiceNull
import com.getcode.network.exchange.Exchange
import com.getcode.network.exchange.ExchangeNull
import com.getcode.network.repository.BetaOptions
import com.getcode.util.CurrencyUtils
import com.getcode.util.DeeplinkHandler
import com.getcode.util.PhoneUtils
import com.getcode.utils.network.NetworkConnectivityListener
import com.getcode.utils.network.NetworkObserverStub
import com.getcode.view.main.home.components.BiometricsState

val LocalAnalytics: ProvidableCompositionLocal<AnalyticsService> = staticCompositionLocalOf { AnalyticsServiceNull() }
val LocalNetworkObserver: ProvidableCompositionLocal<NetworkConnectivityListener> = staticCompositionLocalOf { NetworkObserverStub() }
val LocalPhoneFormatter: ProvidableCompositionLocal<PhoneUtils?> = staticCompositionLocalOf { null }
val LocalCurrencyUtils: ProvidableCompositionLocal<CurrencyUtils?> = staticCompositionLocalOf { null }
val LocalExchange: ProvidableCompositionLocal<Exchange> = staticCompositionLocalOf { ExchangeNull() }
val LocalDeeplinks: ProvidableCompositionLocal<DeeplinkHandler?> = staticCompositionLocalOf { null }
val LocalTopBarPadding: ProvidableCompositionLocal<PaddingValues> = staticCompositionLocalOf { PaddingValues() }
val LocalBetaFlags: ProvidableCompositionLocal<BetaOptions> = staticCompositionLocalOf { BetaOptions.Defaults }
val LocalDownloadQrCode: ProvidableCompositionLocal<BitmapPainter?> = staticCompositionLocalOf { null }
val LocalBiometricsState: ProvidableCompositionLocal<BiometricsState> = staticCompositionLocalOf { BiometricsState() }
