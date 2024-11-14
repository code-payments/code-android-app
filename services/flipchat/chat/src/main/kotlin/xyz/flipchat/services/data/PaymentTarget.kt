package xyz.flipchat.services.data

import com.getcode.model.ID

sealed interface PaymentTarget {
    data class User(val id: ID): PaymentTarget
}