package com.flipcash.app.menu.internal

import androidx.lifecycle.ViewModel
import com.flipcash.services.models.UserProfile
import com.flipcash.services.user.UserManager
import com.flipcash.shared.tipping.TippingCoordinator
import com.getcode.opencode.model.core.ID
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** What the You tab's share sheet needs to address the viewer's own profile. */
@HiltViewModel
internal class ProfileShareViewModel @Inject constructor(
    private val userManager: UserManager,
    private val tippingCoordinator: TippingCoordinator,
) : ViewModel() {

    /** The profile being shared, read once when the sheet opens. */
    val profile: UserProfile?
        get() = userManager.state.value.userProfile

    /** Null only when there is no signed-in user to address. */
    val currentUserId: ID?
        get() = tippingCoordinator.currentUserId
}
