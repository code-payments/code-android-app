package com.flipcash.app.payments.delegates

sealed interface DelegateEvent {
    data object Sent : DelegateEvent
}