package com.flipcash.app.purchase.internal

import android.Manifest
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.registry.ScreenRegistry
import com.flipcash.app.core.NavScreenProvider
import com.flipcash.features.purchase.R
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import com.getcode.util.getActivity
import com.getcode.util.permissions.LocalPermissionChecker
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

@Composable
internal fun PurchaseAccountScreenContent(viewModel: PurchaseAccountViewModel) {
    val navigator = LocalCodeNavigator.current
    val context = LocalContext.current
    val permissions = LocalPermissionChecker.current

    val state by viewModel.stateFlow.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<PurchaseAccountViewModel.Event.OnAccountCreated>()
            .onEach {
                when {
                    permissions.isDenied(Manifest.permission.POST_NOTIFICATIONS) -> {
                        navigator.push(ScreenRegistry.get(NavScreenProvider.Permissions.Notification(true)))
                    }

                    permissions.isDenied(Manifest.permission.CAMERA) -> {
                        navigator.push(ScreenRegistry.get(NavScreenProvider.Permissions.Camera(true)))
                    }

                    else -> {
                        navigator.replaceAll(ScreenRegistry.get(NavScreenProvider.HomeScreen.Scanner()))
                    }
                }
            }.launchIn(this)
    }

    CodeScaffold(
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .padding(bottom = CodeTheme.dimens.grid.x2)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1)
            ) {
                CodeButton(
                    modifier = Modifier
                        .fillMaxWidth(),
                    buttonState = ButtonState.Filled,
                    isLoading = state.creatingAccount.loading,
                    isSuccess = state.creatingAccount.success,
                    text = stringResource(R.string.action_purchaseAccount),
                ) {
                    context.getActivity()?.let { activity ->
                        viewModel.dispatchEvent(PurchaseAccountViewModel.Event.BuyAccount(activity))
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .align(Alignment.Center),
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Image(
                    modifier = Modifier
                        .size(120.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colorStops = arrayOf(
                                    0.43f to Color(0xFF0B2D17),
                                    0.67f to Color(0xFF053209),
                                ),
                            ),
                            shape = CircleShape
                        )
                        .drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color(42,92,34),
                                        Color(0x00FFFFFF),
                                    ),
                                    center = Offset(size.width * 0.25f, size.height * 0.25f),
                                    radius = size.width * 0.5f,
                                ),
                                radius = size.width / 2,
                            )
                        }
                        .padding(CodeTheme.dimens.grid.x6),
                    imageVector = Icons.Default.CheckCircleOutline,
                    colorFilter = ColorFilter.tint(Color.White),
                    contentDescription = null,
                )

                Text(
                    modifier = Modifier.padding(top = CodeTheme.dimens.grid.x10),
                    text = state.title,
                    style = CodeTheme.typography.textLarge,
                    textAlign = TextAlign.Center,
                    color = CodeTheme.colors.textMain
                )

                Text(
                    text = state.subtitle,
                    style = CodeTheme.typography.textMedium,
                    textAlign = TextAlign.Center,
                    color = CodeTheme.colors.textSecondary
                )
            }
        }
    }
}