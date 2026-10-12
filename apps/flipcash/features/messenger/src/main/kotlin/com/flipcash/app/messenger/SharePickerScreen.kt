package com.flipcash.app.messenger

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.Text
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.flipcash.shared.chat.share.MAX_SHARE_IMAGES
import com.flipcash.shared.chat.share.ShareChatStatus
import com.flipcash.shared.chat.share.ShareFailure
import com.flipcash.shared.chat.share.ShareSending
import com.flipcash.shared.chat.share.ShareSendProgress
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.flipcash.app.core.AppRoute
import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.messenger.internal.SharePickerViewModel
import com.flipcash.features.messenger.R
import com.flipcash.services.models.chat.ChatId
import com.flipcash.shared.chat.ui.ConversationReference
import com.flipcash.shared.common.ui.ContactAvatar
import com.getcode.navigation.core.LocalCodeNavigator
import com.getcode.theme.CodeTheme
import com.getcode.theme.White50
import com.getcode.ui.components.AppBarDefaults
import com.getcode.ui.components.AppBarWithTitle
import com.getcode.ui.components.SearchInput
import com.getcode.ui.components.chat.ChatInput
import com.getcode.ui.components.chat.ChatInputSubmit
import com.getcode.ui.theme.CodeCircularProgressIndicator
import com.getcode.ui.utils.rememberKeyboardController

/**
 * Where a share from another app picks its conversations and is sent, behind
 * `AppRoute.Messaging.SharePicker`. The iOS share extension's "Send to" screen: a title with a
 * close button, a search field, the chats grouped under "Suggested" and "Recent chats", and, once
 * one is picked, a bar with the shared images, a message field, who it goes to and Send (Retry
 * after a failure). Up to five chats can be picked; it closes when every one has the share.
 *
 * Differs from iOS where Android does: the search field and bar use the app's own components, the
 * Send button sits inside the message field as it does in the chat composer, and the selection
 * indicator is the app's check mark. Failure lines, the image limit notice and "Sending X of Y"
 * are as on iOS.
 */
@Composable
fun SharePickerScreen() {
    val viewModel = hiltViewModel<SharePickerViewModel>()
    val allChats by viewModel.chats.collectAsStateWithLifecycle()
    val chats by viewModel.visibleChats.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val sending by viewModel.sending.collectAsStateWithLifecycle()
    val done by viewModel.done.collectAsStateWithLifecycle()
    val limitHits by viewModel.limitHits.collectAsStateWithLifecycle()
    val navigator = LocalCodeNavigator.current
    val haptics = LocalHapticFeedback.current
    val query = viewModel.queryState.text.toString().trim()

    LaunchedEffect(done, viewModel.hasShare) {
        if (done || !viewModel.hasShare) navigator.pop()
    }
    LaunchedEffect(limitHits) {
        if (limitHits > 0) haptics.performHapticFeedback(HapticFeedbackType.Reject)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CodeTheme.colors.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
        AppBarWithTitle(
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.title_shareToChat),
            titleAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(16.dp),
            endContent = { AppBarDefaults.Close(onClick = { navigator.pop() }) },
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val loaded = chats
            when {
                loaded == null -> {
                    // No rows until the list is known, so an early tap can't land on a row that
                    // is about to be replaced.
                    CodeCircularProgressIndicator(
                        strokeWidth = CodeTheme.dimens.thickBorder,
                        color = CodeTheme.colors.textSecondary,
                        modifier = Modifier.align(Alignment.Center).requiredSize(CodeTheme.dimens.staticGrid.x8),
                    )
                }
                allChats.orEmpty().isEmpty() -> ShareMessage(
                    title = stringResource(R.string.title_shareNoChats),
                    detail = stringResource(R.string.subtitle_shareNoChats),
                )
                else -> {
                    // Suggested chat first and not repeated below; a search is one flat list.
                    val suggested = viewModel.suggested
                        ?.takeIf { query.isEmpty() }
                        ?.let { id -> loaded.firstOrNull { it.chatId == id } }
                    val recent = if (suggested == null) loaded else loaded.filter { it.chatId != suggested.chatId }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item(key = "search") {
                            SearchInput(
                                state = viewModel.queryState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = ListInset)
                                    .padding(top = 20.dp, bottom = CodeTheme.dimens.grid.x2),
                            )
                        }
                        if (suggested != null) {
                            item(key = "suggestedHeader") { SectionHeader(stringResource(R.string.label_suggested)) }
                            item(key = "suggested:${suggested.chatId}") {
                                ShareTargetRow(suggested, suggested.chatId in selected) { viewModel.toggle(suggested.chatId) }
                            }
                        }
                        if (loaded.isEmpty()) {
                            item(key = "empty") {
                                Text(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = ListInset).padding(top = 24.dp),
                                    text = stringResource(R.string.label_shareNoMatches, query),
                                    style = CodeTheme.typography.textSmall,
                                    color = White50,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        } else if (recent.isNotEmpty()) {
                            item(key = "header") {
                                SectionHeader(
                                    stringResource(if (query.isEmpty()) R.string.label_recentChats else R.string.label_chats)
                                )
                            }
                            items(recent, key = { it.chatId.toString() }) { chat ->
                                ShareTargetRow(chat, chat.chatId in selected) { viewModel.toggle(chat.chatId) }
                            }
                        }
                    }
                }
            }
        }

        // Slides up once a chat is picked, as the iOS compose bar does.
        val names = allChats.orEmpty().associate { it.chatId to it.name.orEmpty() }
        AnimatedVisibility(
            visible = selected.isNotEmpty(),
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            val isSending = sending != null
            val hasMessage = viewModel.messageState.text.isNotBlank()
            ComposeBar(
                recipients = selected.mapNotNull { names[it] }.joinToString(", "),
                images = viewModel.images.map { it.toString() },
                droppedImages = viewModel.droppedImageCount,
                messageState = viewModel.messageState,
                sending = sending,
                progress = progress,
                names = names,
                canSend = viewModel.canSend(selected, isSending, progress, hasMessage),
                onSend = { viewModel.send() },
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Column(modifier = Modifier.padding(horizontal = ListInset)) {
        Text(
            modifier = Modifier.padding(vertical = 12.dp),
            text = title,
            style = CodeTheme.typography.textSmall,
            color = White50,
        )
        HorizontalDivider(color = CodeTheme.colors.divider)
    }
}

@Composable
private fun ShareMessage(title: String, detail: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = title,
            style = CodeTheme.typography.textMedium,
            color = CodeTheme.colors.textMain,
            textAlign = TextAlign.Center,
        )
        Text(
            text = detail,
            style = CodeTheme.typography.textSmall,
            color = White50,
            textAlign = TextAlign.Center,
        )
    }
}

