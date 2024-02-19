package com.getcode.solana.organizer

import android.content.Context
import com.getcode.crypt.DerivePath
import com.getcode.crypt.DerivedKey
import com.getcode.crypt.MnemonicPhrase
import com.getcode.model.AccountInfo
import com.getcode.model.Domain
import com.getcode.model.Kin
import com.getcode.solana.keys.*
import timber.log.Timber

class Organizer(
    val tray: Tray,
    val mnemonic: MnemonicPhrase,
    private var accountInfos: Map<PublicKey, AccountInfo> = mapOf(),
    val context: Context
) {
    val slotsBalance get() = tray.slotsBalance
    val availableBalance get() = tray.availableBalance
    val availableDepositBalance get() = tray.owner.partialBalance
    val availableIncomingBalance get() = tray.incoming.partialBalance
    val availableRelationshipBalance get() = tray.availableRelationshipBalance
    val ownerKeyPair get() = tray.owner.getCluster().authority.keyPair
    val swapKeyPair get() = tray.swap.getCluster().authority.keyPair
    val swapDepositAddress get() = swapKeyPair.publicKey
    val primaryVault get() = tray.owner.getCluster().timelockAccounts.vault.publicKey
    val incomingVault get() = tray.incoming.getCluster().timelockAccounts.vault.publicKey

    val isUnlocked: Boolean
        get() = accountInfos.values.any { info ->
            info.managementState != AccountInfo.ManagementState.Locked
        }

    fun set(tray: Tray) {
        this.tray.slots = tray.slots
        this.tray.owner = tray.owner
        this.tray.incoming = tray.incoming
        this.tray.outgoing = tray.outgoing
        this.tray.mnemonic = tray.mnemonic
    }

    fun setBalances(balances: Map<AccountType, Kin>) {
        tray.setBalances(balances)
    }

    fun allAccounts() = tray.allAccounts()

    fun info(accountType: AccountType): AccountInfo? {
        val account = tray.cluster(accountType).timelockAccounts.vault.publicKey
        return accountInfos[account]
    }

    fun setAccountInfo(infos: Map<PublicKey, AccountInfo>) {
        this.accountInfos = infos
        tray.createRelationships(context, infos)
        propagateBalances()
    }

    fun getAccountInfo() = accountInfos

    val buckets: List<AccountInfo>
        get() = accountInfos.values.toList().sortedBy { it.accountType.sortOrder() }

    fun propagateBalances() {
        val balances = mutableMapOf<AccountType, Kin>()

        for ((vaultPublicKey, info) in accountInfos) {
            val cluster = tray.cluster(accountType = info.accountType)

            if (cluster.timelockAccounts.vault.publicKey == vaultPublicKey) {
                balances[info.accountType] = info.balance
            } else {
                // The public key above doesn't match any accounts
                // that the Tray is aware of. If we're dealing with
                // temp I/O accounts then we likely just need to
                // update the index and try again
                when (info.accountType) {
                    AccountType.Incoming, AccountType.Outgoing -> {
                        // Update the index
                        tray.setIndex(context, info.index, accountType = info.accountType)
                        Timber.i("Updating ${info.accountType} index to: ${info.index}")

                        // Ensure that the account matches
                        val cluster1 = tray.cluster(accountType = info.accountType)

                        if (cluster1.timelockAccounts.vault.publicKey != vaultPublicKey) {
                            Timber.i("Indexed account mismatch. This isn't suppose to happen.")
                            continue
                        }
                        balances[info.accountType] = info.balance
                    }
                    AccountType.Primary,
                    is AccountType.Bucket,
                    AccountType.RemoteSend,
                    is AccountType.Relationship,
                    AccountType.Swap -> {
                        Timber.i("Non-indexed account mismatch. Account doesn't match server-provided account. Something is definitely wrong")
                    }
                }
            }
        }

        setBalances(balances)
    }

    fun relationshipFor(domain: Domain): Relationship? {
        return tray.relationships.relationshipWith(domain)
    }

    fun relationshipsLargestFirst(): List<Relationship> {
        return tray.relationships.relationships(largestFirst = true)
    }

    companion object {
        fun newInstance(
            context: Context,
            mnemonic: MnemonicPhrase
        ): Organizer {
            val tray = Tray.newInstance(context, mnemonic)
            return Organizer(
                mnemonic = mnemonic,
                tray = tray,
                context = context
            )
        }
    }
}

enum class Denomination {
    ones,
    tens,
    hundreds,
    thousands,
    tenThousands,
    hundredThousands,
    millions;

    val derivationPath: DerivePath
        get() {
            return when (this) {
                ones -> DerivePath.bucket1
                tens -> DerivePath.bucket10
                hundreds -> DerivePath.bucket100
                thousands -> DerivePath.bucket1k
                tenThousands -> DerivePath.bucket10k
                hundredThousands -> DerivePath.bucket100k
                millions -> DerivePath.bucket1m
            }
        }
}