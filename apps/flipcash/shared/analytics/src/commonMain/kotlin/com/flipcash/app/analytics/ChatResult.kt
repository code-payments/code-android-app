package com.flipcash.app.analytics

import com.flipcash.services.models.AdmitLobbyMemberError
import com.flipcash.services.models.BanUserError
import com.flipcash.services.models.CreateInviteError
import com.flipcash.services.models.DenyLobbyMemberError
import com.flipcash.services.models.EditChatError
import com.flipcash.services.models.JoinChatError
import com.flipcash.services.models.LeaveChatError
import com.flipcash.services.models.MuteChatError
import com.flipcash.services.models.RevokeInviteError
import com.flipcash.services.models.SilenceUserError
import com.flipcash.services.models.StartChatError
import com.flipcash.services.models.UnmuteChatError
import com.getcode.opencode.model.core.errors.ValidationException

/**
 * The `Error` property on the group and mute events: the chat RPC's result name as the proto
 * spells it, or `Network` when the call failed in transport. Spelled out rather than taken from
 * `javaClass.simpleName`, which R8 renames in release builds.
 *
 * `Validation` is the request failing proto validation before it was sent, which has no result
 * name because the server never answered.
 */
val Throwable.chatResult: String
    get() = when (this) {
        is StartChatError.Denied, is EditChatError.Denied, is JoinChatError.Denied,
        is LeaveChatError.Denied, is MuteChatError.Denied, is UnmuteChatError.Denied,
        is CreateInviteError.Denied, is RevokeInviteError.Denied, is AdmitLobbyMemberError.Denied,
        is DenyLobbyMemberError.Denied, is SilenceUserError.Denied, is BanUserError.Denied -> "Denied"
        is EditChatError.NotFound, is JoinChatError.NotFound, is LeaveChatError.NotFound,
        is MuteChatError.NotFound, is UnmuteChatError.NotFound,
        is CreateInviteError.NotFound, is RevokeInviteError.NotFound, is AdmitLobbyMemberError.NotFound,
        is DenyLobbyMemberError.NotFound, is SilenceUserError.NotFound, is BanUserError.NotFound -> "NotFound"
        is CreateInviteError.TooManyInvites -> "TooManyInvites"
        is CreateInviteError.InvalidExpiresAt -> "InvalidExpiresAt"
        is CreateInviteError.InvalidSignature -> "InvalidSignature"
        is AdmitLobbyMemberError.NotInLobby -> "NotInLobby"
        is SilenceUserError.CannotRestrictCreator, is BanUserError.CannotRestrictCreator -> "CannotRestrictCreator"
        is StartChatError.TitleModerated, is EditChatError.TitleModerated -> "TitleModerated"
        is StartChatError.PictureBlobNotAccepted, is EditChatError.PictureBlobNotAccepted -> "PictureBlobNotAccepted"
        is StartChatError.InvalidRules -> "InvalidRules"
        is StartChatError.RulesNotSatisfied, is JoinChatError.RulesNotSatisfied -> "RulesNotSatisfied"
        is JoinChatError.Banned -> "Banned"
        is StartChatError.Unrecognized, is EditChatError.Unrecognized, is JoinChatError.Unrecognized,
        is LeaveChatError.Unrecognized, is MuteChatError.Unrecognized, is UnmuteChatError.Unrecognized,
        is CreateInviteError.Unrecognized, is RevokeInviteError.Unrecognized, is AdmitLobbyMemberError.Unrecognized,
        is DenyLobbyMemberError.Unrecognized, is SilenceUserError.Unrecognized, is BanUserError.Unrecognized -> "Unrecognized"
        is ValidationException -> "Validation"
        else -> "Network"
    }
