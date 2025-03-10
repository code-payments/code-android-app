package xyz.flipchat.app.features.profile

import android.app.Activity
import android.content.Context
import android.os.Parcelable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import cafe.adriel.voyager.core.registry.ScreenRegistry
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import com.getcode.manager.BottomBarManager
import com.getcode.model.ID
import com.getcode.navigation.NavScreenProvider
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.model.social.user.SocialProfile
import com.getcode.navigation.screens.ContextSheet
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.chat.UserAvatar
import com.getcode.ui.components.user.social.SocialUserDisplay
import com.getcode.ui.core.ContextMenuAction
import com.getcode.ui.theme.ButtonState
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.utils.RepeatOnLifecycle
import com.getcode.ui.utils.getActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import xyz.flipchat.app.R
import xyz.flipchat.app.features.profile.components.ProfileContextAction
import xyz.flipchat.app.oauth.OAuthProvider
import xyz.flipchat.app.oauth.rememberLauncherForOAuth
import xyz.flipchat.app.ui.LocalUserManager
import xyz.flipchat.services.internal.data.mapper.nullIfEmpty

@Parcelize
class ProfileScreen(val userId: ID? = null, val isInTab: Boolean) : Screen, Parcelable {

    override val key: ScreenKey = uniqueScreenKey

    @Composable
    override fun Content() {
        val navigator = LocalCodeNavigator.current
        val context = LocalContext.current
        val userManager = LocalUserManager.current
        val viewModel = getViewModel<ProfileViewModel>()
        val state by viewModel.stateFlow.collectAsState()
        val composeScope = rememberCoroutineScope()

        LaunchedEffect(viewModel, userId) {
            if (userId != null) {
                viewModel.dispatchEvent(ProfileViewModel.Event.OnLoadUser(userId))
            } else {
                viewModel.dispatchEvent(ProfileViewModel.Event.OnLoadUser(userManager?.userId!!))
            }
        }

        RepeatOnLifecycle(targetState = Lifecycle.State.RESUMED) {
            viewModel.dispatchEvent(ProfileViewModel.Event.CheckUserLink)
        }

        Column {
            AppBarWithTitle(
                backButton = !isInTab,
                onBackIconClicked = { navigator.pop() },
                endContent = {
                    if (state.isSelf && isInTab) {
                        AppBarDefaults.Overflow {
                            navigator.show(
                                ContextSheet(
                                    buildActions(
                                        state = state,
                                        dispatchEvent = viewModel::dispatchEvent,
                                        navigateTo = {
                                            composeScope.launch {
                                                delay(200)
                                                navigator.push(it)
                                            }
                                        },
                                        deleteAccount = {
                                            confirmAccountDeletion(
                                                context,
                                                composeScope
                                            ) { activity ->
                                                viewModel.deleteAccount(activity) {
                                                    navigator.replaceAll(ScreenRegistry.get(NavScreenProvider.Login.Home()))
                                                }
                                            }
                                        }
                                    )
                                )
                            )
                        }
                    }
                }
            )
            ProfileContent(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                state = state,
                isInTab = isInTab,
                dispatchEvent = viewModel::dispatchEvent
            )
        }
    }
}


private fun confirmAccountDeletion(
    context: Context,
    composeScope: CoroutineScope,
    onConfirmed: (Activity) -> Unit
) {
    BottomBarManager.showMessage(
        BottomBarManager.BottomBarMessage(
            title = context.getString(R.string.prompt_title_deleteAccount),
            subtitle = context
                .getString(R.string.prompt_description_deleteAccount),
            positiveText = context.getString(R.string.action_permanentlyDeleteAccount),
            tertiaryText = context.getString(R.string.action_cancel),
            onPositive = {
                composeScope.launch {
                    delay(150)
                    context.getActivity()?.let {
                        onConfirmed(it)
                    }
                }
            }
        )
    )
}

