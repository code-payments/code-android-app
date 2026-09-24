package com.flipcash.app.tipping.internal.screens

import android.os.Parcelable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.flipcash.app.core.chat.NewGroupStep
import com.flipcash.app.core.data.Loadable
import com.flipcash.app.core.data.isLoaded
import com.flipcash.app.core.ui.DisplayTextInput
import com.flipcash.app.tipping.internal.BalancePresets
import com.flipcash.app.tipping.internal.CreateGroupViewModel
import com.flipcash.app.tipping.internal.GroupCurrency
import com.flipcash.features.tipping.R
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.opencode.model.financial.Fiat
import com.getcode.theme.CodeTheme
import com.getcode.theme.White
import com.getcode.theme.White05
import com.getcode.theme.White10
import com.getcode.theme.White20
import com.getcode.theme.White50
import com.getcode.theme.extraSmall
import com.getcode.theme.rememberDynamicAccent
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.ui.utils.rememberKeyboardController

/**
 * "New Public Group" — node 10127:118014 empty, 10127:118237 ready to create.
 *
 * Everything on the screen edits one draft held by [CreateGroupViewModel], so the currency sheet and
 * the custom-amount keypad can be separate steps without the form losing what was typed.
 *
 * Create is inert until the draft is complete *and* the creator's own balance satisfies the rule
 * they are setting. The last part is not obvious from the button alone, so the caption under the
 * card says so while it holds, and the refusal names the currency when Create is tapped anyway —
 * which is reachable, because the balance behind the check is a live flow.
 */
@Composable
internal fun NewGroupFormScreen(viewModel: CreateGroupViewModel) {
    val flowNavigator = rememberFlowNavigator<NewGroupStep, Parcelable>()
    val keyboard = rememberKeyboardController()
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    val pickPicture = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) {
            viewModel.dispatchEvent(CreateGroupViewModel.Event.OnImageSelected(uri))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AppBarWithTitle(
            title = stringResource(R.string.title_newPublicGroup),
            titleAlignment = Alignment.CenterHorizontally,
            onBackIconClicked = { keyboard.hideIfVisible { flowNavigator.exitCanceled() } },
            endContent = {
                CreateAction(
                    enabled = state.canCreate,
                    isLoading = state.processingState.loading,
                    isSuccess = state.processingState.success,
                    onClick = {
                        keyboard.hideIfVisible {
                            viewModel.dispatchEvent(CreateGroupViewModel.Event.CreateRequested)
                        }
                    },
                )
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CodeTheme.dimens.inset)
                .padding(top = CodeTheme.dimens.grid.x3)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x9),
        ) {
            IdentityCard(
                image = state.image,
                titleField = {
                    DisplayTextInput(
                        state = state.titleFieldState,
                        placeholder = stringResource(R.string.hint_groupName),
                        modifier = Modifier.fillMaxWidth(),
                        style = CodeTheme.typography.screenTitle.copy(
                            color = CodeTheme.colors.textMain,
                        ),
                        placeholderStyle = CodeTheme.typography.screenTitle.copy(
                            color = CodeTheme.colors.textMain.copy(alpha = 0.2f),
                        ),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done,
                        ),
                        onKeyboardAction = { keyboard.hideIfVisible { } },
                    )
                },
                onPick = { pickPicture.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
            )

            RequirementSection(
                state = state,
                onChangeCurrency = {
                    keyboard.hideIfVisible {
                        flowNavigator.navigateTo(NewGroupStep.SelectCurrency)
                    }
                },
                onPresetSelected = {
                    viewModel.dispatchEvent(CreateGroupViewModel.Event.OnAmountSelected(it))
                },
                onCustomAmount = {
                    keyboard.hideIfVisible {
                        flowNavigator.navigateTo(NewGroupStep.CustomAmount)
                    }
                },
            )
        }
    }
}

