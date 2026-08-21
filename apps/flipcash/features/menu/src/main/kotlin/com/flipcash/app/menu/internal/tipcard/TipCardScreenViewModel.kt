package com.flipcash.app.menu.internal.tipcard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.bill.Scannable
import com.flipcash.shared.tipping.TippingCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Resolves the viewer's own tip card for the full-screen presentation. The coordinator builds it
 * from the cached profile, so this settles within a frame or two of the screen appearing — the
 * nullable state is there for that gap, not for a real load.
 */
@HiltViewModel
internal class TipCardScreenViewModel @Inject constructor(
    tippingCoordinator: TippingCoordinator,
) : ViewModel() {

    private val _card = MutableStateFlow<Scannable.TipCard?>(null)
    val card: StateFlow<Scannable.TipCard?> = _card.asStateFlow()

    init {
        viewModelScope.launch {
            tippingCoordinator.resolveTipCard().onSuccess { _card.value = it }
        }
    }
}
