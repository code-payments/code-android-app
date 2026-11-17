package com.flipcash.app.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.getcode.theme.CodeTheme

/**
 * The left-aligned headline the new-account steps share when the Group Money gate is open
 * (phone, code, email, name): a title, a line under it, then the step's own fields.
 */
@Composable
fun OnboardingStepLayout(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CodeTheme.dimens.inset)
            .padding(top = CodeTheme.dimens.grid.x3),
    ) {
        OnboardingHeadline(title = title, subtitle = subtitle)
        Spacer(Modifier.height(CodeTheme.dimens.grid.x6))
        Column(verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2)) {
            content()
        }
    }
}

/** The title and the line under it, for a step that lays out its own fields. */
@Composable
fun OnboardingHeadline(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = CodeTheme.typography.displayCompact,
            color = CodeTheme.colors.textMain,
        )
        Spacer(Modifier.height(CodeTheme.dimens.grid.x4))
        Text(
            text = subtitle,
            style = CodeTheme.typography.textMedium,
            color = CodeTheme.colors.textSecondary,
        )
    }
}

/** The label above a field in [OnboardingStepLayout]. */
@Composable
fun OnboardingFieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        modifier = modifier,
        text = text,
        style = CodeTheme.typography.textSmall,
        color = CodeTheme.colors.textSecondary,
    )
}

/** The note under a field in [OnboardingStepLayout]. */
@Composable
fun OnboardingFootnote(text: String, modifier: Modifier = Modifier) {
    Text(
        modifier = modifier.padding(top = CodeTheme.dimens.grid.x3),
        text = text,
        style = CodeTheme.typography.textSmall,
        color = CodeTheme.colors.textSecondary,
    )
}
