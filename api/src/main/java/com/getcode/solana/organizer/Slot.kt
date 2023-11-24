package com.getcode.solana.organizer

import android.content.Context
import com.getcode.crypt.DerivePath
import com.getcode.crypt.DerivedKey
import com.getcode.crypt.MnemonicPhrase
import com.getcode.model.Kin


data class Slot(
    var partialBalance: Kin,
    val type: SlotType,
    private val cluster: Lazy<AccountCluster>
) {
    val billValue: Int = type.getBillValue()

    fun billCount(): Long {
        return (partialBalance.toKinValueDouble() / type.getBillValue()).toLong()
    }

    fun getCluster() = cluster.value

    companion object {
        fun newInstance(
            context: Context,
            partialBalance: Kin = Kin.fromQuarks(0),
            type: SlotType,
            mnemonic: MnemonicPhrase
        ): Slot {
            return Slot(
                partialBalance = partialBalance,
                type = type,
                cluster = lazy {
                    AccountCluster.newInstance(
                        DerivedKey.derive(
                            context,
                            type.getDerivationPath(),
                            mnemonic
                        )
                    )
                }
            )
        }
    }
}

enum class SlotType {
    Bucket1,
    Bucket10,
    Bucket100,
    Bucket1k,
    Bucket10k,
    Bucket100k,
    Bucket1m;
}

fun SlotType.getBillValue(): Int =
    when (this.ordinal) {
        SlotType.Bucket1.ordinal -> 1
        SlotType.Bucket10.ordinal -> 10
        SlotType.Bucket100.ordinal -> 100
        SlotType.Bucket1k.ordinal -> 1_000
        SlotType.Bucket10k.ordinal -> 10_000
        SlotType.Bucket100k.ordinal -> 100_000
        SlotType.Bucket1m.ordinal -> 1_000_000
        else -> throw IllegalStateException()
    }

fun SlotType.getDerivationPath(): DerivePath =
    when (this.ordinal) {
        SlotType.Bucket1.ordinal -> DerivePath.bucket1
        SlotType.Bucket10.ordinal -> DerivePath.bucket10
        SlotType.Bucket100.ordinal -> DerivePath.bucket100
        SlotType.Bucket1k.ordinal -> DerivePath.bucket1k
        SlotType.Bucket10k.ordinal -> DerivePath.bucket10k
        SlotType.Bucket100k.ordinal -> DerivePath.bucket100k
        SlotType.Bucket1m.ordinal -> DerivePath.bucket1m
        else -> null
    }.let { it ?: throw IllegalStateException() }
