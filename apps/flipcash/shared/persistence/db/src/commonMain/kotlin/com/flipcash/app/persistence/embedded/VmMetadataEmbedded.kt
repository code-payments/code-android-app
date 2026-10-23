package com.flipcash.app.persistence.embedded

import androidx.room.ColumnInfo
import androidx.room.Ignore
import com.getcode.solana.keys.PublicKey

data class VmMetadataEmbedded(
    @ColumnInfo(name = "vm")
    val vm: String,

    @ColumnInfo(name = "authority")
    val authority: String,

    @ColumnInfo(name = "lock_duration_days")
    val lockDurationInDays: Int,
) {
    @get:Ignore
    val vmKey: PublicKey
        get() = PublicKey(vm)

    @get:Ignore
    val authorityKey: PublicKey
        get() = PublicKey(authority)
}