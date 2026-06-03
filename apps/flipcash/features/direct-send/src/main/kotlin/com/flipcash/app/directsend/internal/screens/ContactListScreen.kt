package com.flipcash.app.directsend.internal.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.flipcash.app.contacts.device.DeviceContact
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.send.SendResult
import com.flipcash.app.core.send.SendStep
import com.flipcash.app.directsend.internal.ContactListItem
import com.flipcash.app.directsend.internal.SendFlowViewModel
import com.flipcash.app.permissions.ContactAccessResult
import com.flipcash.app.permissions.rememberContactAccessHandle
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.features.directsend.R
import com.getcode.navigation.flow.LocalOuterCodeNavigator
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.theme.CodeTheme
import com.getcode.theme.White10
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.CircularIconButton
import com.getcode.ui.components.SearchInput
import com.getcode.ui.core.rememberedClickable
import com.getcode.ui.core.verticalScrollStateGradient
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.ui.theme.CodeScaffold
import com.getcode.view.LoadingSuccessState
import kotlin.math.abs
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch


@Composable
internal fun ContactListScreen() {
    val flowNavigator = rememberFlowNavigator<SendStep, SendResult>()
    val viewModel = flowSharedViewModel<SendFlowViewModel>()
    val navigator = LocalOuterCodeNavigator.current

    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.eventFlow
            .filterIsInstance<SendFlowViewModel.Event.SendInvite>()
            .collect { event ->
                navigator.show(AppRoute.Main.InviteContact(event.contact.e164))
            }
    }

    LaunchedEffect(Unit) {
        viewModel.eventFlow
            .filterIsInstance<SendFlowViewModel.Event.NavigateToAmountEntry>()
            .collect { event ->
                flowNavigator.navigateTo(
                    SendStep.AmountEntry(
                        e164 = event.e164,
                        displayName = event.displayName,
                    )
                )
            }
    }

    val accessHandle = rememberContactAccessHandle(
        isPickerMode = state.isPickerMode,
    ) { result ->
        when (result) {
            is ContactAccessResult.Picked -> {
                viewModel.dispatchEvent(SendFlowViewModel.Event.ContactsPicked(result.contacts))
            }

            else -> Unit
        }
    }

    CodeScaffold(
        topBar = {
            Column(
                verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.inset)
            ) {
                AppBarWithTitle(
                    title = stringResource(R.string.title_send),
                    titleAlignment = Alignment.CenterHorizontally,
                    isInModal = true,
                    endContent = {
                        AppBarDefaults.Close { flowNavigator.exitCanceled() }
                    },
                )

                Row(
                    modifier = Modifier
                        .padding(horizontal = CodeTheme.dimens.grid.x3)
                        .padding(top = CodeTheme.dimens.grid.x3),
                    horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SearchInput(
                        modifier = Modifier.weight(1f),
                        state = state.searchState,
                        contentPadding = PaddingValues(start = CodeTheme.dimens.grid.x1),
                    )

                    if (state.isPickerMode) {
                        CircularIconButton(
                            onClick = { accessHandle.launch() }) { size ->
                            Icon(
                                imageVector = Icons.Default.GroupAdd,
                                contentDescription = "",
                                tint = Color.White,
                                modifier = Modifier.requiredSize(size),
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        AnimatedContent(
            targetState = state.listItems.isEmpty(),
            modifier = Modifier.padding(innerPadding),
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "contact-list",
        ) { isEmpty ->
            if (isEmpty) {
                EmptyContactsState()
            } else {
                ContactList(
                    items = state.listItems,
                    isPickerMode = state.isPickerMode,
                    onItemClick = { contact ->
                        viewModel.dispatchEvent(SendFlowViewModel.Event.OnContactClicked(contact))
                    },
                    onItemDismissed = { contact ->
                        viewModel.dispatchEvent(SendFlowViewModel.Event.ContactRemoved(contact.contact.e164))
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyContactsState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.title_noContacts),
                style = CodeTheme.typography.textLarge,
                color = CodeTheme.colors.textMain,
            )
            Text(
                text = stringResource(R.string.subtitle_noContacts),
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun ContactList(
    items: List<ContactListItem>,
    modifier: Modifier = Modifier,
    isPickerMode: Boolean = false,
    onItemClick: (ContactListItem.ContactRow) -> Unit = {},
    onItemDismissed: (ContactListItem.ContactRow) -> Unit = {},
) {
    val listState = rememberLazyListState()
    LazyColumn(
        modifier = Modifier
            .verticalScrollStateGradient(
                scrollState = listState,
                color = CodeTheme.colors.background,
                isLongGradient = true,
            )
            .then(modifier),
        state = listState,
    ) {
        itemsIndexed(
            items = items,
            key = { _, item ->
                when (item) {
                    is ContactListItem.Header -> item.title
                    is ContactListItem.ContactRow -> item.contact.e164
                }
            }
        ) { index, item ->
            when (item) {
                is ContactListItem.Header -> ContactGroupHeader(
                    text = item.title,
                    modifier = Modifier.animateItem(),
                )

                is ContactListItem.ContactRow -> {
                    val isLastInSection =
                        index == items.lastIndex ||
                                items[index + 1] is ContactListItem.Header

                    if (isPickerMode) {
                        SwipeToRevealItem(
                            modifier = Modifier.animateItem(),
                            onDelete = { onItemDismissed(item) },
                        ) {
                            ContactRowItem(
                                contact = item.contact,
                                isOnFlipcash = item.isOnFlipcash,
                                showDivider = !isLastInSection,
                                onClick = { onItemClick(item) },
                            )
                        }
                    } else {
                        ContactRowItem(
                            contact = item.contact,
                            isOnFlipcash = item.isOnFlipcash,
                            showDivider = !isLastInSection,
                        ) {
                            onItemClick(item)
                        }
                    }
                }
            }
        }
    }
}

private enum class RevealValue { Settled, Revealed, Dismissed }

@Composable
private fun SwipeToRevealItem(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val actionWidth = CodeTheme.dimens.staticGrid.x10 + CodeTheme.dimens.inset * 2
    val actionWidthPx = with(density) { actionWidth.toPx() }

    val state = remember { AnchoredDraggableState(initialValue = RevealValue.Settled) }
    var rowWidthPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(rowWidthPx, actionWidthPx) {
        if (rowWidthPx > 0f) {
            state.updateAnchors(DraggableAnchors {
                RevealValue.Settled at 0f
                RevealValue.Revealed at -actionWidthPx
                RevealValue.Dismissed at -rowWidthPx
            })
        }
    }

    val currentOnDelete by rememberUpdatedState(onDelete)
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == RevealValue.Dismissed) {
            currentOnDelete()
        }
    }

    val actionPadding = CodeTheme.dimens.inset
    val minActionSize = CodeTheme.dimens.staticGrid.x10

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { rowWidthPx = it.width.toFloat() },
    ) {
        // Action area — grows from circle to rounded rect as swipe progresses
        Box(
            modifier = Modifier
                .matchParentSize()
                .layout { measurable, constraints ->
                    val absOffset = abs(state.offset)
                    val paddingPx = actionPadding.roundToPx()
                    val minPx = minActionSize.roundToPx()

                    val w = (absOffset.toInt() - paddingPx * 2).coerceAtLeast(minPx)
                    val h = (constraints.maxHeight - paddingPx * 2).coerceAtLeast(minPx)

                    val placeable = measurable.measure(
                        constraints.copy(
                            minWidth = w, maxWidth = w,
                            minHeight = h, maxHeight = h,
                        )
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place(
                            constraints.maxWidth - placeable.width - paddingPx,
                            (constraints.maxHeight - placeable.height) / 2,
                        )
                    }
                }
                .clip(RoundedCornerShape(50))
                .background(CodeTheme.colors.error)
                .rememberedClickable {
                    scope.launch {
                        state.snapTo(RevealValue.Settled)
                    }
                    onDelete()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_delete),
                contentDescription = stringResource(R.string.action_remove),
                tint = Color.White,
                modifier = Modifier.requiredSize(CodeTheme.dimens.staticGrid.x5),
            )
        }

        // Foreground content that slides
        Box(
            modifier = Modifier
                .offset { IntOffset(state.offset.toInt(), 0) }
                .anchoredDraggable(state, Orientation.Horizontal),
        ) {
            content()
        }
    }
}

@Composable
private fun ContactGroupHeader(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Column {
            Row(modifier = Modifier.padding(horizontal = CodeTheme.dimens.inset)) {
                Text(
                    modifier = Modifier.padding(bottom = CodeTheme.dimens.grid.x2),
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary,
                    text = text,
                )
            }
            HorizontalDivider(
                color = CodeTheme.colors.divider,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CodeTheme.dimens.inset)
                    .height(1.dp),
            )
        }
    }
}

@Composable
private fun ContactRowItem(
    contact: DeviceContact,
    isOnFlipcash: Boolean,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CodeTheme.colors.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .rememberedClickable(onClick = onClick)
                .padding(
                    vertical = CodeTheme.dimens.inset,
                    horizontal = CodeTheme.dimens.inset,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
        ) {
            ContactAvatar(
                photoUri = contact.photoUri,
                displayName = contact.displayName,
                modifier = Modifier
                    .requiredSize(CodeTheme.dimens.staticGrid.x8)
                    .clip(CircleShape),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.displayName,
                    style = CodeTheme.typography.textMedium,
                    color = CodeTheme.colors.textMain,
                )
                Text(
                    text = contact.displayNumber.ifEmpty { contact.e164 },
                    style = CodeTheme.typography.textSmall,
                    color = CodeTheme.colors.textSecondary,
                )
            }

            if (isOnFlipcash) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = CodeTheme.colors.textSecondary,
                )
            } else {
                Text(
                    modifier = Modifier
                        .background(
                            color = White10, // ButtonState.Filled10
                            shape = CodeTheme.shapes.small,
                        )
                        .padding(
                            horizontal = CodeTheme.dimens.grid.x2,
                            vertical = CodeTheme.dimens.grid.x1,
                        ),
                    text = stringResource(R.string.action_invite),
                    style = CodeTheme.typography.textMedium,
                    color = CodeTheme.colors.textMain,
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                color = CodeTheme.colors.divider,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .padding(
                        start = CodeTheme.dimens.inset + CodeTheme.dimens.staticGrid.x8 + CodeTheme.dimens.grid.x3,
                        end = CodeTheme.dimens.inset,
                    ),
            )
        }
    }
}

