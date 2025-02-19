package xyz.flipchat.app.features.chat.info

import android.content.Intent
import androidx.lifecycle.viewModelScope
import com.getcode.manager.BottomBarManager
import com.getcode.manager.TopBarManager
import com.getcode.model.ID
import com.getcode.model.Kin
import com.getcode.model.chat.LinkedSocialProfile
import com.getcode.model.chat.MinimalMember
import com.getcode.model.social.user.SocialProfile
import com.getcode.model.social.user.XExtraData
import com.getcode.navigation.RoomInfoArgs
import com.getcode.solana.keys.PublicKey
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
import com.getcode.view.LoadingSuccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.flipchat.app.R
import xyz.flipchat.app.beta.Lab
import xyz.flipchat.app.beta.Labs
import xyz.flipchat.app.data.RoomInfo
import xyz.flipchat.app.features.chat.conversation.ConversationViewModel
import xyz.flipchat.app.features.chat.conversation.ConversationViewModel.Event
import xyz.flipchat.app.features.login.register.onResult
import xyz.flipchat.app.util.IntentUtils
import xyz.flipchat.chat.RoomController
import xyz.flipchat.controllers.ChatsController
import xyz.flipchat.services.domain.model.profile.toLinked
import xyz.flipchat.services.extensions.titleOrFallback
import xyz.flipchat.services.internal.data.mapper.nullIfEmpty
import xyz.flipchat.services.user.UserManager
import javax.inject.Inject

sealed interface MemberType {
    data object Speaker : MemberType
    data object Listener : MemberType
}

