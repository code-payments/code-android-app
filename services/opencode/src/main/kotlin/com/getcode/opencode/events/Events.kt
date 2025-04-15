package com.getcode.opencode.events

import com.getcode.opencode.model.accounts.AccountCluster
import com.hoc081098.channeleventbus.ChannelEvent
import com.hoc081098.channeleventbus.ChannelEventKey

sealed interface Events {
    class FetchBalance: ChannelEvent<FetchBalance>, Events {
        override val key: ChannelEvent.Key<FetchBalance> = Key
        companion object Key : ChannelEventKey<FetchBalance>(FetchBalance::class)
    }

    data class RequestFirstAirdrop(internal val owner: AccountCluster): ChannelEvent<RequestFirstAirdrop>,
        Events {
        override val key: ChannelEvent.Key<RequestFirstAirdrop> = Key
        companion object Key : ChannelEventKey<RequestFirstAirdrop>(RequestFirstAirdrop::class)
    }

    data class UpdateLimits(internal val owner: AccountCluster, internal val force: Boolean = false): ChannelEvent<UpdateLimits>,
        Events {
        override val key: ChannelEvent.Key<UpdateLimits> = Key
        companion object Key : ChannelEventKey<UpdateLimits>(UpdateLimits::class)
    }
}