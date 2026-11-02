package com.flipcash.app.messenger.internal

import com.flipcash.app.messenger.internal.ChatViewModel.EditingMessage
import com.flipcash.app.messenger.internal.ChatViewModel.Event
import com.flipcash.app.messenger.internal.ChatViewModel.State
import com.flipcash.shared.chat.canReact

/** Selecting a message and acting on it: copy, edit, delete, reply, and mention suggestions. Null when [event] belongs to another area. */
internal fun reduceMessageAction(event: Event): ((State) -> State)? =
    when (event) {
        is Event.ToggleMessageSelection -> { state ->
            // By message rather than by row: a message split around its card is one
            // selection whichever of its rows was pressed.
            val alreadySelected = state.selection?.messageKey == event.bubble.messageKey
            // Narrowed again so the bar offers what is open now, not when the row was built.
            val selected = event.bubble.takeUnless { alreadySelected }?.narrowedFor(state)
            state.copy(
                selection = selected,
                reactionStripOnly = false,
                confirmingDelete = false,
            ).withQuickReactionStrip()
        }
        is Event.PresentReactionStrip -> { state ->
            val selected = event.bubble.narrowedFor(state).takeIf { it.canReact }
            if (selected == null) {
                state
            } else {
                state.copy(
                    selection = selected,
                    reactionStripOnly = true,
                    confirmingDelete = false,
                ).withQuickReactionStrip()
            }
        }
        Event.ClearMessageSelection -> { state ->
            state.copy(selection = null, confirmingDelete = false).withQuickReactionStrip()
        }
        is Event.CopyMessage -> { state ->
            state.copy(selection = null, confirmingDelete = false)
        }
        Event.ReportRequested -> { state ->
            state.copy(selection = null, confirmingDelete = false)
        }
        is Event.CopyMessageLink -> { state ->
            state.copy(selection = null, confirmingDelete = false)
        }
        Event.ShareMessageRequested -> { state ->
            state.copy(selection = null, confirmingDelete = false)
        }
        is Event.PhotoSaveResult -> { state -> state }
        is Event.EditMessage -> { state ->
            state.copy(
                selection = null,
                confirmingDelete = false,
                replyingTo = null,
                editing = EditingMessage(
                    messageId = event.messageId,
                    originalText = event.text,
                    // Starting a second edit before the first ends must not stash the
                    // first edit's text as if it were the user's draft.
                    stashedDraft = state.editing?.stashedDraft
                        ?: state.chatInputState.text.toString(),
                ),
            )
        }
        // Selection survives the confirmation sheet, but the focus does not: the sheet is
        // modal, so the transcript behind it goes uniformly dim until the sheet closes.
        is Event.DeleteMessage -> { state -> state.copy(confirmingDelete = true) }
        Event.SubmitEdit -> { state -> state }
        Event.CancelEdit -> { state -> state }
        Event.EditingEnded -> { state -> state.copy(editing = null) }
        // The strip opens on ReplyToMessage, once the citation resolves; all this does is
        // take the selection bar down so the transcript is legible while that read runs.
        is Event.ReplyRequested -> { state ->
            state.copy(selection = null, confirmingDelete = false)
        }
        is Event.ReplyToMessage -> { state ->
            state.copy(
                replyingTo = event.quote,
                selection = null,
                confirmingDelete = false,
                // Reply and edit both own the composer, so opening one closes the other.
                editing = null,
            )
        }
        Event.CancelReply -> { state -> state.copy(replyingTo = null) }
        is Event.OnMentionSuggestions -> { state -> state.copy(mentionSuggestions = event.matches) }
        // Closed at once rather than waiting for the inserted space to reach the query
        // observer, so the list doesn't linger a frame over the text it just wrote.
        is Event.PickMention -> { state -> state.copy(mentionSuggestions = emptyList()) }
        else -> null
    }
