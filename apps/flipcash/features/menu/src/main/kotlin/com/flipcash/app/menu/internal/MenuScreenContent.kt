package com.flipcash.app.menu.internal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.bills.ScannableRenderer
import com.flipcash.app.bills.components.cards.LocalTipCardBaseAlpha
import com.flipcash.app.bills.components.cards.LocalTipCardColor
import com.flipcash.app.bills.components.cards.TipCardFlattened
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.bill.Scannable
import com.flipcash.app.core.navigation.LocalTabBarPadding
import com.flipcash.app.menu.internal.MenuScreenViewModel.Event
import com.flipcash.app.menu.internal.MenuScreenViewModel.ProfileState
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.core.R as CoreR
import com.flipcash.features.menu.R
import com.flipcash.services.models.UserProfile
import com.flipcash.services.models.chat.BlobAccessContext
import com.flipcash.shared.common.ui.ContactAvatar
import com.flipcash.shared.common.ui.profile.ProfileActionButton
import com.flipcash.shared.common.ui.profile.ProfileHeader
import com.flipcash.shared.common.ui.profile.ProfileStatsCard
import com.getcode.theme.CodeTheme
import com.getcode.theme.White
import com.getcode.theme.White08
import com.getcode.ui.core.verticalScrollStateGradient
import com.getcode.ui.theme.CodeScaffold
import com.getcode.ui.utils.sheetResignmentBehavior
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurDefaults
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * The "You" tab: the viewer's own profile, in the same layout other people's profiles use. The
 * settings gear floats over the cover's top trailing corner and scrolls away with the page.
 */
@Composable
internal fun MenuScreenContent(viewModel: MenuScreenViewModel) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    // v2's tab bar is a hoisted overlay drawn ABOVE this content, so reserve its height as bottom
    // content padding — the list then scrolls clear of the bar instead of running under it. Per-entry
    // via LocalTabBarPadding, which is only non-zero for tab homes. v1 has no such bar.
    val bottomInset = LocalTabBarPadding.current.calculateBottomPadding()

    // No app bar: the cover is the first thing on the page, and reaches the top of the display. It
    // is not padded for the status bar, so the gear takes that clearance for itself.
    CodeScaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScrollStateGradient(scrollState = listState, isLongGradient = true)
                .sheetResignmentBehavior(listState),
            state = listState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = bottomInset),
        ) {
            when (val profileState = state.profileState) {
                // A named account resolves in a frame or two, and a prompt that flashed at it would
                // be a lie. The gear is still there, so Settings is never out of reach.
                ProfileState.Unknown -> item(key = "settings_gear") {
                    SettingsGear(onClick = { viewModel.dispatchEvent(Event.OpenScreen(AppRoute.Menu.Settings)) })
                }

                is ProfileState.Unclaimed -> {
                    // As on iOS: the header keeps its cover, avatar and Edit Profile, with no Share
                    // (there is nothing to share yet), and the claim prompt takes the stats' place.
                    item(key = "profile_header") {
                        OwnProfileHeader(
                            profile = profileState.profile,
                            onEdit = { viewModel.dispatchEvent(Event.OpenScreen(AppRoute.Menu.EditProfile)) },
                            onShare = null,
                            onClaimUsername = state.usernameProgress?.let {
                                { viewModel.dispatchEvent(Event.ClaimUsername) }
                            },
                            onSettings = { viewModel.dispatchEvent(Event.OpenScreen(AppRoute.Menu.Settings)) },
                        )
                    }
                    item(key = "claim_prompt") {
                        Spacer(Modifier.height(UnclaimedTopSpacing))
                        UnclaimedTipCardPrompt(
                            placeholder = profileState.placeholder,
                            cardWidth = YouCardWidth,
                            onClaim = { viewModel.dispatchEvent(Event.ClaimTipCard) },
                        )
                    }
                }

                is ProfileState.Named -> {
                    item(key = "profile_header") {
                        OwnProfileHeader(
                            profile = profileState.profile,
                            onEdit = { viewModel.dispatchEvent(Event.OpenScreen(AppRoute.Menu.EditProfile)) },
                            onShare = { viewModel.dispatchEvent(Event.ShareProfile) },
                            onClaimUsername = state.usernameProgress?.let {
                                { viewModel.dispatchEvent(Event.ClaimUsername) }
                            },
                            onSettings = { viewModel.dispatchEvent(Event.OpenScreen(AppRoute.Menu.Settings)) },
                        )
                    }
                    item(key = "stats") {
                        ProfileStatsCard(
                            modifier = Modifier
                                .padding(horizontal = CodeTheme.dimens.inset)
                                .padding(top = CodeTheme.dimens.staticGrid.x4),
                            minimumToChat = state.minimumToChat,
                            joined = profileState.joined,
                        )
                    }
                    item(key = "bottom_spacer") { Spacer(Modifier.height(CodeTheme.dimens.grid.x4)) }
                }
            }
        }
    }
}

