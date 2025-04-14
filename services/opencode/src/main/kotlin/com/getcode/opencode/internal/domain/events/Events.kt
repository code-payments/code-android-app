package com.getcode.opencode.internal.domain.events

import com.getcode.opencode.model.accounts.AccountCluster
import com.hoc081098.channeleventbus.ChannelEvent
import com.hoc081098.channeleventbus.ChannelEventKey

internal sealed interface Events {
    class FetchBalance: ChannelEvent<FetchBalance>, Events {
        override val key: ChannelEvent.Key<FetchBalance> = Key
        companion object Key : ChannelEventKey<FetchBalance>(FetchBalance::class)
    }

    data class RequestFirstAirdrop(internal val owner: AccountCluster): ChannelEvent<RequestFirstAirdrop>, Events {
        override val key: ChannelEvent.Key<RequestFirstAirdrop> = Key
        companion object Key : ChannelEventKey<RequestFirstAirdrop>(RequestFirstAirdrop::class)
    }
}