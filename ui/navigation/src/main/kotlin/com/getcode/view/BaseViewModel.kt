package com.getcode.view

import androidx.lifecycle.ViewModel
import com.getcode.util.resources.ResourceHelper
import io.reactivex.rxjava3.disposables.CompositeDisposable

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