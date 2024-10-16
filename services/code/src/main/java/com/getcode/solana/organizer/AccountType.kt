package com.getcode.solana.organizer

import com.codeinc.gen.common.v1.Model
import com.getcode.model.Domain

sealed interface AccountType {
    data object Primary : AccountType
    data object Incoming : AccountType
    data object Outgoing : AccountType
    data class Bucket(val type: SlotType) : AccountType
    data object RemoteSend: AccountType

    data class Relationship(val domain: Domain): AccountType

    data object Swap: AccountType

    fun sortOrder() = when (this) {
        Primary -> 0
        Incoming -> 1
        Outgoing -> 2
        is Bucket -> {
            when (type) {
                SlotType.Bucket1 -> 3
                SlotType.Bucket10 -> 4
                SlotType.Bucket100 -> 5
                SlotType.Bucket1k -> 6
                SlotType.Bucket10k -> 7
                SlotType.Bucket100k -> 8
                SlotType.Bucket1m -> 9
            }
        }
        Swap -> 10
        is Relationship -> 11
        RemoteSend -> 12
    }

    fun getDerivationPath(index: Int): com.getcode.crypt.DerivePath {
        return when (this) {
            Primary -> com.getcode.crypt.DerivePath.primary
            Incoming -> com.getcode.crypt.DerivePath.getBucketIncoming(index)
            Outgoing -> com.getcode.crypt.DerivePath.getBucketOutgoing(index)
            is Bucket -> type.getDerivationPath()
            RemoteSend -> {
                // Remote send accounts are standard Solana accounts
                // and should use a standard derivation path that
                // would be compatible with other 3rd party wallets
                com.getcode.crypt.DerivePath.primary
            }
            is Relationship -> com.getcode.crypt.DerivePath.relationship(domain)
            Swap -> com.getcode.crypt.DerivePath.swap
        }
    }

    fun getAccountType(): Model.AccountType {
        return when (this) {
            Primary -> Model.AccountType.PRIMARY
            Incoming -> Model.AccountType.TEMPORARY_INCOMING
            Outgoing -> Model.AccountType.TEMPORARY_OUTGOING
            is Bucket -> {
                when (this.type) {
                    SlotType.Bucket1 -> Model.AccountType.BUCKET_1_KIN
                    SlotType.Bucket10 -> Model.AccountType.BUCKET_10_KIN
                    SlotType.Bucket100 -> Model.AccountType.BUCKET_100_KIN
                    SlotType.Bucket1k -> Model.AccountType.BUCKET_1_000_KIN
                    SlotType.Bucket10k -> Model.AccountType.BUCKET_10_000_KIN
                    SlotType.Bucket100k -> Model.AccountType.BUCKET_100_000_KIN
                    SlotType.Bucket1m -> Model.AccountType.BUCKET_1_000_000_KIN
                }
            }
            RemoteSend -> Model.AccountType.REMOTE_SEND_GIFT_CARD
            is Relationship -> Model.AccountType.RELATIONSHIP
            Swap -> Model.AccountType.SWAP
        }
    }

    companion object {
        fun newInstance(accountType: Model.AccountType, relationship: Model.Relationship? = null): AccountType? {
            return when (accountType) {
                Model.AccountType.PRIMARY -> Primary
                Model.AccountType.TEMPORARY_INCOMING -> Incoming
                Model.AccountType.TEMPORARY_OUTGOING -> Outgoing
                Model.AccountType.BUCKET_1_KIN -> Bucket(SlotType.Bucket1)
                Model.AccountType.BUCKET_10_KIN -> Bucket(SlotType.Bucket10)
                Model.AccountType.BUCKET_100_KIN -> Bucket(SlotType.Bucket100)
                Model.AccountType.BUCKET_1_000_KIN -> Bucket(SlotType.Bucket1k)
                Model.AccountType.BUCKET_10_000_KIN -> Bucket(SlotType.Bucket10k)
                Model.AccountType.BUCKET_100_000_KIN -> Bucket(SlotType.Bucket100k)
                Model.AccountType.BUCKET_1_000_000_KIN -> Bucket(SlotType.Bucket1m)
                Model.AccountType.UNKNOWN -> null
                Model.AccountType.LEGACY_PRIMARY_2022 -> Primary
                Model.AccountType.REMOTE_SEND_GIFT_CARD -> RemoteSend
                Model.AccountType.UNRECOGNIZED -> null
                Model.AccountType.RELATIONSHIP -> {
                    val domain = Domain.from(relationship?.domain?.value) ?: return null
                    Relationship(domain)
                }
                Model.AccountType.SWAP -> Swap
            }
        }
    }
}