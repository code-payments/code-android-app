package com.getcode

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.painter.BitmapPainter
import com.getcode.network.repository.BetaOptions
import com.getcode.ui.biometrics.BiometricsState
import com.getcode.util.DeeplinkHandler
import com.getcode.util.PhoneUtils

val LocalSession: ProvidableCompositionLocal<SessionController?> = staticCompositionLocalOf { null }
val LocalPhoneFormatter: ProvidableCompositionLocal<PhoneUtils?> = staticCompositionLocalOf { null }
val LocalDeeplinks: ProvidableCompositionLocal<DeeplinkHandler?> = staticCompositionLocalOf { null }
val LocalBetaFlags: ProvidableCompositionLocal<BetaOptions> = staticCompositionLocalOf { BetaOptions.Defaults }
val LocalDownloadQrCode: ProvidableCompositionLocal<BitmapPainter?> = staticCompositionLocalOf { null }
val LocalBiometricsState: ProvidableCompositionLocal<BiometricsState> = staticCompositionLocalOf { BiometricsState() }
