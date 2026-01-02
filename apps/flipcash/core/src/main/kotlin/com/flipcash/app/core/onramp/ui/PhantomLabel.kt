package com.flipcash.app.core.onramp.ui

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import com.flipcash.services.internal.model.thirdparty.OnRampProvider
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.getButtonColors

typealias AnnotatedButtonLabel = Pair<AnnotatedString, Map<String, InlineTextContent>>

@Composable
fun buildPhantomButtonLabel(
    prefix: String,
    iconColor: Color = getButtonColors(
        true,
        ButtonState.Filled
    ).contentColor(true).value
): AnnotatedButtonLabel {
    return buildExternalWalletButtonLabel(prefix, OnRampProvider.Phantom, iconColor)
}