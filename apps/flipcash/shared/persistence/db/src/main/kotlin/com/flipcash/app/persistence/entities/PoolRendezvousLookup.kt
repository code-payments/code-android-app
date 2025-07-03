package com.flipcash.app.persistence.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pool_rendezvous_keys")
data class PoolRendezvousKeyEntity(
    @PrimaryKey
    val poolIdBase58: String,
    val rendezvousSeedBase58: String,
)