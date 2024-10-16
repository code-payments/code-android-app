package com.getcode.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class GiftCard(
    @PrimaryKey val key: String, //base58
    val entropy: String, //base58
    val amount: Long,
    val date: Long
)