package com.getcode.view.main.account.withdraw

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.navigation.NavController
import com.getcode.App
import com.getcode.network.client.Client
import com.getcode.network.client.fetchDestinationMetadata
import com.getcode.network.repository.TransactionRepository
import com.getcode.solana.keys.PublicKey
import com.getcode.solana.keys.base58
import com.getcode.utils.ErrorUtils
import com.getcode.view.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.kin.sdk.base.tools.Base58
import javax.inject.Inject

data class AccountWithdrawAddressUiModel(
    val addressText: String = "",
    val isPasteEnabled: Boolean = false,
    val isNextEnabled: Boolean = false,
    val isValid: Boolean? = null,
    val hasResolvedDestination: Boolean = false,
    val resolvedAddress: String? = null
)

@HiltViewModel
class AccountWithdrawAddressViewModel @Inject constructor(
    private val client: Client,
) : BaseViewModel() {
    val uiFlow = MutableStateFlow(AccountWithdrawAddressUiModel())

    private fun getClipboardValue(): String {
        val clipboard: ClipboardManager? =
            App.getInstance().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
        return clipboard?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
    }

    fun refreshPasteButtonState() {
        CoroutineScope(Dispatchers.IO).launch {
            delay(400)
            val addressText = getClipboardValue()
            val isValid = isAddressValid(addressText)
            uiFlow.value = uiFlow.value.copy(isPasteEnabled = isValid)
        }
    }

    private fun isAddressValid(addressText: String): Boolean {
        return try {
            val decoded = Base58.decode(addressText)
            decoded.size == 32
        } catch (e: Exception) {
            false
        }
    }

    fun pasteAddress() {
        uiFlow.value = uiFlow.value.copy(isPasteEnabled = false)

        val addressText = getClipboardValue()
        if (isAddressValid(addressText)) {
            setAddress(addressText)
        }
    }

    fun setAddress(addressText: String) {
        val publicKey: PublicKey? = try {
            val decoded = Base58.decode(addressText)
            val isValid = decoded.size == 32
            if (isValid) PublicKey(decoded.toList()) else null
        } catch (e: Exception) {
            null
        }
        val isChanged = addressText != uiFlow.value.addressText

        if (isChanged) {
            uiFlow.value = uiFlow.value.copy(
                addressText = addressText.replace("\n", ""),
                isValid = null,
                isNextEnabled = false
            )
        }

        if (publicKey != null && isChanged) {
            getDestinationMetaData(publicKey)
        }
    }

    fun onSubmit(
        navController: NavController,
        arguments: Bundle,
    ) {
        val amountFiat = arguments.getString(ARG_WITHDRAW_AMOUNT_FIAT) ?: return
        val amountKin = arguments.getString(ARG_WITHDRAW_AMOUNT_KIN) ?: return
        val amountText = arguments.getString(ARG_WITHDRAW_AMOUNT_TEXT) ?: return
        val currencyCode = arguments.getString(ARG_WITHDRAW_AMOUNT_CURRENCY_CODE) ?: return
        val currencyResId = arguments.getString(ARG_WITHDRAW_AMOUNT_CURRENCY_RES_ID) ?: return
        val currencyRate = arguments.getString(ARG_WITHDRAW_AMOUNT_CURRENCY_RATE) ?: return
        val resolvedDestination = uiFlow.value.resolvedAddress ?: return

        navController.navigate(
            SheetSections.WITHDRAW_SUMMARY.route
                .replace("{$ARG_WITHDRAW_AMOUNT_FIAT}", amountFiat)
                .replace("{$ARG_WITHDRAW_AMOUNT_KIN}", amountKin)
                .replace("{$ARG_WITHDRAW_AMOUNT_TEXT}", amountText)
                .replace("{$ARG_WITHDRAW_AMOUNT_CURRENCY_CODE}", currencyCode)
                .replace("{$ARG_WITHDRAW_AMOUNT_CURRENCY_RES_ID}", currencyResId)
                .replace("{$ARG_WITHDRAW_AMOUNT_CURRENCY_RATE}", currencyRate)
                .replace("{$ARG_WITHDRAW_ADDRESS}", uiFlow.value.addressText)
                .replace("{$ARG_WITHDRAW_RESOLVED_DESTINATION}", resolvedDestination)
        )
    }

    private fun getDestinationMetaData(publicKey: PublicKey) {
        client.fetchDestinationMetadata(publicKey)
            .subscribe({ result ->
                when (result.kind) {
                    TransactionRepository.DestinationMetadata.Kind.OwnerAccount,
                    TransactionRepository.DestinationMetadata.Kind.TokenAccount -> {
                        uiFlow.value = uiFlow.value.copy(
                            resolvedAddress = result.resolvedDestination.base58(),
                            isValid = true,
                            isNextEnabled = true,
                            hasResolvedDestination = result.hasResolvedDestination
                        )
                    }
                    else -> {
                        uiFlow.value = uiFlow.value.copy(
                            resolvedAddress = "",
                            isValid = false,
                            isNextEnabled = false,
                            hasResolvedDestination = false
                        )
                    }
                }
            }, ErrorUtils::handleError)
    }
}