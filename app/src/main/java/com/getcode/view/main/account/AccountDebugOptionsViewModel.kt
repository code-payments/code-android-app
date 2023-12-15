package com.getcode.view.main.account

import androidx.lifecycle.viewModelScope
import com.getcode.model.PrefsBool
import com.getcode.network.repository.PrefRepository
import com.getcode.utils.ErrorUtils
import com.getcode.view.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject


data class AccountDebugOptionsUiModel(
    val isDebugBuckets: Boolean = false,
    val isVibrateOnScan: Boolean = false,
    val isDisplayErrors: Boolean = false,
    val isRemoteSendEnabled: Boolean = false,
    val isIncentivesEnabled: Boolean = false,
)

@HiltViewModel
class AccountDebugOptionsViewModel @Inject constructor(
    private val prefRepository: PrefRepository,
) : BaseViewModel() {
    val uiFlow = MutableStateFlow(AccountDebugOptionsUiModel())

    fun reset() {
        viewModelScope.launch {
            try {
                val isDebugBuckets =
                    prefRepository.getFirstOrDefault(PrefsBool.IS_DEBUG_BUCKETS, false)
                uiFlow.tryEmit(uiFlow.value.copy(isDebugBuckets = isDebugBuckets))

                val isVibrateOnScan =
                    prefRepository.getFirstOrDefault(PrefsBool.IS_DEBUG_VIBRATE_ON_SCAN, false)
                uiFlow.tryEmit(uiFlow.value.copy(isVibrateOnScan = isVibrateOnScan))

                val isDisplayErrors =
                    prefRepository.getFirstOrDefault(PrefsBool.IS_DEBUG_DISPLAY_ERRORS, false)
                uiFlow.tryEmit(uiFlow.value.copy(isDisplayErrors = isDisplayErrors))
            } catch (e: Exception) {
                ErrorUtils.handleError(e)
            }
        }
    }

    fun setIsDisplayErrors(isDisplayErrors: Boolean) {
        prefRepository.set(PrefsBool.IS_DEBUG_DISPLAY_ERRORS, isDisplayErrors)
        uiFlow.tryEmit(uiFlow.value.copy(isDisplayErrors = isDisplayErrors))
        ErrorUtils.setDisplayErrors(isDisplayErrors)
    }

    fun setBool(key: PrefsBool, value: Boolean) {
        prefRepository.set(key, value)
        val model = when(key) {
            PrefsBool.IS_DEBUG_BUCKETS -> uiFlow.value.copy(isDebugBuckets = value)
            PrefsBool.IS_DEBUG_VIBRATE_ON_SCAN -> uiFlow.value.copy(isVibrateOnScan = value)
            PrefsBool.IS_DEBUG_DISPLAY_ERRORS -> uiFlow.value.copy(isDisplayErrors = value)
            else -> uiFlow.value
        }
        uiFlow.tryEmit(model)
    }
}