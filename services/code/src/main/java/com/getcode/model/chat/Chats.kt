package com.getcode.model.chat

typealias ModelIntentId = com.codeinc.gen.common.v1.Model.IntentId

typealias ChatGrpcV1 = com.codeinc.gen.chat.v1.ChatGrpc
typealias ChatGrpcV2 = com.codeinc.gen.chat.v2.ChatGrpc

typealias ChatIdV1 = com.codeinc.gen.chat.v1.ChatService.ChatId
typealias ChatIdV2 = com.codeinc.gen.common.v1.Model.ChatId

typealias MessageContentV1 = com.codeinc.gen.chat.v1.ChatService.Content
typealias MessageContentV2 = com.codeinc.gen.chat.v2.ChatService.Content

typealias VerbV1 = com.codeinc.gen.chat.v1.ChatService.ExchangeDataContent.Verb
typealias VerbV2 = com.codeinc.gen.chat.v2.ChatService.ExchangeDataContent.Verb

typealias ChatCursorV1 = com.codeinc.gen.chat.v1.ChatService.Cursor
typealias ChatCursorV2 = com.codeinc.gen.chat.v2.ChatService.Cursor

typealias GetMessagesDirectionV1 = com.codeinc.gen.chat.v1.ChatService.GetMessagesRequest.Direction
typealias GetMessagesDirectionV2 = com.codeinc.gen.chat.v2.ChatService.GetMessagesRequest.Direction

typealias PointerV1 = com.codeinc.gen.chat.v1.ChatService.Pointer
typealias PointerV2 = com.codeinc.gen.chat.v2.ChatService.Pointer

typealias StartChatRequest = com.codeinc.gen.chat.v2.ChatService.StartChatRequest
typealias StartChatResponse = com.codeinc.gen.chat.v2.ChatService.StartChatResponse

typealias GetChatsRequestV1 = com.codeinc.gen.chat.v1.ChatService.GetChatsRequest
typealias GetChatsRequestV2 = com.codeinc.gen.chat.v2.ChatService.GetChatsRequest
typealias GetChatsResponseV1 = com.codeinc.gen.chat.v1.ChatService.GetChatsResponse
typealias GetChatsResponseV2 = com.codeinc.gen.chat.v2.ChatService.GetChatsResponse

typealias GetMessagesRequestV1 = com.codeinc.gen.chat.v1.ChatService.GetMessagesRequest
typealias GetMessagesRequestV2 = com.codeinc.gen.chat.v2.ChatService.GetMessagesRequest
typealias GetMessagesResponseV1 = com.codeinc.gen.chat.v1.ChatService.GetMessagesResponse
typealias GetMessagesResponseV2 = com.codeinc.gen.chat.v2.ChatService.GetMessagesResponse

typealias AdvancePointerRequestV1 = com.codeinc.gen.chat.v1.ChatService.AdvancePointerRequest
typealias AdvancePointerRequestV2 = com.codeinc.gen.chat.v2.ChatService.AdvancePointerRequest
typealias AdvancePointerResponseV1 = com.codeinc.gen.chat.v1.ChatService.AdvancePointerResponse
typealias AdvancePointerResponseV2 = com.codeinc.gen.chat.v2.ChatService.AdvancePointerResponse


typealias SetMuteStateRequestV1 = com.codeinc.gen.chat.v1.ChatService.SetMuteStateRequest
typealias SetMuteStateRequestV2 = com.codeinc.gen.chat.v2.ChatService.SetMuteStateRequest
typealias SetMuteStateResponseV1 = com.codeinc.gen.chat.v1.ChatService.SetMuteStateResponse
typealias SetMuteStateResponseV2 = com.codeinc.gen.chat.v2.ChatService.SetMuteStateResponse


typealias SetSubscriptionStateRequestV1 = com.codeinc.gen.chat.v1.ChatService.SetSubscriptionStateRequest
typealias SetSubscriptionStateResponseV1 = com.codeinc.gen.chat.v1.ChatService.SetSubscriptionStateResponse

/**
 * Code reference to a V1 [Chat] that serves as a collection of messages associated
 * with a notification type (Tips, Cash Payments, Web Payments, etc.)
 */
typealias NotificationCollectionEntity = Chat

/**
 * Code reference to a V2 [Chat] that is a full end-to-end chat that suports
 * peer-to-peer messaging between users.
 */
typealias ConversationEntity = Chat