/**
 * The Create capsule in the nav bar — node 10127:118237.
 *
 * Painted with the wallpaper accent, the way the currency creator's bill step paints its Next
 * capsule: both commit a thing the user has just been choosing the look of, and both sit in the nav
 * bar over that preview, so they take the system accent rather than the app's white fill.
 * [rememberDynamicAccent] falls back to the theme's own action colors below API 31.
 *
 * Unlike Next, Create commits over the network, so the capsule reports progress and success in place
 * of its label. The label stays in the layout while that happens — the pill would otherwise shrink
 * to the width of a spinner and jump back.
 */
@Composable
private fun CreateAction(
    enabled: Boolean,
    isLoading: Boolean,
    isSuccess: Boolean,
    onClick: () -> Unit,
) {
    val (accent, onAccent) = rememberDynamicAccent(
        fallbackAccent = CodeTheme.colors.secondary,
        fallbackOnAccent = CodeTheme.colors.onAction,
    )

    val clickable = enabled && !isLoading && !isSuccess
    // An incomplete draft dims the capsule rather than removing it: it is the only thing on screen
    // that says the form has an end, so it stays visible while it is inert.
    val container = if (enabled) accent else White10
    val content = if (enabled) onAccent else White50

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(container)
            .clickable(enabled = clickable, onClick = onClick)
            .padding(
                horizontal = CodeTheme.dimens.grid.x2,
                vertical = CodeTheme.dimens.grid.x1,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.action_create),
            style = CodeTheme.typography.textMedium,
            color = content,
            modifier = Modifier.alpha(if (isLoading || isSuccess) 0f else 1f),
        )

        if (isLoading) {
            CodeCircularProgressIndicator(
                strokeWidth = CodeTheme.dimens.thickBorder,
                color = content,
                modifier = Modifier.size(CodeTheme.dimens.grid.x3),
            )
        }

        if (isSuccess) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(CodeTheme.dimens.grid.x3),
            )
        }
    }
}

/**
 * The picture-and-name card at the top of the form.
 *
 * The tile shows the pick as soon as it is made and keeps showing it while the re-encode runs —
 * [Loadable.Loading] carries the source uri for exactly that — so the only spinner is over an image
 * the user already recognises.
 */
@Composable
private fun IdentityCard(
    image: Loadable<android.net.Uri>,
    titleField: @Composable () -> Unit,
    onPick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CodeTheme.shapes.extraSmall)
            .background(White05)
            .padding(CodeTheme.dimens.grid.x5),
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(AvatarSize)
                .clip(CircleShape)
                .background(White05)
                .border(CodeTheme.dimens.border, White20, CircleShape)
                .clickable(onClick = onPick),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(targetState = image, label = "group picture") { picture ->
                when {
                    picture.isLoaded() -> AsyncImage(
                        model = ImageRequest.Builder(LocalPlatformContext.current)
                            .data(picture.data)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )

                    picture.dataOrNull != null -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalPlatformContext.current)
                                .data(picture.dataOrNull)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        CodeCircularProgressIndicator()
                    }

                    else -> Icon(
                        painter = painterResource(R.drawable.ic_camera),
                        contentDescription = null,
                        tint = White50,
                        modifier = Modifier.size(CameraIconSize),
                    )
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) { titleField() }
    }
}

/**
 * The balance rule: which currency, and how much of it.
 */