/** The viewer's profile header with the settings gear over the cover's top trailing corner. */
@Composable
private fun OwnProfileHeader(
    profile: UserProfile?,
    onEdit: () -> Unit,
    onShare: (() -> Unit)?,
    onClaimUsername: (() -> Unit)?,
    onSettings: () -> Unit,
) {
    // The header is what scrolls under the gear, so it is the gear's frosting source.
    val hazeState = rememberHazeState()
    Box {
        ProfileHeader(
            modifier = Modifier.hazeSource(hazeState),
            cover = profile?.coverPicture,
            // The viewer's own blobs, so no profile id is needed to authorize re-minting them.
            access = BlobAccessContext.Owned,
            avatar = { modifier ->
                ContactAvatar(
                    image = profile?.profilePicture,
                    displayName = profile?.displayName.orEmpty(),
                    access = BlobAccessContext.Owned,
                    modifier = modifier,
                )
            },
            title = profile?.displayName?.ifEmpty { null },
            subtitle = profile?.username?.takeIf { it.isNotEmpty() }?.let { "@$it" },
            body = profile?.bio?.ifEmpty { null },
            // As on iOS: a one-line offer under the handle while there is no handle, or only an
            // auto-assigned one. The tap goes through the balance gate like the old progress card.
            underSubtitle = {
                if (onClaimUsername != null) {
                    Text(
                        modifier = Modifier
                            .padding(top = CodeTheme.dimens.staticGrid.x1)
                            .clickable(onClick = onClaimUsername)
                            .padding(vertical = CodeTheme.dimens.staticGrid.x1)
                            .testTag("you-claim-username"),
                        text = stringResource(R.string.action_claimYourUsernameLink),
                        style = CodeTheme.typography.textSmall,
                        color = CodeTheme.colors.textMain,
                    )
                }
            },
            actions = {
                ProfileActionButton(
                    text = stringResource(R.string.action_editProfile),
                    onClick = onEdit,
                )
                // Only a named profile has something to share.
                if (onShare != null) {
                    ProfileActionButton(
                        icon = ImageVector.vectorResource(R.drawable.ic_share_os),
                        contentDescription = stringResource(R.string.action_share),
                        onClick = onShare,
                    )
                }
            },
        )
        SettingsGear(
            modifier = Modifier.align(Alignment.TopEnd),
            onClick = onSettings,
            hazeState = hazeState,
        )
    }
}

/**
 * The settings gear, clear of the status bar. Over the cover it frosts the picture behind it
 * ([hazeState]); with no cover to frost (an account still loading or unnamed) it falls back to the
 * flat translucent fill.
 */
@Composable
private fun SettingsGear(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
) {
    ProfileBarButton(
        icon = R.drawable.ic_settings_outline,
        contentDescription = stringResource(CoreR.string.title_settings),
        onClick = onClick,
        modifier = modifier,
        hazeState = hazeState,
        testTag = "you-settings",
    )
}

/**
 * The resting card's width (node 9278:7301: 241.636), kept as the fraction of a 402-wide display it
 * was drawn at rather than the dp it happened to measure on it.
 */
private const val YouCardWidthFraction = 0.60f

private val YouCardWidth: Dp
    @Composable get() = CodeTheme.dimens.screenWidth * YouCardWidthFraction

/** Gap between the header and the unclaimed stand-in (iOS 24). */
private val UnclaimedTopSpacing: Dp
    @Composable get() = CodeTheme.dimens.staticGrid.x5

