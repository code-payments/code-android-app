package com.flipcash.app.payments.delegates

sealed interface DelegateEvent {
    data object Cancel: DelegateEvent
    data object Sent : DelegateEvent
}