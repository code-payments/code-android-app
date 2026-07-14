package com.flipcash.app.permissions.internal.contacts

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.res.painterResource
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
    simplified: Boolean = false,
    onSkip: (() -> Unit)? = null,
    isLoading: Boolean = false,
    isSuccess: Boolean = false,
) {
    if (simplified) {
        SimplifiedContactScreenContent(
            accessHandle = accessHandle,
            onSkip = onSkip,
            isLoading = isLoading,
            isSuccess = isSuccess,
        )
        return
    }

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
                        .fillMaxWidth(0.6f)
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
private fun SimplifiedContactScreenContent(
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
                action = stringResource(R.string.action_continue),
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x6),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_send_large),
                    contentDescription = null,
                )
                Column(
                    modifier = Modifier.fillMaxWidth(0.8f),
                    verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.permissions_title_contactsSimple),
                        style = CodeTheme.typography.textLarge,
                        color = CodeTheme.colors.textMain,
                    )
                    Text(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.permissions_description_contactsSimple),
                        style = CodeTheme.typography.textSmall,
                        color = CodeTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                    horizontalAlignment = Alignment.CenterHorizontally,

                    ) {
                    BulletRow(
                        painter = painterResource(com.flipcash.core.R.drawable.ic_checklist),
                        text = stringResource(com.flipcash.core.R.string.rationale_bullet_contactChoice),
                        textColor = CodeTheme.colors.textMain,
                    )
                    BulletRow(
                        painter = painterResource(com.flipcash.core.R.drawable.ic_lock),
                        text = stringResource(com.flipcash.core.R.string.rationale_bullet_secure),
                        textColor = CodeTheme.colors.textMain,
                    )
                    BulletRow(
                        painter = painterResource(com.flipcash.core.R.drawable.ic_people_gear),
                        text = stringResource(com.flipcash.core.R.string.rationale_bullet_changeAccess),
                        textColor = CodeTheme.colors.textMain,
                    )
                }
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

@Composable
@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
private fun PreviewSimplifiedContactPermissionScreen() {
    CompositionLocalProvider(LocalAnalytics provides StubFlipcashAnalytics()) {
        ProvideTestPermissions(granted = emptySet()) {
            val state = rememberContactPermission()
            ContactScreenContent(state.asContactAccessHandle(), simplified = true)
        }
    }
}