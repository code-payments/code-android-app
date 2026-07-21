package com.flipcash.app.directsend.internal.screens.components

import android.text.format.DateFormat
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.flipcash.app.contacts.ui.ContactAvatar
import com.flipcash.app.core.contacts.DeviceContact
import com.flipcash.app.directsend.internal.ContactListItem
import com.flipcash.app.permissions.ContactAccessHandle
import com.flipcash.app.theme.FlipcashThemeWrapper
import com.flipcash.features.directsend.R
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.ui.ConversationReference
import com.getcode.theme.CodeTheme
import com.getcode.theme.White10
import com.getcode.theme.extraSmall
import com.getcode.ui.core.verticalScrollStateGradient
import com.getcode.util.formatLocalized
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@Composable
internal fun ContactList(
    items: List<ContactListItem>,
    searchState: TextFieldState,
    listState: LazyListState,
    accessHandle: ContactAccessHandle,
    modifier: Modifier = Modifier,
    isPickerMode: Boolean = false,
    onItemClick: (ContactListItem.ContactRow) -> Unit = {},
    onItemDismissed: (ContactListItem.ContactRow) -> Unit = {},
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .testTag("send_contact_list")
            .verticalScrollStateGradient(
                scrollState = listState,
                color = CodeTheme.colors.background,
                isLongGradient = true,
            )
            .then(modifier),
        state = listState,
        contentPadding = PaddingValues(bottom = CodeTheme.dimens.grid.x3),
    ) {
        if (items.isEmpty() && searchState.text.isNotEmpty()) {
            item(key = "empty_search") {
                EmptySearchState(
                    searchQuery = searchState.text.toString(),
                    modifier = Modifier.fillParentMaxSize(),
                )
            }
        }

        if (items.isEmpty() && searchState.text.isEmpty()) {
            item(key = "empty_contacts") {
                EmptyContactsState(
                    modifier = Modifier.fillParentMaxSize(),
                )
            }
        }

        itemsIndexed(
            items = items,
            key = { _, item ->
                when (item) {
                    is ContactListItem.Header -> item.title
                    is ContactListItem.ContactRow -> item.conversation?.chatId?.toString()
                        ?: item.contact.e164
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
                        lastMessagePreview = item.conversation?.lastMessagePreview,
                        unreadCount = item.conversation?.unreadCount ?: 0,
                        isTyping = item.conversation?.isTyping == true,
                        showDivider = !isLastInSection,
                        lastActivity = item.lastActivity,
                    ) {
                        onItemClick(item)
                    }
                }
            }
        }

        if (isPickerMode) {
            item {
                PickerModeHeader(
                    onAddMoreContacts = { accessHandle.launch() },
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
    lastActivity: Instant? = null,
    unreadCount: Int = 0,
    isTyping: Boolean = false,
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
                .testTag("send_contact_row")
                .clickable(onClick = onClick)
                .padding(vertical = CodeTheme.dimens.inset)
                .padding(end = CodeTheme.dimens.inset),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x3),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Spacer used by the unread indicator that we moved
                // left for spacing compatibility and easy add back later
                Box(modifier = Modifier.requiredWidth(CodeTheme.dimens.inset))
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
                        modifier = Modifier.weight(1f),
                        text = contact.displayName,
                        style = CodeTheme.typography.textMedium,
                        color = CodeTheme.colors.textMain,
                    )

                    when {
                        contact.isUnknown -> {
                            Text(
                                modifier = Modifier.background(
                                    color = CodeTheme.colors.surfaceVariant,
                                    shape = CircleShape,
                                ).padding(
                                    vertical = CodeTheme.dimens.grid.x1,
                                    horizontal = CodeTheme.dimens.grid.x2,
                                ),
                                text = stringResource(R.string.label_unknownContact),
                                style = CodeTheme.typography.caption.copy(fontSize = 10.sp, lineHeight = 10.sp),
                                color = CodeTheme.colors.textSecondary,
                            )
                        }
                        lastActivity != null -> {
                            val activityTextColor by animateColorAsState(
                                if (unreadCount > 0) CodeTheme.colors.indicator else CodeTheme.colors.textSecondary
                            )
                            Text(
                                text = formatLastActivity(lastActivity),
                                style = CodeTheme.typography.caption,
                                color = activityTextColor,
                            )
                        }
                    }

                    if (unreadCount > 0) {
                        UnreadBadge(
                            modifier = Modifier.padding(
                                start = CodeTheme.dimens.grid.x1,
                                end = CodeTheme.dimens.grid.x1,
                            ),
                            count = unreadCount
                        )
                    } else if (isOnFlipcash) {
                        Icon(
                            modifier = Modifier.scale(0.6f),
                            painter = painterResource(id = R.drawable.ic_chevron_right),
                            contentDescription = null,
                            tint = CodeTheme.colors.textSecondary,
                        )
                    }
                }

                when {
                    isTyping -> Text(
                        text = stringResource(R.string.label_isTyping),
                        style = CodeTheme.typography.textSmall,
                        color = CodeTheme.colors.textSecondary,
                    )

                    else -> Text(
                        text = if (isOnFlipcash && !lastMessagePreview.isNullOrEmpty()) {
                            lastMessagePreview
                        } else if (isOnFlipcash) {
                            "${contact.displayName} joined Flipcash"
                        } else {
                            contact.displayNumber
                        },
                        style = CodeTheme.typography.textSmall,
                        color = CodeTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }

            if (!isOnFlipcash) {
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
private fun formatLastActivity(instant: Instant): String {
    val context = LocalContext.current
    val is24Hour = DateFormat.is24HourFormat(context)
    val tz = TimeZone.currentSystemDefault()
    val todayDate = Clock.System.now().toLocalDateTime(tz).date
    val messageDate = instant.toLocalDateTime(tz).date
    val dayDiff = todayDate.toEpochDays() - messageDate.toEpochDays()

    val time = instant.formatLocalized("h:mm a", is24Hour = is24Hour, if24Hour = "H:mm")

    return when {
        dayDiff == 0L -> time
        dayDiff == 1L -> stringResource(R.string.label_chatReceipt_yesterday)
        dayDiff in 2L..6L -> instant.formatLocalized("EEEE")
        messageDate.year == todayDate.year -> instant.formatLocalized("MMM d")
        else -> instant.formatLocalized("MMM d, yyyy")
    }
}

@Preview
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun ContactListPreview() {
    val fakeNames = listOf(
        "Alice Anderson", "Bob Baker", "Charlie Chen", "Dana Davis",
        "Eli Evans", "Fiona Fisher", "George Garcia", "Hannah Hill",
        "Isaac Ingram", "Julia Jones", "Kevin Kim", "Lenny Johnson",
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

    val now = Clock.System.now()
    val recents = listOf(
        // Unread conversation with a message preview
        ContactListItem.ContactRow(
            contact = flipcashContacts[0].contact,
            isOnFlipcash = true,
            lastActivity = now - 5.minutes,
            conversation = ConversationReference(
                chatId = ChatId(byteArrayOf(1)),
                lastMessagePreview = "Sent you $10.00",
                unreadCount = 2,
            ),
        ),
        // Contact is currently typing
        ContactListItem.ContactRow(
            contact = flipcashContacts[1].contact,
            isOnFlipcash = true,
            lastActivity = now - 1.hours,
            conversation = ConversationReference(
                chatId = ChatId(byteArrayOf(2)),
                lastMessagePreview = "See you soon",
                isTyping = true,
            ),
        ),
        // Read conversation from yesterday
        ContactListItem.ContactRow(
            contact = flipcashContacts[2].contact,
            isOnFlipcash = true,
            lastActivity = now - 26.hours,
            conversation = ConversationReference(
                chatId = ChatId(byteArrayOf(3)),
                lastMessagePreview = "You: Thanks!",
            ),
        ),
    )

    val items = buildList {
        addAll(recents)
        // On-Flipcash contacts without a chat still rank by joinedAt,
        // so they carry a lastActivity timestamp too.
        addAll(
            flipcashContacts.drop(3).mapIndexed { i, row ->
                row.copy(lastActivity = now - (i + 2).days)
            }
        )
        add(ContactListItem.Header("Not On Flipcash Yet"))
        addAll(otherContacts)
    }

    ContactList(
        items = items,
        searchState = TextFieldState(),
        listState = rememberLazyListState(),
        accessHandle = ContactAccessHandle(launch = {}),
    )
}

@Preview(heightDp = 1400)
@PreviewWrapper(FlipcashThemeWrapper::class)
@Composable
private fun ContactRowItemStatesPreview() {
    val now = Clock.System.now()
    val known = DeviceContact(
        e164 = "+15550001234",
        androidContactId = 1L,
        displayName = "Bob Baker",
        photoUri = null,
        displayNumber = "(555) 000-1234",
    )
    val unknown = DeviceContact.unknownContact(
        e164 = "+15559998888",
        displayName = "+1 (555) 999-8888",
        displayNumber = "+1 (555) 999-8888",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CodeTheme.colors.background)
            .verticalScroll(rememberScrollState()),
    ) {
        // On Flipcash · active chat · read (message preview, timestamp, chevron)
        StateLabel("On Flipcash · active chat · read")
        ContactRowItem(
            contact = known,
            isOnFlipcash = true,
            lastMessagePreview = "See you tomorrow!",
            lastActivity = now - 30.minutes,
        ) {}

        // On Flipcash · active chat · unread (badge + indicator-colored timestamp)
        StateLabel("On Flipcash · active chat · unread")
        ContactRowItem(
            contact = known,
            isOnFlipcash = true,
            lastMessagePreview = "Sent you $5.00",
            lastActivity = now - 5.minutes,
            unreadCount = 3,
        ) {}

        // On Flipcash · typing (subtitle overridden by "is typing…")
        StateLabel("On Flipcash · typing")
        ContactRowItem(
            contact = known,
            isOnFlipcash = true,
            lastMessagePreview = "Old message",
            lastActivity = now - 2.hours,
            isTyping = true,
        ) {}

        // On Flipcash · typing + unread
        StateLabel("On Flipcash · typing · unread")
        ContactRowItem(
            contact = known,
            isOnFlipcash = true,
            lastMessagePreview = "Old message",
            lastActivity = now - 2.hours,
            unreadCount = 1,
            isTyping = true,
        ) {}

        // On Flipcash · no chat yet (still shows joinedAt timestamp, no subtitle)
        StateLabel("On Flipcash · no chat yet")
        ContactRowItem(
            contact = known,
            isOnFlipcash = true,
            lastActivity = now - 3.days,
        ) {}

        // On Flipcash · long preview (single line, ellipsized)
        StateLabel("On Flipcash · long preview (ellipsized)")
        ContactRowItem(
            contact = known,
            isOnFlipcash = true,
            lastMessagePreview = "This is a really long message preview that should be truncated with an ellipsis when it runs out of room",
            lastActivity = now - 26.hours,
        ) {}

        // On Flipcash · older activity (date formatting instead of time)
        StateLabel("On Flipcash · older activity")
        ContactRowItem(
            contact = known,
            isOnFlipcash = true,
            lastMessagePreview = "Thanks!",
            lastActivity = now - 4.days,
        ) {}

        // On Flipcash · unknown contact ("Unknown" label replaces timestamp)
        StateLabel("On Flipcash · unknown contact")
        ContactRowItem(
            contact = unknown,
            isOnFlipcash = true,
            lastMessagePreview = "Hey, is this you?",
            lastActivity = now - 10.minutes,
        ) {}

        // Not on Flipcash · invite (phone number subtitle + Invite chip)
        StateLabel("Not on Flipcash · invite")
        ContactRowItem(
            contact = known,
            isOnFlipcash = false,
        ) {}
    }
}

@Composable
private fun StateLabel(text: String) {
    Text(
        modifier = Modifier.padding(
            start = CodeTheme.dimens.inset,
            top = CodeTheme.dimens.grid.x3,
            bottom = CodeTheme.dimens.grid.x1,
        ),
        text = text,
        style = CodeTheme.typography.caption,
        color = CodeTheme.colors.textSecondary,
    )
}