/**
 * What the "You" tab shows before the account has a display name: the card it *would* have, blurred
 * out behind a prompt to claim it. Mirrors iOS `YouScreen.setupPrompt`.
 *
 * The stand-in is the account's real scannable payload drawn over an unnamed profile, with the
 * card's own fill turned off so the 8% ground shows through — the same construction iOS uses. It is
 * decoration: not tappable, not expandable, not shareable. It takes the stats card's place under the
 * header, and the header drops its Share button, because there is nothing yet to share.
 *
 * [blurEnabled] is haze's own API-31 gate, surfaced so a preview can render what an API 29/30
 * device draws (see `Preview_UnclaimedTipCardPrompt_NoBlur`). Leave it at the default in app code.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnclaimedTipCardPrompt(
    placeholder: Scannable.TipCard?,
    cardWidth: Dp,
    onClaim: () -> Unit,
    blurEnabled: Boolean = HazeBlurDefaults.isBlurEnabledByDefault(),
) {
    val shape = RoundedCornerShape(cardWidth * TipCardCornerFraction)
    val hazeState = rememberHazeState()
    val placeholderName = stringResource(CoreR.string.label_tipCardNamePlaceholder)

    // The card's ground, flattened: the 8% white wash resolved against the page behind it. The
    // frosting composites over this, which is what makes the overlay opaque — haze draws the blur as
    // a layer in FRONT of its source rather than filtering it in place, so without an opaque ground
    // the sharp code would read straight through its own frosting.
    // The HazeBlurStyle builder is not a @Composable scope, so the theme read is hoisted above it.
    val cardGround = White08.compositeOver(CodeTheme.colors.background)
    val frosting = HazeBlurStyle {
        blurEnabled(blurEnabled)
        blurRadius(PlaceholderBlurRadius)
        backgroundColor(cardGround)
        // Haze only blurs on API 31+ and minSdk is 29; below that it falls back to this scrim, which
        // has to be opaque for the same reason. Never the sharp code: an unclaimed card drawn
        // legibly would read as a real one.
        fallbackColorEffect(HazeColorEffect.tint(cardGround))
        // Off: haze's default film grain over a scannable figure reads as noise in the code itself
        // rather than as texture, and iOS frosts the stand-in with a plain blur.
        noiseFactor(0f)
    }

    // The stand-in is a fixed-width card in a full-width header slot, so it has to be centred the way
    // the claimed card's own Column centres it. Without this it sits at the slot's start edge.
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                // The card pads itself off the status bar; the list's content padding already owns that
                // clearance here, so consume the inset rather than paying it twice.
                .consumeWindowInsets(WindowInsets.statusBarsIgnoringVisibility)
                .size(cardWidth, cardWidth * TipCardAspectRatio)
                .clip(shape)
                .background(White08)
                .border(PlaceholderBorderWidth, White.copy(alpha = 0.12f), shape),
            contentAlignment = Alignment.Center,
        ) {
            if (placeholder != null) {
                // A nameless account renders its name line as a bare "Tip ", which frosts to a much
                // narrower smudge than a real card's. Stand a name in so the blur has the weight the
                // claimed card's would (iOS `YouScreen.placeholderName`). The handle goes with it —
                // this card is a stand-in, and a real `@handle` under a stand-in name is neither.
                val stoodIn = remember(placeholder, placeholderName) {
                    placeholder.copy(
                        user = placeholder.user.copy(
                            displayName = placeholderName,
                            username = null,
                        )
                    )
                }

                CompositionLocalProvider(
                    LocalTipCardColor provides TipCardFlattened,
                    // Fill off, so the placeholder ground behind it is what's frosted, not an opaque card.
                    LocalTipCardBaseAlpha provides 0f,
                ) {
                    ScannableRenderer(
                        modifier = Modifier.hazeSource(hazeState),
                        scannable = stoodIn,
                        tipCardWidth = cardWidth,
                    )
                }

                // Drawn over the stand-in and under the prompt, so the copy below stays sharp.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .hazeBlur(HazeInput.Sources(hazeState), frosting)
                )
            }

            Column(
                // iOS caps the prompt at the card width less 16, so the copy never reaches the corners.
                modifier = Modifier.width(cardWidth - PromptInset * 2),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(CoreR.string.title_tipIntro),
                    style = CodeTheme.typography.textLarge,
                    color = CodeTheme.colors.textMain,
                    textAlign = TextAlign.Center,
                )
                Text(
                    modifier = Modifier.padding(top = CodeTheme.dimens.grid.x2),
                    text = stringResource(CoreR.string.subtitle_tipIntro),
                    style = CodeTheme.typography.textSmall,
                    // Full strength, not secondary: it sits over the blurred code's glow.
                    color = CodeTheme.colors.textMain,
                    textAlign = TextAlign.Center,
                )
                Text(
                    modifier = Modifier
                        .padding(top = CodeTheme.dimens.grid.x4)
                        .clip(CircleShape)
                        .background(CodeTheme.colors.textMain)
                        .clickable(onClick = onClaim)
                        .padding(
                            horizontal = CodeTheme.dimens.grid.x5,
                            vertical = CodeTheme.dimens.grid.x2,
                        ),
                    text = stringResource(CoreR.string.action_startReceivingTips),
                    style = CodeTheme.typography.textMedium,
                    color = CodeTheme.colors.background,
                )
            }
        }

        Spacer(Modifier.height(UnclaimedRowsGap))
    }
}

/** The tip card's height-to-width proportion and corner radius, mirrored from `TipCard`. */
private const val TipCardAspectRatio = 333f / 269f
private const val TipCardCornerFraction = 0.08f

