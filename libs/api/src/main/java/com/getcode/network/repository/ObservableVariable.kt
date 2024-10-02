package com.getcode.network.repository

import io.reactivex.rxjava3.subjects.BehaviorSubject

class ObservableVariable<T : Any>(private val defaultValue: T) {
    var value: T = defaultValue
        set(value) {
            field = value
            observable.onNext(value)
        }
    val observable = BehaviorSubject.createDefault(value)
}