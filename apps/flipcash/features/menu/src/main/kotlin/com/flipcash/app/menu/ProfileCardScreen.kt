package com.flipcash.app.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.flipcash.app.bills.ScannableRenderer
import com.flipcash.app.bills.components.cards.LocalTipCardBaseAlpha
import com.flipcash.app.bills.components.cards.LocalTipCardColor
import com.flipcash.app.bills.components.cards.TipCardFlattened
import com.flipcash.app.menu.internal.ProfileCardViewModel
import com.flipcash.features.menu.R
import com.flipcash.shared.common.ui.profile.ProfileActionButton
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle

/**
 * The viewer's own profile card, full screen, behind `AppRoute.Menu.ProfileCard`. Download hands
 * the card to the system share sheet as an image.
 */
@Composable
fun ProfileCardScreen() {
    val viewModel = hiltViewModel<ProfileCardViewModel>()
    val navigator = LocalCodeNavigator.current
    val card = viewModel.card

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CodeTheme.colors.background),
    ) {
        AppBarWithTitle(
            modifier = Modifier.statusBarsPadding(),
            endContent = { AppBarDefaults.Close(onClick = { navigator.pop() }) },
        )

        if (card != null) {
            // Static backdrop, so draw the card opaque at its flattened tone, as the You tab did.
            CompositionLocalProvider(
                LocalTipCardColor provides TipCardFlattened,
                LocalTipCardBaseAlpha provides 1f,
            ) {
                ScannableRenderer(
                    modifier = Modifier.align(Alignment.Center),
                    scannable = card,
                    tipCardWidth = CodeTheme.dimens.screenWidth * CardWidthFraction,
                )
            }
        }

        ProfileActionButton(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = CodeTheme.dimens.grid.x4),
            text = stringResource(R.string.action_download),
            onClick = viewModel::download,
        )
    }
}

/** How much of the display the card spans, the proportion the earlier full-screen card used. */
private const val CardWidthFraction = 0.75f
