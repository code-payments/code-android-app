package com.flipcash.app.myaccount.internal.linkdesktop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.app.devicelink.PhoneLinkController
import com.flipcash.services.user.UserManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Holds the common [PhoneLinkController] for the screen's lifetime; the seed is read on confirm. */
@HiltViewModel
internal class LinkDesktopViewModel @Inject constructor(
    userManager: UserManager,
) : ViewModel() {
    val controller = PhoneLinkController(viewModelScope) { userManager.state.value.entropy }

    override fun onCleared() {
        controller.cancel()
    }
}