@Composable
private fun ProfileContent(
    modifier: Modifier = Modifier,
    isInTab: Boolean,
    state: ProfileViewModel.State,
    dispatchEvent: (ProfileViewModel.Event) -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        UserAvatar(
            modifier = Modifier
                .padding(
                    top = CodeTheme.dimens.grid.x7,
                    bottom = CodeTheme.dimens.grid.x4
                )
                .size(120.dp)
                .clip(CircleShape),
            data = state.imageUrl.nullIfEmpty() ?: state.id,
            overlay = {
                Image(
                    modifier = Modifier.size(60.dp),
                    imageVector = Icons.Default.Person,
                    colorFilter = ColorFilter.tint(Color.White),
                    contentDescription = null,
                )
            }
        )

        // SocialUserDisplay (title with platform logo)
        if (state.linkedSocialProfile != null) {
            SocialUserDisplay(
                modifier = Modifier.fillMaxWidth(),
                profile = state.linkedSocialProfile
            )
        } else {
            Text(
                text = state.displayName,
                style = CodeTheme.typography.textLarge,
                color = CodeTheme.colors.textMain,
                textAlign = TextAlign.Center
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Crossfade for linked vs unlinked content
            Crossfade(
                targetState = state.linkedSocialProfile != null,
                animationSpec = tween(durationMillis = 300)
            ) { isLinked ->
                if (!isLinked) {
                    // Unlinked state
                    if (state.canConnectAccount && state.isSelf) {
                        val xOAuthLauncher =
                            rememberLauncherForOAuth(OAuthProvider.X) { accessToken ->
                                println("x access token=$accessToken")
                                dispatchEvent(ProfileViewModel.Event.LinkXAccount(accessToken))
                            }

                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
                        ) {
                            CodeButton(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = CodeTheme.dimens.grid.x12)
                                    .padding(horizontal = CodeTheme.dimens.inset),
                                buttonState = ButtonState.Filled,
                                onClick = {
                                    xOAuthLauncher.launch(
                                        OAuthProvider.X.launchIntent(
                                            context
                                        )
                                    )
                                },
                                content = {
                                    Image(
                                        painter = rememberVectorPainter(
                                            image = ImageVector.vectorResource(
                                                id = R.drawable.ic_twitter_x
                                            )
                                        ),
                                        colorFilter = ColorFilter.tint(Color.Black),
                                        contentDescription = null
                                    )
                                    Spacer(Modifier.width(CodeTheme.dimens.grid.x2))
                                    Text(text = stringResource(R.string.action_connectYourAccount))
                                }
                            )
                        }
                    }
                } else {
                    // Linked state
                    state.linkedSocialProfile?.let { linkedProfile ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            state.username?.let { username ->
                                AnimatedVisibility(
                                    visible = true,
                                    enter = fadeIn(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    Text(
                                        modifier = Modifier.fillMaxWidth(),
                                        text = username,
                                        style = CodeTheme.typography.textSmall,
                                        color = CodeTheme.colors.textSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            when (linkedProfile) {
                                is SocialProfile.X -> {
                                    AnimatedVisibility(
                                        visible = true,
                                        enter = fadeIn(),
                                        exit = fadeOut() + shrinkVertically()
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = CodeTheme.dimens.grid.x8),
                                                text = "${linkedProfile.followerCountFormatted} Followers",
                                                style = CodeTheme.typography.textSmall,
                                                color = CodeTheme.colors.textSecondary,
                                                textAlign = TextAlign.Center
                                            )
                                            Text(
                                                modifier = Modifier
                                                    .fillMaxWidth(0.70f)
                                                    .padding(top = CodeTheme.dimens.grid.x1),
                                                text = linkedProfile.description,
                                                style = CodeTheme.typography.textSmall,
                                                color = CodeTheme.colors.textSecondary,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }

                                is SocialProfile.Unknown -> Unit
                            }

                            if (!isInTab) {
                                AnimatedVisibility(
                                    visible = true,
                                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
                                ) {
                                    CodeButton(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = CodeTheme.dimens.grid.x12)
                                            .padding(horizontal = CodeTheme.dimens.inset),
                                        buttonState = ButtonState.Filled,
                                        onClick = { uriHandler.openUri(linkedProfile.profileUrl) },
                                        text = stringResource(
                                            R.string.action_openProfileOnPlatform,
                                            linkedProfile.platformTypeName
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildActions(
    state: ProfileViewModel.State,
    navigateTo: (Screen) -> Unit,
    dispatchEvent: (ProfileViewModel.Event) -> Unit,
    deleteAccount: () -> Unit,
): List<ContextMenuAction> {
    return buildList {
        if (state.isStaff) {
            add(
                ProfileContextAction.Labs {
                    navigateTo(ScreenRegistry.get(NavScreenProvider.BetaFlags))
                }
            )
        }

        if (state.linkedSocialProfile != null) {
            add(
                ProfileContextAction.UnlinkSocialProfile(state.linkedSocialProfile) {
                    dispatchEvent(ProfileViewModel.Event.UnlinkSocialProfileRequested(state.linkedSocialProfile))
                }
            )
        }

        add(
            ProfileContextAction.DeleteAccount {
                deleteAccount()
            }
        )
    }
}

