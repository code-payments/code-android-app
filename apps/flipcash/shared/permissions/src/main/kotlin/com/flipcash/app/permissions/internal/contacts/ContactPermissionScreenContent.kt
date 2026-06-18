package com.flipcash.app.permissions.internal.contacts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.flipcash.app.analytics.StubFlipcashAnalytics
import com.flipcash.app.permissions.ContactAccessHandle
import com.flipcash.app.permissions.asContactAccessHandle
import com.flipcash.app.permissions.internal.contacts.components.AnimatedContactListPreview
import com.flipcash.app.permissions.internal.contacts.components.ContactPermissionBottomBar
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.shared.permissions.R
import com.getcode.libs.analytics.LocalAnalytics
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.CodeScaffold
import com.getcode.util.permissions.ProvideTestPermissions
import com.getcode.util.permissions.rememberContactPermission

@Composable
fun ContactScreenContent(
    accessHandle: ContactAccessHandle,
    onSkip: (() -> Unit)? = null,
    isLoading: Boolean = false,
    isSuccess: Boolean = false,
) {
    CodeScaffold(
        bottomBar = {
            ContactPermissionBottomBar(
                accessHandle = accessHandle,
                onSkip = onSkip,
                isLoading = isLoading,
                isSuccess = isSuccess,
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedContactListPreview(animate = false)

                Text(
                    text = stringResource(R.string.permissions_title_contacts),
                    style = CodeTheme.typography.displaySmall,
                    color = CodeTheme.colors.textMain,
                )
                Text(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .padding(top = CodeTheme.dimens.grid.x2)
                        .padding(horizontal = CodeTheme.dimens.inset),
                    text = stringResource(R.string.permissions_description_contacts),
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
private fun PreviewContactPermissionScreen() {
    CompositionLocalProvider(LocalAnalytics provides StubFlipcashAnalytics()) {
        ProvideTestPermissions(granted = emptySet()) {
            val state = rememberContactPermission()
            ContactScreenContent(state.asContactAccessHandle(), onSkip = { })
        }
    }
}