@Composable
private fun RequirementSection(
    state: CreateGroupViewModel.State,
    onChangeCurrency: () -> Unit,
    onPresetSelected: (Fiat) -> Unit,
    onCustomAmount: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.staticGrid.x2),
    ) {
        Text(
            modifier = Modifier.padding(horizontal = CodeTheme.dimens.grid.x3),
            text = stringResource(R.string.title_minimumBalanceRequired),
            style = LabelStyle,
            color = White50,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CodeTheme.shapes.extraSmall)
                .background(White05)
                .padding(CodeTheme.dimens.grid.x3),
            verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.staticGrid.x2),
        ) {
            MintRow(state = state, onClick = onChangeCurrency)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
            ) {
                BalancePresets.forEach { preset ->
                    AmountChip(
                        modifier = Modifier.weight(1f),
                        selected = state.amount == preset,
                        onClick = { onPresetSelected(preset) },
                    ) { textColor ->
                        Text(
                            text = preset.formatted(rule = Fiat.FormattingRule.Truncated),
                            style = ChipStyle,
                            color = textColor,
                        )
                    }
                }

                // The fourth slot is the keypad's. A custom amount has nowhere else to show, so it
                // takes this chip's face and the ellipsis is what an unused slot looks like.
                val custom = state.customAmount
                AmountChip(
                    modifier = Modifier.weight(1f),
                    selected = custom != null,
                    onClick = onCustomAmount,
                ) { textColor ->
                    Text(
                        text = custom?.formatted(rule = Fiat.FormattingRule.Truncated) ?: "•••",
                        style = ChipStyle,
                        color = textColor,
                    )
                }
            }
        }

        // Create dims for three reasons, and two of them — no name, no amount — are visible in the
        // form itself. The third is not: a rule the creator cannot meet looks exactly like a
        // complete draft. So the caption stops explaining what the requirement does to anyone else
        // and says what is holding this creator up instead.
        val unsatisfied = state.rules != null && !state.selfSatisfied

        Text(
            modifier = Modifier.padding(horizontal = CodeTheme.dimens.grid.x3),
            text = stringResource(
                if (unsatisfied) {
                    R.string.error_description_groupRuleNotSelfSatisfied
                } else {
                    R.string.subtitle_groupBalanceRequirement
                }
            ),
            style = CodeTheme.typography.caption.copy(fontSize = 13.sp, lineHeight = 17.sp),
            color = if (unsatisfied) CodeTheme.colors.errorText else White50,
        )
    }
}

@Composable
private fun MintRow(state: CreateGroupViewModel.State, onClick: () -> Unit) {
    Row(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val allCurrencies = state.currency == GroupCurrency.All
        if (allCurrencies) {
            // Node 10364:1059 — the sheet's card glyph, scaled to the row.
            AllCurrenciesIcon(discSize = MintIconSize, iconSize = AllCurrenciesGlyphSize)
        } else {
            AsyncImage(
                model = ImageRequest.Builder(LocalPlatformContext.current)
                    .data(state.token?.imageUrl)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(MintIconSize)
                    .clip(CircleShape)
                    .background(White10),
            )
        }

        // A picked token whose balance has since left the list has no name to show, so the row
        // falls back to the sheet's own title rather than rendering a blank, tappable line.
        val name = if (allCurrencies) {
            stringResource(R.string.title_allCurrencies)
        } else {
            state.currencyName
        }
        Text(
            text = name ?: stringResource(R.string.title_selectCurrency),
            style = CodeTheme.typography.textMedium.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = if (name == null) White50 else CodeTheme.colors.textMain,
        )

        Icon(
            painter = painterResource(R.drawable.ic_chevron_down_small),
            contentDescription = null,
            tint = CodeTheme.colors.textMain,
            modifier = Modifier.size(CodeTheme.dimens.staticGrid.x5),
        )
    }
}

/** A preset slot. Selected inverts to a solid white face — node 10127:118237's `$100`. */
@Composable
private fun AmountChip(
    modifier: Modifier = Modifier,
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable (textColor: Color) -> Unit,
) {
    Box(
        modifier = modifier
            .height(ChipHeight)
            .clip(CodeTheme.shapes.extraSmall)
            .background(if (selected) White else White10)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content(if (selected) CodeTheme.colors.background else CodeTheme.colors.textMain)
    }
}

private val AvatarSize = 74.dp
private val CameraIconSize = 40.dp
private val MintIconSize = 20.dp
private val AllCurrenciesGlyphSize = 12.dp
private val ChipHeight = 60.dp

private val LabelStyle
    @Composable get() = CodeTheme.typography.textMedium.copy(
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    )

private val ChipStyle
    @Composable get() = CodeTheme.typography.textLarge.copy(
        fontSize = 22.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
    )
