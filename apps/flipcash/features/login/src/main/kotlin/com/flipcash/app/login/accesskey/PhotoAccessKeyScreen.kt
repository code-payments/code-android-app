package com.flipcash.app.login.accesskey

import android.os.Parcelable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
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
import com.getcode.navigation.screens.AppScreen
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
class PhotoAccessKeyScreen : AppScreen, Parcelable {

    @IgnoredOnParcel
    override val key: ScreenKey = uniqueScreenKey

    @IgnoredOnParcel
    override val testTag: String = "access_key_help_screen"

    @Composable
    override fun ScreenContent() {
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
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .aspectRatio(1f),
                    painter = painterResource(R.drawable.ic_access_key_in_photos),
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
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