package com.getcode.view

import androidx.lifecycle.ViewModel
import com.getcode.App
import io.reactivex.rxjava3.disposables.CompositeDisposable

abstract class BaseViewModel : ViewModel() {
    private val compositeDisposable = CompositeDisposable()

    override fun onCleared() {
        super.onCleared()
        compositeDisposable.clear()
    }

    open fun setIsLoading(isLoading: Boolean) {}

    fun getString(resId: Int): String = App.getInstance().getString(resId)
}