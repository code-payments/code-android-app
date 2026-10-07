package com.flipcash.app.menu.internal

import android.content.ClipboardManager
import androidx.lifecycle.ViewModel
import com.flipcash.app.bills.share.TipCodePreviewCache
import com.flipcash.app.core.extensions.setText
import com.flipcash.app.core.tipping.TipCardOwner
import com.flipcash.app.core.toast.SystemToastController
import com.flipcash.app.core.util.Linkify
import com.flipcash.app.shareable.ShareSheetController
import com.flipcash.app.shareable.Shareable
import com.flipcash.features.menu.R
import com.flipcash.services.models.UserProfile
import com.flipcash.services.user.UserManager
import com.flipcash.shared.tipping.TippingCoordinator
import com.getcode.util.resources.ResourceHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What the share sheet's rows do for the viewer's own profile. The sheet only chooses; the
 * navigation host runs one of these once the sheet has finished leaving.
 */
@HiltViewModel
internal class ProfileShareViewModel @Inject constructor(
    private val userManager: UserManager,
    private val tippingCoordinator: TippingCoordinator,
    private val tipCodePreviewCache: TipCodePreviewCache,
    private val shareable: ShareSheetController,
    private val clipboardManager: ClipboardManager,
    private val toastController: SystemToastController,
    private val resources: ResourceHelper,
) : ViewModel() {

    // Not viewModelScope: the actions run after the sheet has finished leaving, and this view model
    // is cleared with the sheet's entry before then. Each action is short and holds nothing but
    // singletons, so there is nothing to leak by letting it finish.
    private val actionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** The profile being shared, read once when the sheet opens. */
    val profile: UserProfile?
        get() = userManager.state.value.userProfile

    /** The link a profile is shared by. Null only when there is no signed-in user to address. */
    private val link: String?
        get() = tippingCoordinator.currentUserId?.let { userId ->
            Linkify.tipcard(TipCardOwner.preferringUsername(profile?.username, userId))
        }

    /** Hands the profile link, with its card preview if ready, to the system share sheet. */
    fun share() {
        val userId = tippingCoordinator.currentUserId ?: return
        // Attach the eagerly-rendered preview if it's ready; null shares the URL alone.
        actionScope.launch {
            shareable.present(
                Shareable.Profile(
                    userId = userId,
                    displayName = profile?.displayName,
                    username = profile?.username,
                    preview = tipCodePreviewCache.get(userId),
                )
            )
        }
    }

    fun copyLink() {
        val link = link ?: return
        clipboardManager.setText(
            text = link,
            label = resources.getString(R.string.title_clipboardLabelTipCardLink),
        )
        toastController.showToast(R.string.action_copied, replacePrevious = true)
    }
}
