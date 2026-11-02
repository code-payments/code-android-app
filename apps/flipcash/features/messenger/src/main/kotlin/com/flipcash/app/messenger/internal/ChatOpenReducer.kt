package com.flipcash.app.messenger.internal

import com.flipcash.app.core.chat.ChatIdentifier
import com.flipcash.app.core.chat.ChatParticipant
import com.flipcash.app.messenger.internal.ChatViewModel.Companion.ROUTED_TO_PROFILE
import com.flipcash.app.messenger.internal.ChatViewModel.Event
import com.flipcash.app.messenger.internal.ChatViewModel.ResolveState
import com.flipcash.app.messenger.internal.ChatViewModel.State
import com.flipcash.services.models.chat.ChatType
import com.flipcash.shared.chat.groupAccess
import com.flipcash.shared.chat.speakerBlock
import com.getcode.view.LoadingSuccessState

/** Opening a chat and what it resolves: subject, group, access, lobby, members, join requests, and the chat-wide inputs (token, limits, policy). Only the events [stateReducer] routes here arrive; any other is a routing bug. */
internal fun reduceChatOpen(event: Event): (State) -> State =
    when (event) {
        is Event.OnChatOpened -> { state ->
            when (val id = event.identifier) {
                is ChatIdentifier.ByContact ->
                    state.copy(
                        subject = ChatSubject.Contact(ChatParticipant.Contact(id.contact)),
                        chatType = ChatType.CONTACT_DM,
                    )
                // Known without a lookup, so the transcript, boundary and the rest keyed
                // on it start now rather than once the open handler gets to ChatFound.
                is ChatIdentifier.ByChatId -> state.copy(chatId = id.chatId)
                is ChatIdentifier.ByUser -> error(ROUTED_TO_PROFILE)
            }
        }
        is Event.OnContactFound -> { state ->
            state.copy(
                subject = ChatSubject.Contact(ChatParticipant.Contact(event.contact)),
                chatType = ChatType.CONTACT_DM,
            )
        }
        // "No device contact" is also true of every group, so a group that has already
        // resolved keeps its type.
        Event.OnTipDmDetected -> { state ->
            if (state.subject is ChatSubject.Group) state
            else state.copy(chatType = ChatType.TIP_DM)
        }
        is Event.OnWrongKey -> { state -> state.copy(wrongKey = event.wrongKey) }
        is Event.OnGroupResolved -> { state ->
            val metadata = event.membership.metadata
            val previous = state.subject as? ChatSubject.Group
            // The access was decided for the old membership and rules. Kept across a change
            // to either, a stale Eligible would unblur a group whose rules just tightened
            // until the balance flow caught up, so it goes back to unknown instead.
            val accessStale = previous == null ||
                previous.isMember != event.membership.isMember ||
                previous.rules != metadata.rules
            state.copy(
                groupAccess = if (accessStale) null else state.groupAccess,
                subject = ChatSubject.Group(
                    chatId = metadata.chatId,
                    groupTitle = metadata.title,
                    picture = metadata.picture,
                    memberCount = metadata.rosterSummary.memberCount,
                    rules = metadata.rules,
                    isMember = event.membership.isMember,
                    creator = metadata.creator,
                    description = metadata.description,
                    coverPicture = metadata.coverPicture,
                    isPrivate = metadata.isPrivate,
                ),
                chatType = ChatType.GROUP,
                resolveState = ResolveState.Resolved,
            )
        }
        is Event.OnRuleCurrencyResolved ->
            { state -> state.copy(ruleCurrency = event.currency) }
        is Event.OnGroupAccessResolved -> { state -> state.copy(groupAccess = event.access) }
        is Event.OnSpeakerBlockResolved -> { state -> state.copy(
            speakerBlock = event.block?.requirement,
            speakerBlocksReactions = event.block?.reactionsBlocked == true,
            speakerBlockResolved = true,
        ) }
        Event.JoinChat -> { state ->
            state.copy(joinProgress = LoadingSuccessState(loading = true))
        }
        is Event.JoinStateUpdated -> { state ->
            state.copy(
                joinProgress = LoadingSuccessState(
                    event.loading,
                    event.success,
                )
            )
        }
        is Event.OnEncryptionResolved -> { state -> state.copy(isEncrypted = event.isEncrypted) }

        is Event.OnViewerStateResolved -> { state ->
            state.copy(viewerState = event.viewerState, silenceClock = kotlin.time.Clock.System.now())
        }
        Event.SilenceLapsed -> { state -> state.copy(silenceClock = kotlin.time.Clock.System.now()) }
        Event.SilenceLifted -> { state -> state }
        Event.CopyInviteLink,
        is Event.InviteSheetOpened,
        Event.InviteLinkShared,
        is Event.GateFundingTapped,
        Event.GroupInfoOpened,
        Event.InviteCardFollowed,
        Event.LeaveChat, Event.GroupProfileOpened,
        Event.RequestToJoin, Event.CancelJoinRequest,
        Event.JoinRequestsOpened, is Event.ApproveJoinRequest, is Event.DenyJoinRequest -> { state -> state }
        is Event.OnJoinRequestsResolved -> { state ->
            state.copy(isLobbyHost = event.isHost, joinRequests = event.members)
        }
        is Event.JoinRequestCallChanged -> { state ->
            val calls = state.joinRequestCalls.toMutableMap()
            val call = event.call
            if (call == null) calls.remove(event.userId) else calls[event.userId] = call
            state.copy(joinRequestCalls = calls)
        }
        is Event.LobbyCallChanged -> { state -> state.copy(lobbyCall = event.call) }
        is Event.OnLobbyRequestResolved -> { state -> state.copy(lobbyRequestedAt = event.requestedAt) }
        is Event.OnHostNameResolved -> { state -> state.copy(hostName = event.name) }
        is Event.OnChattersLoaded -> { state -> state.copy(chatters = event.chatters) }
        is Event.OnMembersResolved -> { state -> state.copy(members = event.members) }
        is Event.OnKnownMembersResolved -> { state -> state.copy(knownMembers = event.members) }
        Event.MembersOpened -> { state -> state }
        is Event.OnProfileStandingResolved -> { state -> state.copy(profileStanding = event.standing) }
        is Event.OnRuleTokensResolved -> { state -> state.copy(ruleTokens = event.tokens) }
        Event.LeaveConfirmed -> { state -> state.copy(leaving = true) }
        Event.LeftChat, Event.LeaveFailed -> { state -> state.copy(leaving = false) }
        Event.ChatNotFound -> { state -> state.copy(chatNotFound = true) }
        is Event.OnTipUserResolved -> { state ->
            // A device contact, once matched, wins over the server profile (it carries the
            // phone number and the user's own naming). Otherwise this is a tip DM: adopt the
            // profile identity and mark the recipient resolved so the send can proceed (the
            // tip user is known to exist; the tip send resolves their address at send time).
            // A group has no counterparty: the member this names is just someone in the room.
            if (state.subject is ChatSubject.Contact || state.subject is ChatSubject.Group) state
            else state.copy(
                subject = ChatSubject.TipUser(
                    ChatParticipant.TipUser(event.userId, event.profile)
                ),
                chatType = ChatType.TIP_DM,
                resolveState = ResolveState.Resolved,
            )
        }
        is Event.OnCurrencySymbolUpdated -> { state -> state.copy(cashSymbol = event.symbol) }
        is Event.ChatFound -> { state -> state.copy(chatId = event.chatId) }
        is Event.TokenUpdated -> { state -> state.copy(token = event.token) }
        is Event.LimitsChanged -> { state -> state.copy(limits = event.limits) }
        is Event.AdvanceReadPointer -> { state -> state }
        is Event.ChatDeactivated -> { state -> state.copy(isAnonymous = event.isReadOnly) }
        is Event.MessagePolicyChanged -> { state -> state.copy(messagePolicy = event.policy) }
        else -> error("reduceChatOpen does not handle $event")
    }
