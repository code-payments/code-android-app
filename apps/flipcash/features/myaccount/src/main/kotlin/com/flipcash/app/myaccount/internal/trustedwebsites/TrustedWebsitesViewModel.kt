package com.flipcash.app.myaccount.internal.trustedwebsites

import androidx.lifecycle.ViewModel
import com.flipcash.app.core.links.TrustedWebsite
import com.flipcash.app.core.links.TrustedWebsites
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
internal class TrustedWebsitesViewModel @Inject constructor(
    private val trustedWebsites: TrustedWebsites,
) : ViewModel() {

    /** Newest first. */
    val websites: StateFlow<List<TrustedWebsite>> = trustedWebsites.websites

    /** Takes effect at once, with no confirmation: the next link to [host] warns again. */
    fun remove(host: String) = trustedWebsites.remove(host)
}
