package com.getcode.opencode.model.accounts

import com.getcode.crypt.DerivePath
import com.getcode.crypt.DerivedKey
import com.getcode.crypt.MnemonicPhrase
import com.getcode.ed25519.Ed25519

class PoolAccount(
    val rendezvous: Ed25519.KeyPair,
    val mnemonic: MnemonicPhrase,
    val cluster: AccountCluster,
) {
    companion object {
        fun create(index: Long, mnemonic: MnemonicPhrase): PoolAccount {
            return PoolAccount(
                rendezvous = DerivedKey.derive(
                    DerivePath.getPoolRendezvous(index),
                    mnemonic = mnemonic
                ).keyPair,
                mnemonic = mnemonic,
                cluster = AccountCluster.newInstance(
                    authority = DerivedKey.derive(DerivePath.getPool(index), mnemonic = mnemonic)
                )
            )
        }
    }
}