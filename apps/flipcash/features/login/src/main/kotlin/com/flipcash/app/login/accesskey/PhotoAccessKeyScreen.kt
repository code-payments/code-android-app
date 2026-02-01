package com.flipcash.app.login.accesskey

import android.os.Parcelable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import com.flipcash.app.core.android.extensions.launchPhotos
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.features.login.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class PhotoAccessKeyScreen : Screen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current
        val context = LocalContext.current
        
        AccessKeyInPhotos(
            goBack = { navigator.pop() },
            openPhotos = { context.launchPhotos() },
        )
    }
}

@Composable
private fun AccessKeyInPhotos(
    goBack: () -> Unit,
    openPhotos: () -> Unit,
) {
    CodeScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AppBarWithTitle(
                modifier = Modifier.fillMaxWidth(),
                title = stringResource(R.string.title_cantFindYourAccessKey),
                titleAlignment = Alignment.CenterHorizontally,
                backButton = true,
                onBackIconClicked = goBack,
            )
        },
        bottomBar = {
            CodeButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = CodeTheme.dimens.inset,
                    )
                    .padding(bottom = CodeTheme.dimens.grid.x3)
                    .navigationBarsPadding(),
                text = stringResource(R.string.action_openPhotos),
                buttonState = ButtonState.Filled,
                onClick = openPhotos,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x7),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_access_key_in_photos),
                    contentDescription = null,
                )

                Text(
                    modifier = Modifier.fillMaxWidth(0.5f),
                    text = stringResource(R.string.description_cantFindYourAccessKey),
                    style = CodeTheme.typography.textMedium,
                    textAlign = TextAlign.Center,
                    color = CodeTheme.colors.textSecondary,
                )
            }
        }
    }
}

@Preview
@Composable
private fun PreviewAccessKeyHelp() {
    FlipcashPreview {
        AccessKeyInPhotos(
            goBack = {},
            openPhotos = {},
        )
    }
}