package com.getcode.model

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity
data class FaqItem(
    @PrimaryKey(autoGenerate = true) val uid: Int? = null,
    val question: String,
    val answer: String
    )