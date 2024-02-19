package com.getcode.solana.organizer

import android.content.Context
import com.getcode.crypt.DerivePath
import com.getcode.crypt.DerivedKey
import com.getcode.crypt.MnemonicPhrase
import com.getcode.model.Domain
import com.getcode.model.Kin

class Relationship(
    val domain: Domain,
    val mnemonic: MnemonicPhrase,
    var partialBalance: Kin = Kin.fromKin(0),
) {
    private lateinit var cluster: Lazy<AccountCluster>

    fun getCluster() = cluster.value

    companion object {
        fun newInstance(
            context: Context,
            domain: Domain,
            mnemonic: MnemonicPhrase,
            partialKinBalance: Kin = Kin.fromKin(0),
        ): Relationship {
            val cluster = AccountCluster.newInstanceLazy(
                DerivedKey.derive(
                    context = context,
                    path = DerivePath.relationship(domain),
                    mnemonic = mnemonic
                ),
                kind = AccountCluster.Kind.Timelock,
            )

            return Relationship(domain, mnemonic, partialBalance = partialKinBalance).apply {
                this.cluster = cluster
            }
        }
    }
}