/** How far the unclaimed stand-in is frosted (iOS `YouScreen.setupPrompt`: `blur(radius: 12)`). */
private val PlaceholderBlurRadius = 12.dp

/** The hairline that keeps the blurred stand-in readable as a card rather than a smudge. */
private val PlaceholderBorderWidth: Dp
    @Composable get() = CodeTheme.dimens.border

/** Margin between the claim prompt and the stand-in card's edges (iOS: 16 across the pair). */
private val PromptInset: Dp
    @Composable get() = CodeTheme.dimens.grid.x2

/**
 * Gap below the unclaimed stand-in. Wider than the claimed card's 19, because the claimed
 * card pays part of its clearance in the action buttons that the unclaimed state doesn't draw (iOS `YouScreen`: `.padding(.top, displayName == nil ? 48 : 19)`).
 */
private val UnclaimedRowsGap: Dp
    @Composable get() = CodeTheme.dimens.grid.x10

private val PreviewCodeData = listOf(
    0xA5, 0x3C, 0xD7, 0x8B, 0x14, 0xE9, 0x62, 0xF0,
    0x4D, 0xB6, 0x29, 0x7A, 0xC3, 0x58, 0x91, 0xDE,
    0x6F, 0x03, 0xB4, 0x87, 0x2C, 0xE5, 0x50, 0xA9,
    0x1E, 0x73, 0xC6, 0x3F, 0x98, 0x41, 0xDA, 0x65,
    0x0B, 0xF2, 0x7D, 0xAE, 0x53, 0xC0, 0x19,
).map { it.toByte() }

/** The stand-in as an API 31+ device draws it: haze's RenderEffect blur over the card's ground. */
@Preview(name = "Unclaimed — blurred (API 31+)")
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_UnclaimedTipCardPrompt() {
    UnclaimedTipCardPrompt(
        placeholder = Scannable.TipCard(data = PreviewCodeData, user = UserProfile.Empty),
        cardWidth = YouCardWidth,
        onClaim = {},
    )
}

/**
 * The same stand-in on API 29/30, where haze can't blur and falls through to its scrim delegate.
 * The scrim draws nothing on its own, so this is the preview that proves `fallbackColorEffect`
 * is doing its job: the code underneath must be fully covered, not legible.
 */
@Preview(name = "Unclaimed — scrim fallback (API 29/30)")
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun Preview_UnclaimedTipCardPrompt_NoBlur() {
    UnclaimedTipCardPrompt(
        placeholder = Scannable.TipCard(data = PreviewCodeData, user = UserProfile.Empty),
        cardWidth = YouCardWidth,
        onClaim = {},
        blurEnabled = false,
    )
}
