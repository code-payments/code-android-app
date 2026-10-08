package com.flipcash.app.menu.internal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flipcash.app.core.bill.Scannable
import com.flipcash.app.core.share.TipCodeExportFormat
import com.flipcash.app.core.share.TipCodeExporter
import com.flipcash.app.core.share.exportFileBaseName
import com.flipcash.app.shareable.ShareSheetController
import com.flipcash.app.shareable.Shareable
import com.flipcash.features.menu.R
import com.flipcash.shared.tipping.TippingCoordinator
import com.getcode.manager.BottomBarManager
import com.getcode.opencode.model.core.ID
import com.getcode.util.resources.ResourceHelper
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch

/** [userId]'s profile card, shown full screen and exportable as an image. */
@HiltViewModel(assistedFactory = ProfileCardViewModel.Factory::class)
internal class ProfileCardViewModel @AssistedInject constructor(
    @Assisted userId: ID,
    private val tippingCoordinator: TippingCoordinator,
    private val tipCodeExporter: TipCodeExporter,
    private val shareable: ShareSheetController,
    private val resources: ResourceHelper,
) : ViewModel() {

    /** Null until the card resolves. */
    var card: Scannable.TipCard? by mutableStateOf(null)
        private set

    init {
        viewModelScope.launch {
            tippingCoordinator.profileCard(userId).onSuccess { card = it }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(userId: ID): ProfileCardViewModel
    }

    /** Asks which format to export, then renders it. */
    fun download() {
        BottomBarManager.showMessage(
            title = resources.getString(R.string.title_downloadTipCardAs),
            actions = downloadOptions(resources) { format -> export(format) },
            showCancel = false,
            showScrim = true,
        )
    }

    // Render the chosen format, then hand the file to the Sharesheet — Android has no
    // permissionless "save to Photos", and the chooser already offers Files/Drive/Photos.
    private fun export(format: TipCodeExportFormat) {
        val card = card ?: return
        viewModelScope.launch {
            // A name of nothing but characters a file system rejects sanitises to null, which must
            // land on the fallback rather than "Chat with ". The exporter sanitises and caps the
            // assembled name again.
            val safeName = exportFileBaseName(card.user.displayName)
            val baseName = if (safeName == null) {
                resources.getString(R.string.label_profileCardFileNameFallback)
            } else {
                resources.getString(R.string.label_profileCardFileName, safeName)
            }
            val export = tipCodeExporter.export(card, format, baseName = baseName)
            if (export == null) {
                BottomBarManager.showMessage(
                    title = resources.getString(R.string.error_title_tipCardExportFailed),
                    message = resources.getString(R.string.error_description_tipCardExportFailed),
                )
                return@launch
            }
            shareable.present(
                Shareable.TipCodeImage(
                    export = export,
                    title = resources.getString(R.string.title_shareTipCode),
                )
            )
        }
    }
}
