package com.flipcash.app.directsend.internal.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.contacts.ui.ContactAvatar
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.android.extensions.launchAppSettings
import com.flipcash.app.core.contacts.DeviceContact
import com.flipcash.app.core.send.SendResult
import com.flipcash.app.core.send.SendStep
import com.flipcash.app.directsend.internal.ContactListItem
import com.flipcash.app.directsend.internal.SendFlowViewModel
import com.flipcash.app.permissions.ContactAccessResult
import com.flipcash.app.permissions.rememberContactAccessHandle
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.features.directsend.R
import com.flipcash.shared.chat.ui.AnimatedConversationPaymentsPreview
import com.getcode.navigation.flow.LocalOuterCodeNavigator
import com.getcode.navigation.flow.flowSharedViewModel
import com.getcode.navigation.flow.rememberFlowNavigator
import com.getcode.theme.CodeTheme
import com.getcode.theme.White10
import com.getcode.theme.extraLarge
import com.getcode.theme.extraSmall
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.SearchInput
import com.getcode.ui.core.debugBounds
import com.getcode.ui.core.verticalScrollStateGradient
import com.getcode.ui.theme.CodeButton
import com.getcode.ui.theme.CodeScaffold
import com.getcode.util.permissions.PermissionResult
import kotlinx.coroutines.flow.filterIsInstance


@Composable
internal fun ContactListScreen() {
    val flowNavigator = rememberFlowNavigator<SendStep, SendResult>()
    val viewModel = flowSharedViewModel<SendFlowViewModel>()
    val navigator = LocalOuterCodeNavigator.current

    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<SendFlowViewModel.Event.SendInvite>()
            .collect { event ->
                navigator.show(AppRoute.Main.InviteContact(event.contact.e164))
            }
    }

    LaunchedEffect(viewModel) {
        viewModel.eventFlow
            .filterIsInstance<SendFlowViewModel.Event.NavigateToChat>()
            .collect { event ->
                flowNavigator.navigate(
                    AppRoute.Messaging.Chat(identifier = event.identifier)
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
            when {
                isEmpty && state.searchState.text.isNotEmpty() -> {
                    EmptySearchState(state.searchState.text.toString())
                }

                isEmpty -> {
                    EmptyContactsState()
                }

                else -> {
                    ContactList(
                        items = state.listItems,
                        isPickerMode = state.isPickerMode,
                        onAddMoreContacts = { accessHandle.launch() },
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
}

@Composable
private fun EmptyContactsState(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = CodeTheme.dimens.grid.x12),
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
private fun EmptySearchState(
    searchQuery: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = CodeTheme.dimens.grid.x12),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                modifier = Modifier.size(CodeTheme.dimens.staticGrid.x13),
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                colorFilter = ColorFilter.tint(CodeTheme.colors.textSecondary),
            )
            Text(
                modifier = Modifier.padding(top = CodeTheme.dimens.grid.x5),
                text = stringResource(R.string.title_noSearchResults, searchQuery),
                style = CodeTheme.typography.textLarge,
                color = CodeTheme.colors.textMain,
            )
            Text(
                text = stringResource(R.string.subtitle_noSearchResults),
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
    onAddMoreContacts: () -> Unit = {},
    onItemClick: (ContactListItem.ContactRow) -> Unit = {},
    onItemDismissed: (ContactListItem.ContactRow) -> Unit = {},
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val accessHandle = rememberContactAccessHandle(
        isPickerMode = isPickerMode,
    )

    LazyColumn(
        modifier = Modifier
            .verticalScrollStateGradient(
                scrollState = listState,
                color = CodeTheme.colors.background,
                isLongGradient = true,
            )
            .then(modifier),
        state = listState,
        contentPadding = PaddingValues(bottom = CodeTheme.dimens.grid.x3),
    ) {
        itemsIndexed(
            items = items,
            key = { _, item ->
                when (item) {
                    is ContactListItem.Header -> item.title
                    is ContactListItem.ContactRow -> item.chatId?.toString() ?: item.contact.e164
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

                    ContactRowItem(
                        contact = item.contact,
                        isOnFlipcash = item.isOnFlipcash,
                        lastMessagePreview = item.lastMessagePreview,
                        unreadCount = item.unreadCount,
                        showDivider = !isLastInSection,
                    ) {
                        onItemClick(item)
                    }
                }
            }
        }

        if (accessHandle.permissionStatus != PermissionResult.Granted && !isPickerMode) {
            item {
                Column(
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .padding(horizontal = CodeTheme.dimens.inset)
                        .border(
                            width = CodeTheme.dimens.border,
                            color = CodeTheme.colors.divider,
                            shape = CodeTheme.shapes.extraLarge,
                        )
                        .padding(
                            horizontal = CodeTheme.dimens.grid.x2,
                            vertical = CodeTheme.dimens.grid.x2,
                        ),
                    verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.inset),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AnimatedConversationPaymentsPreview(
                        modifier = Modifier.fillMaxWidth(),
                        animate = false,
                    )
                    Text(
                        text = stringResource(R.string.permissions_title_contactsRationale),
                        style = CodeTheme.typography.displayExtraSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = CodeTheme.colors.textMain,
                    )
                    CodeButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = stringResource(R.string.action_allowContactAccessInSettings),
                    ) {
                        context.launchAppSettings()
                    }
                }
            }
        }

        if (isPickerMode) {
            item {
                PickerModeHeader(
                    onAddMoreContacts = onAddMoreContacts,
                )
            }
        }
    }
}

@Composable
private fun PickerModeHeader(
    onAddMoreContacts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val avatarSize = CodeTheme.dimens.staticGrid.x8
    val overlap = CodeTheme.dimens.staticGrid.x4

    Column(modifier = modifier.fillMaxWidth()) {
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onAddMoreContacts)
                .padding(
                    horizontal = CodeTheme.dimens.inset,
                    vertical = CodeTheme.dimens.grid.x3,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(-overlap)) {
                Box(
                    modifier = Modifier
                        .zIndex(4f)
                        .requiredSize(avatarSize)
                        .border(
                            width = CodeTheme.dimens.border,
                            color = CodeTheme.colors.background,
                            shape = CircleShape,
                        )
                        .background(
                            brush = Brush.linearGradient(CodeTheme.colors.contactAvatar.colors),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = CodeTheme.colors.textMain,
                        modifier = Modifier.size(CodeTheme.dimens.staticGrid.x4),
                    )
                }
                repeat(2) { i ->
                    ContactAvatar(
                        contact = null,
                        modifier = Modifier
                            .zIndex((3 - i).toFloat())
                            .requiredSize(avatarSize)
                            .border(
                                width = CodeTheme.dimens.border,
                                color = CodeTheme.colors.background,
                                shape = CircleShape,
                            )
                            .clip(CircleShape),
                    )
                }
            }

            Text(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.title_addMoreContacts),
                style = CodeTheme.typography.textSmall,
                color = CodeTheme.colors.textSecondary,
            )

            Text(
                modifier = Modifier
                    .background(
                        color = Color.White,
                        shape = CodeTheme.shapes.extraSmall,
                    )
                    .padding(
                        horizontal = CodeTheme.dimens.grid.x2,
                        vertical = CodeTheme.dimens.grid.x1,
                    ),
                text = stringResource(R.string.action_add),
                style = CodeTheme.typography.textMedium,
                color = Color.Black,
            )
        }
    }
}

