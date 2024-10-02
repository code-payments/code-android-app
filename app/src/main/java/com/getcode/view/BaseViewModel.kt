package com.getcode.view

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getcode.util.resources.ResourceHelper
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

@Deprecated(
    message = "Replaced With BaseViewModel2",
    replaceWith = ReplaceWith("Use BaseViewModel2", "com.getcode.view.BaseViewModel2"))
abstract class BaseViewModel(
    private val resources: ResourceHelper,
) : ViewModel() {
    private val compositeDisposable = CompositeDisposable()

    override fun onCleared() {
        super.onCleared()
        compositeDisposable.clear()
    }

    open fun setIsLoading(isLoading: Boolean) {}

    fun getString(resId: Int): String = resources.getString(resId)
}