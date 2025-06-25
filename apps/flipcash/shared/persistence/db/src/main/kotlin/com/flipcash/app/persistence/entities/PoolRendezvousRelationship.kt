package com.flipcash.app.persistence.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "pool_rendezvous")
data class PoolRendezvousEntity(
    @PrimaryKey
    val poolIdBase58: String,
    val rendezvousSeed: String,
    val signature: String?,
)