@Composable
private fun ContactGroupHeader(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(CodeTheme.dimens.grid.x12),
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
    lastMessagePreview: String? = null,
    unreadCount: Int = 0,
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
                .clickable(onClick = onClick)
                .padding(vertical = CodeTheme.dimens.inset)
                .padding(end = CodeTheme.dimens.inset),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (unreadCount > 0) {
                    UnreadBadge(
                        modifier = Modifier.padding(
                            start = CodeTheme.dimens.grid.x1,
                            end = CodeTheme.dimens.grid.x1,
                        ),
                        count = unreadCount
                    )
                } else {
                    Box(modifier = Modifier.requiredWidth(CodeTheme.dimens.inset))
                }
                ContactAvatar(
                    contact = contact,
                    modifier = Modifier
                        .requiredSize(CodeTheme.dimens.staticGrid.x8)
                        .clip(CircleShape),
                    includeBorder = false,
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x1),
                ) {
                    Text(
                        modifier = Modifier.weight(1f, fill = false),
                        text = contact.displayName,
                        style = CodeTheme.typography.textMedium,
                        color = CodeTheme.colors.textMain,
                    )
                    if (contact.isUnknown) {
                        Icon(
                            modifier = Modifier.size(CodeTheme.dimens.staticGrid.x3),
                            painter = painterResource(R.drawable.ic_unknown_contact),
                            contentDescription = null,
                            tint = CodeTheme.colors.textMain,
                        )
                    }
                }

                val showSubtitle = lastMessagePreview != null || !isOnFlipcash

                if (showSubtitle) {
                    Text(
                        modifier = Modifier.padding(top = CodeTheme.dimens.grid.x1),
                        text = if (isOnFlipcash && !lastMessagePreview.isNullOrEmpty()) {
                            lastMessagePreview
                        } else {
                            contact.displayNumber.ifEmpty { contact.e164 }
                        },
                        style = CodeTheme.typography.textSmall,
                        color = CodeTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
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
private fun UnreadBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(CodeTheme.dimens.grid.x2)
            .background(
                color = CodeTheme.colors.indicator,
                shape = CircleShape,
            ),
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
        add(ContactListItem.Header("Recents"))
        addAll(flipcashContacts.take(3))
        add(ContactListItem.Header("On Flipcash"))
        addAll(flipcashContacts.drop(3))
        add(ContactListItem.Header("Not On Flipcash Yet"))
        addAll(otherContacts)
    }

    ContactList(items) { }
}