/** One chat, picked or not: avatar, name and the same check mark the invite sheet's rows use. */
@Composable
private fun ShareTargetRow(chat: ConversationReference, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = selected, role = Role.RadioButton, onValueChange = { onClick() })
            .padding(horizontal = ListInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ContactAvatar(
            image = chat.image,
            displayName = chat.name.orEmpty(),
            access = chat.avatarAccess,
            modifier = Modifier
                .requiredSize(CodeTheme.dimens.staticGrid.x6)
                .clip(CircleShape),
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.padding(vertical = CodeTheme.dimens.grid.x4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = chat.name.orEmpty(),
                    style = CodeTheme.typography.textMedium,
                    color = CodeTheme.colors.textMain,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Image(
                    painter = painterResource(if (selected) R.drawable.ic_checked else R.drawable.ic_unchecked),
                    contentDescription = null,
                )
            }
            HorizontalDivider(color = CodeTheme.colors.divider)
        }
    }
}

/**
 * Notices, then the field with the shared images stacked in it and the action, then who it goes
 * to. The field is the chat composer's own so typing here feels the same.
 */
@Composable
private fun ComposeBar(
    recipients: String,
    images: List<String>,
    droppedImages: Int,
    messageState: TextFieldState,
    sending: ShareSending?,
    progress: ShareSendProgress?,
    names: Map<ChatId, String>,
    canSend: Boolean,
    onSend: () -> Unit,
) {
    val failed = progress?.takeIf { sending == null }?.failed.orEmpty()
    val isRetry = failed.isNotEmpty()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CodeTheme.colors.surface)
            .navigationBarsPadding()
            .padding(horizontal = ListInset)
            .padding(top = 12.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (droppedImages > 0) {
            Notice(stringResource(R.string.label_shareImageLimit, MAX_SHARE_IMAGES))
        }
        if (sending != null && sending.total > 1) {
            Notice(stringResource(R.string.label_shareSendingProgress, sending.position, sending.total))
        }
        failed.forEach { id ->
            val status = progress?.statuses?.get(id) as? ShareChatStatus.Failed ?: return@forEach
            Notice(
                text = stringResource(
                    R.string.label_shareFailure,
                    names[id].orEmpty(),
                    stringResource(status.failure.message()),
                ),
                color = CodeTheme.colors.error,
            )
        }
        ChatInput(
            hint = stringResource(R.string.hint_addAMessage),
            state = messageState,
            enabled = sending == null,
            hasAttachments = images.isNotEmpty(),
            sendEnabled = canSend,
            submit = ChatInputSubmit.Action(
                label = stringResource(
                    if (isRetry) com.flipcash.core.R.string.action_retry else com.flipcash.core.R.string.action_send
                ),
                busy = sending != null,
                perform = { if (canSend) onSend() },
            ),
            header = if (images.isEmpty()) null else ({ ThumbnailStack(images) }),
        )
        Text(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            text = recipients,
            style = CodeTheme.typography.textSmall,
            color = White50,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Notice(text: String, color: Color = White50) {
    Text(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        text = text,
        style = CodeTheme.typography.textSmall,
        color = color,
    )
}

private fun ShareFailure.message(): Int = when (this) {
    ShareFailure.Send -> R.string.error_shareSendFailed
    ShareFailure.ImageRejected -> R.string.error_shareImageRejected
    ShareFailure.ImageUnprepared -> R.string.error_shareImageUnprepared
}

/** Up to three of the images, fanned, with the count over them when there are more than one. */
@Composable
private fun ThumbnailStack(images: List<String>) {
    val description = stringResource(R.string.label_shareAttachedCount, images.size)
    Box(
        modifier = Modifier
            .padding(start = 12.dp, top = 12.dp, end = 12.dp)
            .size(width = 54.dp, height = 48.dp)
            .semantics { contentDescription = description },
    ) {
        images.take(3).withIndex().toList().asReversed().forEach { (index, uri) ->
            AsyncImage(
                model = uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .offset(x = (index * 5).dp, y = (3 - index * 3).dp)
                    .size(44.dp)
                    .clip(ThumbShape)
                    .border(1.5.dp, CodeTheme.colors.background, ThumbShape),
            )
        }
        if (images.size > 1) {
            Text(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 5.dp),
                text = images.size.toString(),
                style = CodeTheme.typography.textSmall,
                color = Color.White,
            )
        }
    }
}

private val ThumbShape = RoundedCornerShape(8.dp)
private val ListInset = 20.dp