@HiltViewModel
class ChatInfoViewModel @Inject constructor(
    private val labs: Labs,
    private val roomController: RoomController,
    private val chatsController: ChatsController,
    private val resources: ResourceHelper,
    private val userManager: UserManager,
) : BaseViewModel2<ChatInfoViewModel.State, ChatInfoViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {

    data class State(
        val isPreview: Boolean = false,
        val isHost: Boolean = false,
        val isMember: Boolean = false,
        val paymentDestination: PublicKey? = null,
        val roomNameChangesEnabled: Boolean = false,
        val isOpen: Boolean = false,
        val roomInfo: RoomInfo = RoomInfo(),
        val joining: LoadingSuccessState = LoadingSuccessState(),
        val leaving: LoadingSuccessState = LoadingSuccessState(),
        val members: Map<MemberType, List<MinimalMember>> = emptyMap()
    )

    sealed interface Event {
        // region state updates
        data class OnRoomNameChangesEnabled(val enabled: Boolean) : Event
        data class OnHostStatusChanged(val isHost: Boolean) : Event
        data class OnRoomOpenStateChanged(val isOpen: Boolean) : Event
        data class OnDestinationChanged(val destination: PublicKey) : Event
        data class OnInfoChanged(val args: RoomInfoArgs, val isPreview: Boolean) : Event
        data class OnMembersUpdated(val members: List<MinimalMember>) : Event
        // endregion state updates

        // region action/reaction
        data class OnChangeMessageFee(val roomId: ID) : Event
        data class OnFeeChanged(val cover: Kin) : Event

        data class OnChangeName(val id: ID, val title: String) : Event
        data class OnNameChanged(val name: String) : Event

        data object OnShareRoomClicked : Event
        data class ShareRoom(val intent: Intent) : Event

        data object OnListenToClicked : Event
        data class OnJoiningStateChanged(val joining: Boolean, val joined: Boolean = false) : Event
        data class OnBecameMember(val roomId: ID) : Event

        data object OnOpenStateChangedRequested : Event
        data class OnOpenRoom(val conversationId: ID) : Event
        data class OnCloseRoom(val conversationId: ID) : Event

        data class PromoteRequested(val member: MinimalMember) : Event
        data class PromoteUser(val conversationId: ID, val userId: ID) : Event
        data class OnUserPromoted(val id: ID) : Event
        data class DemoteRequested(val member: MinimalMember) : Event
        data class DemoteUser(val conversationId: ID, val userId: ID) : Event
        data class OnUserDemoted(val id: ID) : Event

        data object LeaveRoom : Event
        data class OnLeavingStateChanged(val leaving: Boolean, val left: Boolean = false) : Event
        data object OnLeaveRoomConfirmed : Event
        // endregion action/reaction

        data object OnLeftRoom : Event
    }

    init {
        eventFlow
            .filterIsInstance<Event.OnInfoChanged>()
            .map { it.args.ownerId }
            .map { hostId -> userManager.userId == hostId }
            .onEach { isHost ->
                dispatchEvent(Event.OnHostStatusChanged(isHost))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnInfoChanged>()
            .mapNotNull { it.args }
            .onEach { args ->
                val showConnectedSocials = labs.get(Lab.ShowConnectedSocials)
                val exists = roomController.getConversation(args.roomId.orEmpty()) != null
                if (!exists) {
                    chatsController.lookupRoom(args.roomNumber)
                        .onSuccess { (room, members) ->
                            dispatchEvent(Event.OnRoomOpenStateChanged(room.isOpen))
                            dispatchEvent(Event.OnNameChanged(room.titleOrFallback(resources)))
                            dispatchEvent(
                                Event.OnMembersUpdated(
                                    members.map { m ->
                                        MinimalMember(
                                            id = m.id,
                                            name = m.identity?.displayName.nullIfEmpty(),
                                            profileImageUrl = m.identity?.imageUrl.nullIfEmpty(),
                                            canSpeak = m.isModerator || !m.isSpectator,
                                            isHost = m.isModerator,
                                            isSelf = userManager.isSelf(m.id),
                                            socialProfiles = if (showConnectedSocials) {
                                                m.identity?.socialProfiles?.mapNotNull {
                                                    it.toLinked()
                                                }.orEmpty()
                                            } else {
                                                emptyList()
                                            }
                                        )
                                    }
                                )
                            )
                            dispatchEvent(Event.OnFeeChanged(room.messagingFee))
                        }
                } else {
                    roomController.observeConversation(args.roomId.orEmpty())
                        .filterNotNull()
                        .map { Triple(it.conversation, it.members, it.conversation.coverCharge) }
                        .onEach { (conversation, members, cover) ->
                            dispatchEvent(Event.OnRoomOpenStateChanged(conversation.isOpen))
                            dispatchEvent(Event.OnNameChanged(conversation.titleOrFallback(resources)))
                            dispatchEvent(
                                Event.OnMembersUpdated(
                                    members.map { m ->
                                        MinimalMember(
                                            id = m.id,
                                            name = m.displayName.nullIfEmpty(),
                                            profileImageUrl = m.imageUri,
                                            isHost = m.isHost,
                                            canSpeak = m.isFullMember,
                                            isSelf = userManager.isSelf(m.id),
                                            socialProfiles = if (showConnectedSocials) {
                                                m.profiles.mapNotNull { it.toLinked() }
                                            } else {
                                                emptyList()
                                            }
                                        )
                                    }
                                )
                            )
                            dispatchEvent(Event.OnFeeChanged(cover))
                        }.launchIn(viewModelScope)
                }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.LeaveRoom>()
            .map { stateFlow.value.roomInfo.number }
            .onEach { roomNumber ->
                BottomBarManager.showMessage(
                    BottomBarManager.BottomBarMessage(
                        title = resources.getString(R.string.title_leaveRoom),
                        subtitle = resources.getString(R.string.subtitle_leaveRoom),
                        positiveText = resources.getString(
                            R.string.action_leaveRoomByName,
                            resources.getString(R.string.title_implicitRoomTitle, roomNumber)
                        ),
                        negativeText = "",
                        tertiaryText = resources.getString(R.string.action_cancel),
                        onPositive = { dispatchEvent(Event.OnLeaveRoomConfirmed) },
                        onNegative = { },
                        type = BottomBarManager.BottomBarMessageType.DESTRUCTIVE,
                        showScrim = true,
                    )
                )
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnListenToClicked>()
            .map { stateFlow.value.roomInfo }
            .onEach { roomInfo ->
                dispatchEvent(Event.OnJoiningStateChanged(true))
                chatsController.joinRoomAsSpectator(roomInfo.id.orEmpty())
                    .onFailure {
                        dispatchEvent(Event.OnJoiningStateChanged(false))
                        TopBarManager.showMessage(
                            TopBarManager.TopBarMessage(
                                resources.getString(R.string.error_title_failedToFollowRoom),
                                resources.getString(
                                    R.string.error_description_failedToFollowRoom,
                                    stateFlow.value.roomInfo.title
                                )
                            )
                        )
                    }.onSuccess {
                        dispatchEvent(Event.OnBecameMember(it.room.id))
                    }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnLeaveRoomConfirmed>()
            .map { stateFlow.value.roomInfo.id }
            .mapNotNull {
                if (it == null) {
                    TopBarManager.showMessage(
                        TopBarManager.TopBarMessage(
                            title = resources.getString(R.string.error_title_failedToLeaveRoom),
                            message = resources.getString(R.string.error_description_failedToLeaveRoom)
                        )
                    )
                    return@mapNotNull null
                }
                dispatchEvent(Event.OnLeavingStateChanged(true))
                roomController.leaveRoom(it)
            }.onResult(
                onError = {
                    dispatchEvent(Event.OnLeavingStateChanged(false))
                    TopBarManager.showMessage(
                        TopBarManager.TopBarMessage(
                            title = resources.getString(R.string.error_title_failedToLeaveRoom),
                            message = resources.getString(R.string.error_description_failedToLeaveRoom)
                        )
                    )
                },
                onSuccess = {
                    dispatchEvent(Event.OnLeavingStateChanged(leaving = false, left = true))
                    dispatchEvent(Event.OnLeftRoom)
                }
            ).launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnOpenStateChangedRequested>()
            .mapNotNull { stateFlow.value.roomInfo.id }
            .map { it to stateFlow.value.isOpen }
            .onEach { (conversationId, isOpen) ->
                confirmOpenStateChange(conversationId, isOpen)
            }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnOpenRoom>()
            .map { it.conversationId }
            .map { roomController.enableChat(it) }
            .onResult(
                onError = {
                    TopBarManager.showMessage(
                        TopBarManager.TopBarMessage(
                            resources.getString(R.string.error_title_failedToReopenRoom),
                            resources.getString(R.string.error_description_failedToReopenRoom)
                        )
                    )
                },
            ).launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnCloseRoom>()
            .map { it.conversationId }
            .map { roomController.disableChat(it) }
            .onResult(
                onError = {
                    TopBarManager.showMessage(
                        TopBarManager.TopBarMessage(
                            resources.getString(R.string.error_title_failedToCloseRoom),
                            resources.getString(R.string.error_description_failedToCloseRoom)
                        )
                    )
                },
            ).launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnShareRoomClicked>()
            .map { IntentUtils.shareRoom(stateFlow.value.roomInfo.roomNumber) }
            .onEach { dispatchEvent(Event.ShareRoom(it)) }
            .launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.PromoteRequested>()
            .map { it.member }
            .onEach {
                confirmUserPromote(
                    conversationId = stateFlow.value.roomInfo.id.orEmpty(),
                    userId = it.id.orEmpty(),
                    user = it.displayName
                )
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.DemoteRequested>()
            .map { it.member }
            .onEach {
                confirmUserDemote(
                    conversationId = stateFlow.value.roomInfo.id.orEmpty(),
                    userId = it.id.orEmpty(),
                    user = it.displayName
                )
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.PromoteUser>()
            .onEach { member ->
                roomController.promoteUser(member.conversationId, member.userId)
                    .onFailure {
                        TopBarManager.showMessage(
                            TopBarManager.TopBarMessage(
                                resources.getString(R.string.error_title_failedToPromoteUser),
                                resources.getString(R.string.error_description_failedToPromoteUser)
                            )
                        )
                    }.onSuccess {
                        dispatchEvent(Event.OnUserPromoted(member.userId))
                    }
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.DemoteUser>()
            .onEach { member ->
                roomController.demoteUser(member.conversationId, member.userId)
                    .onFailure {
                        TopBarManager.showMessage(
                            TopBarManager.TopBarMessage(
                                resources.getString(R.string.error_title_failedToDemoteUser),
                                resources.getString(R.string.error_description_failedToDemoteUser)
                            )
                        )
                    }.onSuccess {
                        dispatchEvent(Event.OnUserDemoted(member.userId))
                    }
            }.launchIn(viewModelScope)
    }

    private fun confirmOpenStateChange(conversationId: ID, isRoomOpen: Boolean) {
        BottomBarManager.showMessage(
            BottomBarManager.BottomBarMessage(
                title = if (isRoomOpen) resources.getString(R.string.prompt_title_closeRoom) else resources.getString(
                    R.string.prompt_title_reopenRoom
                ),
                subtitle = if (isRoomOpen) resources.getString(R.string.prompt_description_closeRoom) else resources.getString(
                    R.string.prompt_description_reopenRoom
                ),
                positiveText = if (isRoomOpen) resources.getString(R.string.action_closeTemporarily) else resources.getString(
                    R.string.action_reopenRoom
                ),
                tertiaryText = resources.getString(R.string.action_cancel),
                onPositive = {
                    if (isRoomOpen) {
                        dispatchEvent(Event.OnCloseRoom(conversationId))
                    } else {
                        dispatchEvent(Event.OnOpenRoom(conversationId))
                    }
                },
                type = BottomBarManager.BottomBarMessageType.THEMED,
                showScrim = true,
            )
        )
    }

    private fun confirmUserPromote(conversationId: ID, user: String?, userId: ID) {
        BottomBarManager.showMessage(
            BottomBarManager.BottomBarMessage(
                title = resources.getString(
                    R.string.title_promoteUserInRoom,
                    user.orEmpty().ifEmpty { "User" }),
                subtitle = resources.getString(R.string.subtitle_promoteUserInRoom),
                positiveText = resources.getString(R.string.action_promote),
                negativeText = "",
                tertiaryText = resources.getString(R.string.action_cancel),
                onPositive = { dispatchEvent(Event.PromoteUser(conversationId, userId)) },
                onNegative = { },
                type = BottomBarManager.BottomBarMessageType.THEMED,
                showScrim = true,
            )
        )
    }

    private fun confirmUserDemote(conversationId: ID, user: String?, userId: ID) {
        BottomBarManager.showMessage(
            BottomBarManager.BottomBarMessage(
                title = resources.getString(
                    R.string.title_demoteUserInRoom,
                    user.orEmpty().ifEmpty { "User" }),
                subtitle = resources.getString(R.string.subtitle_demoteUserInRoom),
                positiveText = resources.getString(R.string.action_demote),
                negativeText = "",
                tertiaryText = resources.getString(R.string.action_cancel),
                onPositive = { dispatchEvent(Event.DemoteUser(conversationId, userId)) },
                onNegative = { },
                type = BottomBarManager.BottomBarMessageType.DESTRUCTIVE,
                showScrim = true,
            )
        )
    }

    companion object {
        private fun sortMembers(members: List<MinimalMember>): Map<MemberType, List<MinimalMember>> {
            return members
                .groupBy { it.canSpeak }
                .mapKeys {
                    if (it.key) {
                        MemberType.Speaker
                    } else {
                        MemberType.Listener
                    }
                }
                .mapValues { (_, members) ->
                    members.sortedWith(
                        compareByDescending<MinimalMember> { it.isHost }.thenByDescending { it.isSelf }
                    )
                }
        }

        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            (when (event) {
                Event.LeaveRoom -> { state -> state }
                is Event.OnInfoChanged -> { state ->
                    val args = event.args
                    state.copy(
                        isPreview = event.isPreview,
                        roomInfo = RoomInfo(
                            id = args.roomId,
                            number = args.roomNumber,
                            title = args.roomTitle.orEmpty(),
                            memberCount = args.memberCount,
                            hostId = args.ownerId,
                            hostName = args.hostName,
                            roomNumber = args.roomNumber,
                            messagingFee = Kin.fromQuarks(args.messagingFeeQuarks)
                        )
                    )
                }

                is Event.PromoteRequested,
                is Event.PromoteUser,
                is Event.DemoteRequested,
                is Event.DemoteUser,
                is Event.OnChangeMessageFee,
                Event.OnLeaveRoomConfirmed,
                is Event.OnChangeName,
                is Event.OnShareRoomClicked,
                is Event.ShareRoom,
                is Event.OnListenToClicked,
                is Event.OnBecameMember,
                is Event.OnOpenStateChangedRequested,
                is Event.OnCloseRoom,
                is Event.OnOpenRoom,
                Event.OnLeftRoom -> { state -> state }

                is Event.OnUserPromoted -> { state ->
                    val members = state.members.flatMap { it.value }
                    val updatedMembers = members.map {
                        if (it.id == event.id) {
                            it.copy(canSpeak = true)
                        } else {
                            it
                        }
                    }

                    state.copy(members = sortMembers(updatedMembers))
                }

                is Event.OnUserDemoted -> { state ->
                    val members = state.members.flatMap { it.value }
                    val updatedMembers = members.map {
                        if (it.id == event.id) {
                            it.copy(canSpeak = false)
                        } else {
                            it
                        }
                    }

                    state.copy(members = sortMembers(updatedMembers))
                }

                is Event.OnHostStatusChanged -> { state -> state.copy(isHost = event.isHost) }
                is Event.OnFeeChanged -> { state ->
                    state.copy(
                        roomInfo = state.roomInfo.copy(
                            messagingFee = event.cover,
                        )
                    )
                }

                is Event.OnNameChanged -> { state ->
                    state.copy(
                        roomInfo = state.roomInfo.copy(
                            title = event.name,
                        )
                    )
                }

                is Event.OnMembersUpdated -> { state ->
                    val groupedMembers = sortMembers(event.members)

                    state.copy(
                        roomInfo = state.roomInfo.copy(
                            memberCount = event.members.count(),
                        ),
                        isMember = event.members.any { it.isSelf },
                        members = groupedMembers,
                    )
                }

                is Event.OnRoomNameChangesEnabled -> { state ->
                    state.copy(
                        roomNameChangesEnabled = event.enabled
                    )
                }

                is Event.OnDestinationChanged -> { state -> state.copy(paymentDestination = event.destination) }
                is Event.OnJoiningStateChanged -> { state ->
                    state.copy(
                        joining = state.joining.copy(
                            loading = event.joining,
                            success = event.joined
                        )
                    )
                }

                is Event.OnLeavingStateChanged -> { state ->
                    state.copy(
                        leaving = state.joining.copy(
                            loading = event.leaving,
                            success = event.left
                        )
                    )
                }

                is Event.OnRoomOpenStateChanged -> { state -> state.copy(isOpen = event.isOpen) }
            })
        }
    }
}