@Composable
private fun ContactAvatar(
    photoUri: String?,
    displayName: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(CodeTheme.colors.contactAvatar.colors)
        )
    ) {
        if (photoUri != null) {
            var isError by rememberSaveable(photoUri) { mutableStateOf(false) }
            if (!isError) {
                val context = LocalContext.current
                val request = remember(photoUri) {
                    ImageRequest.Builder(context)
                        .crossfade(true)
                        .data(photoUri.toUri())
                        .build()
                }
                AsyncImage(
                    modifier = Modifier.matchParentSize(),
                    model = request,
                    contentDescription = null,
                    onError = { isError = true },
                )
            }
            if (isError) {
                InitialsText(displayName)
            }
        } else {
            InitialsText(displayName)
        }
    }
}

@Composable
private fun BoxScope.InitialsText(displayName: String) {
    val initials = remember(displayName) {
        displayName.split(" ")
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifEmpty { "?" }
    }
    Text(
        modifier = Modifier.align(Alignment.Center),
        text = initials,
        style = CodeTheme.typography.textSmall,
        color = CodeTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
    )
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun ContactListPreview() {
    val fakeNames = listOf(
        "Alice Anderson", "Bob Baker", "Charlie Chen", "Dana Davis",
        "Eli Evans", "Fiona Fisher", "George Garcia", "Hannah Hill",
        "Isaac Ingram", "Julia Jones", "Kevin Kim", "Latif Peracha",
        "Maya Martinez", "Noah Nguyen", "Olivia Ortiz", "Paul Park",
        "Quinn Quinn", "Rachel Robinson", "Sam Smith", "Tina Torres",
    )

    val flipcashContacts = fakeNames.take(6).mapIndexed { i, name ->
        ContactListItem.ContactRow(
            contact = DeviceContact(
                e164 = "+1555000${1000 + i}",
                androidContactId = i.toLong(),
                displayName = name,
                photoUri = null,
                displayNumber = "(555) 000-${1000 + i}",
            ),
            isOnFlipcash = true,
        )
    }
    val otherContacts = fakeNames.drop(6).mapIndexed { i, name ->
        ContactListItem.ContactRow(
            contact = DeviceContact(
                e164 = "+1555000${2000 + i}",
                androidContactId = (100 + i).toLong(),
                displayName = name,
                photoUri = null,
                displayNumber = "(555) 000-${2000 + i}",
            ),
            isOnFlipcash = false,
        )
    }

    val items = buildList {
        add(ContactListItem.Header("Flipcash Contacts"))
        addAll(flipcashContacts)
        add(ContactListItem.Header("Other Contacts"))
        addAll(otherContacts)
    }

    ContactList(items) { }
}
