package com.flipcash.app.core.internal.ui.previews

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import com.flipcash.app.core.ui.Callout
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.core.R
import com.getcode.theme.CodeTheme
import com.getcode.theme.extraLarge

@Composable
private fun WarningIcon() {
    Icon(
        painter = painterResource(R.drawable.ic_callout_warning),
        contentDescription = null,
        tint = CodeTheme.colors.warning,
        modifier = Modifier.size(CodeTheme.dimens.staticGrid.x10),
    )
}

@Composable
private fun LockIcon() {
    Icon(
        painter = painterResource(R.drawable.ic_lock),
        contentDescription = null,
        tint = CodeTheme.colors.textSecondary,
        modifier = Modifier.size(CodeTheme.dimens.staticGrid.x10),
    )
}

/** Shaped card — icon + dismiss + action (the contact-access prompt). */
@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun CalloutShapedFullPreview() {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .background(CodeTheme.colors.background),
    ) {
        Callout(
            title = "Allow Full Contact Access",
            description = "Make sure you can send cash and identify people you know",
            shape = CodeTheme.shapes.medium,
            icon = { WarningIcon() },
            actionLabel = "Settings",
            onDismiss = {},
            onAction = {},
        )
    }
}

/** Shaped card — informational, no dismiss, no action. */
@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun CalloutInfoOnlyPreview() {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .background(CodeTheme.colors.background),
    ) {
        Callout(
            title = "Payments are private",
            description = "Only you and the recipient can see this transaction",
            shape = CodeTheme.shapes.medium,
            icon = { LockIcon() },
        )
    }
}

/** Shaped card — action only, no icon, no dismiss. */
@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun CalloutActionOnlyPreview() {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .background(CodeTheme.colors.background),
    ) {
        Callout(
            title = "Verify your email",
            description = "Secure your account and enable recovery",
            shape = CodeTheme.shapes.medium,
            actionLabel = "Verify",
            onAction = {},
        )
    }
}

/** Custom container color + shape, dismiss only. */
@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun CalloutErrorBannerPreview() {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .background(CodeTheme.colors.background),
    ) {
        Callout(
            title = "You're offline",
            description = "Reconnecting…",
            shape = CodeTheme.shapes.extraLarge,
            containerColor = CodeTheme.colors.error,
            icon = { WarningIcon() },
            onDismiss = {},
        )
    }
}

/** Bare overload — no `shape`; the caller owns the surface. */
@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun CalloutBareInCallerSurfacePreview() {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .background(CodeTheme.colors.background),
    ) {
        Surface(
            color = CodeTheme.colors.surfaceVariant,
            contentColor = CodeTheme.colors.textMain,
            shape = CodeTheme.shapes.extraLarge,
        ) {
            Callout(
                title = "Bring your own container",
                description = "No shape passed, so the caller's Surface drives the look",
                modifier = Modifier.padding(CodeTheme.dimens.grid.x4),
                icon = { LockIcon() },
                actionLabel = "Got it",
                onDismiss = {},
                onAction = {},
            )
        }
    }
}

/** Full-bleed banner — square corners via RectangleShape. */
@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun CalloutFullBleedBannerPreview() {
    Column(
        modifier = Modifier.background(CodeTheme.colors.background),
    ) {
        Callout(
            title = "Update available",
            description = "Tap to install the latest version",
            shape = RectangleShape,
            icon = { WarningIcon() },
            actionLabel = "Update",
            onAction = {},
        )
    }
}
