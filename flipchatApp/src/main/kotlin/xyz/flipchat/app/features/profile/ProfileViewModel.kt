package xyz.flipchat.app.features.profile

import androidx.lifecycle.viewModelScope
import com.getcode.model.ID
import com.getcode.view.BaseViewModel2
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import xyz.flipchat.app.beta.Lab
import xyz.flipchat.app.beta.Labs
import xyz.flipchat.controllers.ProfileController
import xyz.flipchat.services.domain.model.profile.UserProfile
import xyz.flipchat.services.user.UserManager
import xyz.flipchat.services.user.social.SocialProfile
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    userManager: UserManager,
    profileController: ProfileController,
    labs: Labs,
): BaseViewModel2<ProfileViewModel.State, ProfileViewModel.Event>(
    initialState = State(
        id = userManager.userId
    ),
    updateStateForEvent = updateStateForEvent
) {
    data class State(
        val id: ID? = null,
        private val name: String = "",
        val linkedSocialProfile: SocialProfile? = null,
        val isStaff: Boolean = false,
        val canConnectAccount: Boolean = false,
    ) {
        val displayName: String
            get() = linkedSocialProfile?.let {
                when (it) {
                    SocialProfile.Unknown -> null
                    is SocialProfile.X -> it.friendlyName
                }
            } ?: name

        val username: String?
            get() = linkedSocialProfile?.let {
                when (it) {
                    SocialProfile.Unknown -> null
                    is SocialProfile.X -> it.username
                }
            }

        val imageUrl: String?
            get() = linkedSocialProfile?.let {
                when (it) {
                    SocialProfile.Unknown -> null
                    is SocialProfile.X -> it.profilePicUrl
                }
            }
    }

    sealed interface Event {
        data class OnUserLoaded(val user: UserProfile): Event
        data class OnStaffEmployed(val enabled: Boolean) : Event
        data class CanConnectSocialChanged(val enabled: Boolean): Event
        data object LinkXAccount: Event
    }

    init {
        userManager.state
            .mapNotNull { UserProfile(it.displayName.orEmpty(), it.linkedSocialProfiles) }
            .distinctUntilChanged()
            .onEach { dispatchEvent(Event.OnUserLoaded(it)) }
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
    }

    internal companion object {
        val updateStateForEvent: (Event) -> ((State) -> State) = { event ->
            when (event) {
                is Event.OnStaffEmployed -> { state -> state.copy(isStaff = event.enabled) }
                is Event.OnUserLoaded -> { state ->
                    state.copy(
                        name = event.user.displayName,
                        linkedSocialProfile = event.user.socialProfiles
                            .filterNot { it is SocialProfile.Unknown }
                            .firstOrNull()
                    )
                }
                is Event.CanConnectSocialChanged -> { state -> state.copy(canConnectAccount = event.enabled) }
                Event.LinkXAccount -> { state -> state }
            }
        }
    }
}