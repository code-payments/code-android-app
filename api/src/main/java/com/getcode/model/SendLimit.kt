package com.getcode.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.codeinc.gen.common.v1.Model

@Entity
data class SendLimit(
    @PrimaryKey val id: String,
    val limit: Double
)