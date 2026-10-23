package com.getcode.opencode.internal.transactors

import com.getcode.crypt.DerivedKey
import com.getcode.opencode.model.accounts.AccountCluster
import javax.inject.Inject

fun interface AccountClusterFactory {
    fun create(authority: DerivedKey): AccountCluster
}

class DefaultAccountClusterFactory @Inject constructor() : AccountClusterFactory {
    override fun create(authority: DerivedKey): AccountCluster = AccountCluster.newInstance(authority)
}
