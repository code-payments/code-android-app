package xyz.flipchat.app.features.profile

import android.app.Activity
import androidx.lifecycle.viewModelScope
import com.getcode.manager.BottomBarManager
import com.getcode.manager.TopBarManager
import com.getcode.model.ID
import com.getcode.model.social.user.SocialProfile
import com.getcode.util.resources.ResourceHelper
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import xyz.flipchat.app.R
import xyz.flipchat.app.auth.AuthManager
import xyz.flipchat.app.beta.Lab
import xyz.flipchat.app.beta.Labs
import xyz.flipchat.app.features.login.register.onResult
import xyz.flipchat.controllers.ChatsController
import xyz.flipchat.controllers.ProfileController
import xyz.flipchat.services.domain.model.profile.UserProfile
import xyz.flipchat.services.user.UserManager
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    userManager: UserManager,
    profileController: ProfileController,
    labs: Labs,
    resources: ResourceHelper,
    private val authManager: AuthManager,
    private val chatsController: ChatsController,
): BaseViewModel2<ProfileViewModel.State, ProfileViewModel.Event>(
    initialState = State(),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        val id: ID? = null,
        val isSelf: Boolean = false,
        private val name: String = "",
        val linkedSocialProfile: SocialProfile? = null,
        val isStaff: Boolean = false,
        val canConnectAccount: Boolean = false,
        val showConnectedSocial: Boolean = false,
    ) {
        val displayName: String
            get() = linkedSocialProfile?.let {
                when (it) {
                    is SocialProfile.Unknown -> null
                    is SocialProfile.X -> it.friendlyName
                }
            } ?: name

        val username: String?
            get() = linkedSocialProfile?.let {
                when (it) {
                    is SocialProfile.Unknown -> null
                    is SocialProfile.X -> "@${it.username}"
                }
            }

        val imageUrl: String?
            get() = linkedSocialProfile?.let {
                when (it) {
                    is SocialProfile.Unknown -> null
                    is SocialProfile.X -> it.profilePicUrl
                }
            }
    }

    sealed interface Event {
        data class OnLoadUser(val id: ID): Event
        data class OnUserLoaded(val isSelf: Boolean, val user: UserProfile): Event
        data class OnStaffEmployed(val enabled: Boolean) : Event
        data class CanConnectSocialChanged(val enabled: Boolean): Event
        data class LinkXAccount(val accessToken: String?): Event
        data class UnlinkSocialProfileRequested(val profile: SocialProfile): Event
        data class UnlinkSocialProfile(val profile: SocialProfile): Event
    }

    init {
        // handle self user updates (connects/disconnects)
        stateFlow
            .filter { it.isSelf }
            .flatMapLatest { userManager.state }
            .mapNotNull { UserProfile(it.displayName.orEmpty(), it.linkedSocialProfiles) }
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.OnUserLoaded(true, it)) }
            .launchIn(viewModelScope)

        userManager.state
            .mapNotNull { it.flags }
            .map { it.isStaff }
            .onEach { dispatchEvent(Event.OnStaffEmployed(it)) }
            .launchIn(viewModelScope)

        labs.observe(Lab.ConnectX)
            .onEach {
                dispatchEvent(Event.CanConnectSocialChanged(it))
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.OnLoadUser>()
            .map { it.id }
            .distinctUntilChanged()
            .map { id ->
                if (userManager.isSelf(id)) {
                    Result.success(true to UserProfile(userManager.displayName.orEmpty(), userManager.socialProfiles))
                } else {
                    profileController.getProfile(id)
                        .map { false to it }
                }
            }.onResult(
                onError = {},
                onSuccess = { (isSelf, profile) ->
                    dispatchEvent(Event.OnUserLoaded(isSelf, profile))
                }
            ).launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.LinkXAccount>()
            .map { it.accessToken }
            .map { profileController.linkXAccount(it.orEmpty()) }
            .onResult(
                onError = {
                    TopBarManager.showMessage(
                        TopBarManager.TopBarMessage(
                            resources.getString(R.string.error_title_failedToLinkXAccount),
                            resources.getString(R.string.error_description_failedToLinkXAccount)
                        )
                    )
                },
            ).launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.UnlinkSocialProfileRequested>()
            .map { it.profile }
            .filterNot { it is SocialProfile.Unknown }
            .onEach { profile ->
                BottomBarManager.showMessage(
                    BottomBarManager.BottomBarMessage(
                        title = resources.getString(R.string.prompt_title_disconnectSocialAccount, profile.platformTypeName),
                        subtitle = resources
                            .getString(R.string.prompt_description_disconnectSocialAccount, profile.platformTypeName),
                        positiveText = resources.getString(R.string.action_disconnectSocialAccount, profile.platformTypeName),
                        tertiaryText = resources.getString(R.string.action_cancel),
                        onPositive = {
                            dispatchEvent(Event.UnlinkSocialProfile(profile))
                        }
                    )
                )
            }.launchIn(viewModelScope)

        eventFlow
            .filterIsInstance<Event.UnlinkSocialProfile>()
            .map { it.profile }
            .onEach {
                when (it) {
                    SocialProfile.Unknown -> Unit
                    is SocialProfile.X -> {
                        profileController.unlinkXAccount(it)
                            .onFailure {
                                TopBarManager.showMessage(
                                    TopBarManager.TopBarMessage(
                                        resources.getString(R.string.error_title_failedToUnlinkXAccount),
                                        resources.getString(R.string.error_description_failedToUnlinkXAccount)
                                    )
                                )
                            }
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    fun deleteAccount(activity: Activity, onComplete: () -> Unit) = viewModelScope.launch {
        authManager.deleteAndLogout(activity)
            .onSuccess {
                chatsController.closeEventStream()
                onComplete()
            }
    }

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnLoadUser -> { state -> state.copy(id = event.id) }
                is Event.OnStaffEmployed -> { state -> state.copy(isStaff = event.enabled) }
                is Event.OnUserLoaded -> { state ->
                    state.copy(
                        isSelf = event.isSelf,
                        name = event.user.displayName,
                        linkedSocialProfile = event.user.socialProfiles
                            .firstOrNull()
                    )
                }
                is Event.CanConnectSocialChanged -> { state -> state.copy(canConnectAccount = event.enabled) }
                is Event.LinkXAccount -> { state -> state }
                is Event.UnlinkSocialProfileRequested -> { state -> state }
                is Event.UnlinkSocialProfile -> { state -> state }
            }
        }
    }
}