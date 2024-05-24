package com.getcode.solana.organizer

import android.content.Context
import com.getcode.crypt.DerivePath
import com.getcode.crypt.DerivedKey
import com.getcode.crypt.MnemonicPhrase
import com.getcode.model.AccountInfo
import com.getcode.model.Domain
import com.getcode.model.Kin
import com.getcode.model.RelationshipBox
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.TraceType
import com.getcode.utils.timedTrace
import com.getcode.utils.trace
import kotlin.math.min

class Tray(
    var slots: List<Slot>,
    var owner: PartialAccount,
    var swap: PartialAccount,
    var incoming: PartialAccount,
    var outgoing: PartialAccount,
    var mnemonic: MnemonicPhrase
) {
    var slotsBalance: Kin = Kin.fromKin(0)
        get() = slots.map { it.partialBalance }.reduce { acc, slot -> acc + slot }
        private set

    var availableBalance: Kin = Kin.fromKin(0)
        get() = slotsBalance + availableDepositBalance + availableIncomingBalance + availableRelationshipBalance
        private set

    private val availableDepositBalance: Kin
        get() = owner.partialBalance

    private val availableIncomingBalance: Kin
        get() = incoming.partialBalance

    var relationships = RelationshipBox()
        internal set

    var availableRelationshipBalance: Kin = Kin.fromKin(0)
        get() = relationships.publicKeys.values.map { it.partialBalance }
            .reduceOrNull { acc, slot -> acc + slot } ?: Kin.fromKin(0)
        private set

    fun slot(type: SlotType): Slot {
        return slots.first { it.type == type }
    }

    fun slotDown(type: SlotType): Slot? {
        val index = slots.indexOfFirst { it.type == type }
        if (index > 0) {
            return slots[index - 1]
        }
        return null
    }

    fun slotUp(type: SlotType): Slot? {
        val index = slots.indexOfFirst { it.type == type }
        if (index < slots.size - 1) {
            return slots[index + 1]
        }
        return null
    }

    fun increment(type: AccountType, kin: Kin) {
        when (type) {
            AccountType.Primary -> owner.partialBalance += kin
            AccountType.Incoming -> incoming.partialBalance += kin
            AccountType.Outgoing -> outgoing.partialBalance += kin
            is AccountType.Bucket -> slots[type.type.ordinal].partialBalance += kin
            AccountType.RemoteSend -> throw IllegalStateException("Remote send account unsupported")
            is AccountType.Relationship -> {
                val relationship = relationships.relationshipWith(type.domain)
                    ?: throw IllegalStateException("Relationship for ${type.domain.relationshipHost}) not found in ${relationships.domains}")
                relationships.insert(
                    relationship.apply {
                        partialBalance += kin
                    }
                )
            }

            AccountType.Swap -> swap.partialBalance += kin
        }
    }

    fun decrement(type: AccountType, kin: Kin) {
        when (type) {
            AccountType.Primary -> owner.partialBalance -= kin
            AccountType.Incoming -> incoming.partialBalance -= kin
            AccountType.Outgoing -> outgoing.partialBalance -= kin
            is AccountType.Bucket -> slots[type.type.ordinal].partialBalance -= kin
            AccountType.RemoteSend -> throw IllegalStateException("Remote send account unsupported")
            is AccountType.Relationship -> {
                val relationship = relationships.relationshipWith(type.domain)
                    ?: throw IllegalStateException("Relationship for ${type.domain.relationshipHost}) not found in ${relationships.domains}")
                relationships.insert(
                    relationship.apply {
                        partialBalance -= kin
                    }
                )
            }
            AccountType.Swap -> swap.partialBalance -= kin
        }
    }

    fun setBalances(balances: Map<AccountType, Kin>) {
        owner.partialBalance = balances[AccountType.Primary] ?: owner.partialBalance
        incoming.partialBalance = balances[AccountType.Incoming] ?: incoming.partialBalance
        outgoing.partialBalance = balances[AccountType.Outgoing] ?: outgoing.partialBalance

        slots[0].partialBalance = balances[AccountType.Bucket(SlotType.Bucket1)]    ?: slots[0].partialBalance
        slots[1].partialBalance = balances[AccountType.Bucket(SlotType.Bucket10)]   ?: slots[1].partialBalance
        slots[2].partialBalance = balances[AccountType.Bucket(SlotType.Bucket100)]  ?: slots[2].partialBalance
        slots[3].partialBalance = balances[AccountType.Bucket(SlotType.Bucket1k)]   ?: slots[3].partialBalance
        slots[4].partialBalance = balances[AccountType.Bucket(SlotType.Bucket10k)]  ?: slots[4].partialBalance
        slots[5].partialBalance = balances[AccountType.Bucket(SlotType.Bucket100k)] ?: slots[5].partialBalance
        slots[6].partialBalance = balances[AccountType.Bucket(SlotType.Bucket1m)]   ?: slots[6].partialBalance

        balances.filter { (type, _) -> type is AccountType.Relationship }
            .mapNotNull { (type, amount) ->
                val relationshipType = type as? AccountType.Relationship ?: return@mapNotNull null
                relationshipType to amount
            }
            .onEach { (relationship, amount) ->
                val domain = relationship.domain
                setBalance(domain, amount)
            }
    }

    private fun setBalance(domain: Domain, balance: Kin) {
        val relationship = relationships.relationshipWith(domain) ?: return
        relationships.insert(relationship.apply {
            partialBalance = balance
        })
    }

    fun partialBalance(type: AccountType): Kin {
        return when (type) {
            is AccountType.Primary -> owner.partialBalance
            is AccountType.Incoming -> incoming.partialBalance
            is AccountType.Outgoing -> outgoing.partialBalance
            is AccountType.Bucket -> slot(type.type).partialBalance
            AccountType.RemoteSend -> throw IllegalStateException("Remote send account unsupported")
            is AccountType.Relationship -> {
                val relationship = relationships.relationshipWith(type.domain)
                    ?: throw IllegalStateException("Relationship for ${type.domain.relationshipHost}) not found in ${relationships.domains}")

                return relationship.partialBalance
            }

            AccountType.Swap -> swap.partialBalance
        }
    }

    fun createRelationships(accountInfos: Map<PublicKey, AccountInfo>) {
        val domains= accountInfos
            .mapNotNull { it.value.relationship?.domain }

        domains.onEach { createRelationship(it) }
    }

    fun createRelationship(domain: Domain): Relationship {
        val relationship = Relationship.newInstance(domain, mnemonic)
        relationships.insert(relationship)
        return relationship
    }

    fun incrementIncoming() {
        setIndex(incoming.getCluster().index + 1, AccountType.Incoming)
    }

    fun incrementOutgoing() {
        setIndex(outgoing.getCluster().index + 1, AccountType.Outgoing)
    }

    fun setIndex(index: Int, accountType: AccountType) {
        when (accountType) {
            AccountType.Incoming -> {
                incoming = PartialAccount(cluster = incoming(index, mnemonic))
            }
            AccountType.Outgoing -> {
                outgoing = PartialAccount(cluster = outgoing(index, mnemonic))
            }

            is AccountType.Bucket,
            AccountType.Primary,
            is AccountType.Relationship,
            AccountType.RemoteSend,
            AccountType.Swap -> {
                throw IllegalStateException()
            }
        }
    }

    fun allAccounts(): List<Pair<AccountType, AccountCluster>> {
        return listOf(
            Pair(AccountType.Primary, owner.getCluster()),
            Pair(AccountType.Incoming, incoming.getCluster()),
            Pair(AccountType.Outgoing, outgoing.getCluster()),
            *slots.map { Pair(AccountType.Bucket(it.type), it.getCluster()) }.toTypedArray(),
        )
    }

    fun publicKey(accountType: AccountType): PublicKey {
        return cluster(accountType).vaultPublicKey
    }

    fun cluster(accountType: AccountType): AccountCluster {
        return when (accountType) {
            AccountType.Primary -> owner.getCluster()
            AccountType.Incoming -> incoming.getCluster()
            AccountType.Outgoing -> outgoing.getCluster()
            is AccountType.Bucket -> slot(accountType.type).getCluster()
            AccountType.RemoteSend -> throw IllegalStateException("Remote send account unsupported")
            is AccountType.Relationship -> {
                relationships.relationshipWith(domain = accountType.domain)!!.getCluster()
            }

            AccountType.Swap -> swap.getCluster()
        }
    }

    fun copy(): Tray {
        return Tray(
            slots = slots.map { it.copy() },
            owner = owner.copy(),
            swap = swap.copy(),
            incoming = incoming.copy(),
            outgoing = outgoing.copy(),
            mnemonic = mnemonic,
        ).apply tray@{
            this@tray.relationships = this@Tray.relationships
        }
    }

    companion object {
        fun newInstance(
            mnemonic: MnemonicPhrase
        ): Tray {
            return Tray(
                mnemonic = mnemonic,
                slots = listOf(
                    Slot.newInstance(
                        type = SlotType.Bucket1,
                        mnemonic = mnemonic
                    ),
                    Slot.newInstance(
                        type = SlotType.Bucket10,
                        mnemonic = mnemonic
                    ),
                    Slot.newInstance(
                        type = SlotType.Bucket100,
                        mnemonic = mnemonic
                    ),
                    Slot.newInstance(
                        type = SlotType.Bucket1k,
                        mnemonic = mnemonic
                    ),
                    Slot.newInstance(
                        type = SlotType.Bucket10k,
                        mnemonic = mnemonic
                    ),
                    Slot.newInstance(
                        type = SlotType.Bucket100k,
                        mnemonic = mnemonic
                    ),
                    Slot.newInstance(
                        type = SlotType.Bucket1m,
                        mnemonic = mnemonic
                    ),
                ),
                incoming = PartialAccount(incoming(0, mnemonic)),
                outgoing = PartialAccount(outgoing( 0, mnemonic)),
                owner = PartialAccount(
                    cluster = AccountCluster.newInstanceLazy(
                        authority = DerivedKey.derive(DerivePath.primary, mnemonic),
                        kind = AccountCluster.Kind.Timelock,
                    )
                ),
                swap = PartialAccount(
                    cluster = AccountCluster.newInstanceLazy(
                        authority = DerivedKey.derive(DerivePath.swap, mnemonic),
                        kind = AccountCluster.Kind.Usdc,
                    )
                )
            )
        }

        fun incoming(index: Int, mnemonic: MnemonicPhrase): Lazy<AccountCluster> {
            return lazy {
                AccountCluster.newInstance(
                    authority = DerivedKey.derive(
                        DerivePath.getBucketIncoming(index),
                        mnemonic
                    ),
                    index = index,
                    kind = AccountCluster.Kind.Timelock,
                )
            }
        }

        fun outgoing(index: Int, mnemonic: MnemonicPhrase): Lazy<AccountCluster> {
            return lazy {
                AccountCluster.newInstance(
                    authority = DerivedKey.derive(
                        DerivePath.getBucketOutgoing(index),
                        mnemonic
                    ),
                    index = index,
                    kind = AccountCluster.Kind.Timelock,
                )
            }
        }
    }


    // MARK: - Redistribute -

    ///  Redistribute the bills in the organizer to ensure there are no gaps
    ///  in consecutive slots.
    ///
    ///  For example, avoid this:
    ///  ----------------------------------------------------------------
    ///  | slot 0 | slot 1 | slot 2 | slot 3 | slot 4 | slot 5 | slot 6 |
    ///  ----------------------------------------------------------------
    ///  |  1     |   0    |   10    |   10  |   0    |   0    |   0    | = 1,101
    ///     ^---------^--- not optimal
    ///
    ///  Instead, we want this:
    ///  ----------------------------------------------------------------
    ///  | slot 0 | slot 1 | slot 2 | slot 3 | slot 4 | slot 5 | slot 6 |
    ///  ----------------------------------------------------------------
    ///  |  11     |   9   |    9   |   10   |   0    |   0    |   0    | = 1,101
    ///      ^---------^--------┘  split the 10 downwards
    ///
    ///  The examples above both have the same total balance, but the second
    ///  example should allow for more efficient payments later down the line.
    ///
    ///  We also try to limit the number of bills in each slot as a secondary
    ///  goal. This is done by recursively exchanging large bills for smaller
    ///  bills and vice versa with rules around how many of each denomination
    ///  to keep. Typically, you never need more than 9 pennies to make any
    ///  payment.
    ///
    ///  Algorithm:
    ///  --------------------------------------------------------------------
    ///  1) First we take large bills and exchange them for smaller bills one
    ///  at a time. We do this recursively until we can't exchange any more
    ///  large bills to small ones. This spreads out our total balance over
    ///  as many slots as possible.
    ///
    ///  2) Then we take smaller bills and exchange them for larger bills if
    ///  we have more than needed in any slot. This reduces the number of
    ///  bills we have in total.
    ///
    ///  This algorithm guarantees that we will never have gaps (zero balance)
    ///  between consecutive slots (e.g. 1000, 0, 10, 1).
    /// ---------------------------------------------------------------------
    ///
    /// TODO: this algorithm could be optimized to reduce the number of
    /// transactions
    fun redistribute(): List<InternalExchange> {
        val exchanges = mutableListOf<InternalExchange>()

        exchanges.addAll(
            exchangeLargeToSmall()
        )

        exchanges.addAll(
            exchangeSmallToLarge()
        )

        return exchanges
    }

    fun receive(receivingAccount: AccountType, amount: Kin): List<InternalExchange> {
        if (partialBalance(receivingAccount) < amount) throw OrganizerException.InvalidSlotBalanceException()

        val container = mutableListOf<InternalExchange>()

        var remainingAmount = amount

        for (i in (slots.size - 1 downTo 0)) {
            val currentSlot = slots[i]

            val howManyFit: Int = (remainingAmount.toKinValueDouble() / currentSlot.billValue).toInt()
            if (howManyFit > 0) {
                val amountToDeposit = Kin.fromKin(howManyFit * currentSlot.billValue)

                normalize(slotType = currentSlot.type, amount = amountToDeposit) { subAmount ->
                    container.add(
                        InternalExchange(
                            from = receivingAccount,
                            to = AccountType.Bucket(currentSlot.type),
                            kin = subAmount
                        )
                    )
                }

                decrement(receivingAccount, amountToDeposit)
                increment(AccountType.Bucket(currentSlot.type), amountToDeposit)

                remainingAmount -= amountToDeposit
            }
        }

        return container
    }

    /// Recursive function to exchange large bills to smaller bills (when
    /// possible). For example, if we have dimes but no pennies, we should
    /// break a dime into pennies.
    ///

    fun exchangeLargeToSmall(layer: Int = 0): List<InternalExchange> {
        val padding = "-".repeat(layer + 1) + "|"

        val exchanges = mutableListOf<InternalExchange>()

        for (i in 1..slots.size) {
            val currentSlot = slots[slots.size - i] // Backwards
            val smallerSlot = slotDown(currentSlot.type)

            trace("$padding o Checking slot: ${currentSlot.type}", type = TraceType.Silent)

            if (smallerSlot == null) {
                // We're at the lowest denomination
                // so we can't exchange anymore.
                trace("$padding x Last slot", type = TraceType.Silent)
                break
            }

            if (currentSlot.billCount() <= 0) {
                // Nothing to exchange, the current slot is empty.
                trace("$padding x Empty", type = TraceType.Silent)
                continue
            }

            val howManyFit = currentSlot.billValue / smallerSlot.billValue

            if (smallerSlot.billCount() >= howManyFit - 1) {
                // No reason to exchange yet, the smaller slot
                // already has enough bills for most payments
                trace("$padding x Enough bills", type = TraceType.Silent)
                continue
            }

            val amount = Kin.fromKin(currentSlot.billValue)

            // Adjust the slot balance
            decrement(AccountType.Bucket(currentSlot.type), kin = amount)
            increment(AccountType.Bucket(smallerSlot.type), kin = amount)

            trace(
                message = "$padding v Exchanging from ${currentSlot.type} to ${smallerSlot.type} $amount Kin",
                type = TraceType.Silent
            )

            exchanges.add(
                InternalExchange(
                    from = AccountType.Bucket(currentSlot.type),
                    to = AccountType.Bucket(smallerSlot.type),
                    kin = amount
                )
            )

            exchanges.addAll(
                exchangeLargeToSmall(layer = layer + 1)
            ) // Recursive
        }

        return exchanges
    }

    /// Recursive function to exchange small bills to larger bills (when
    /// possible).
    ///
    /// For example, if we have 19 pennies or more, we should exchange excess
    /// pennies for dimes. But if we only have 18 pennies or less, we
    /// should not exchange any because we'd be unable to make a future
    /// payment that has a $0.09 amount (there are some edge cases).
    ///

    fun exchangeSmallToLarge(layer: Int = 0): List<InternalExchange> {
        val padding = "-".repeat(layer + 1) + "|"

        val exchanges = mutableListOf<InternalExchange>()

        for (element in slots) {

            val currentSlot = element // Forwards
            val largerSlot = slotUp(currentSlot.type)

            trace("$padding o Checking slot: ${currentSlot.type}")

            if (largerSlot == null) {
                // We're at the largest denomination
                // so we can't exchange anymore.
                trace("$padding x Last slot", type = TraceType.Silent)
                break
            }

            // First we need to check how many bills of the current type fit
            // into the next slot.

            val howManyFit = largerSlot.billValue / currentSlot.billValue
            val howManyWeHave = currentSlot.billCount()
            val howManyToLeave = min(howManyFit - 1L, howManyWeHave)

            if (howManyWeHave < ((howManyFit * 2) - 1)) {
                // We don't have enough bills to exchange, so we can't do
                // anything in this slot at the moment.
                trace("$padding x Not enough bills", type = TraceType.Silent)
                continue
            }

            val howManyToExchange = (howManyWeHave - howManyToLeave) / howManyFit * howManyFit
            val amount = Kin.fromKin(kin = howManyToExchange) * currentSlot.billValue

            val slotTransfers = mutableListOf<InternalExchange>()

            normalizeLargest(amount = amount) { partialAmount ->
                    slotTransfers.add(
                        InternalExchange(
                            from = AccountType.Bucket(currentSlot.type),
                            to = AccountType.Bucket(largerSlot.type),
                            kin = partialAmount
                        )
                    )
            }

            // Adjust the slot balance
            decrement(AccountType.Bucket(currentSlot.type), kin = amount)
            increment(AccountType.Bucket(largerSlot.type), kin = amount)

            slotTransfers.forEach { transfer ->
                trace(
                    message = "$padding v Exchanging from ${transfer.from} to {transfer.to!} {transfer.kin} Kin",
                    type = TraceType.Silent
                )
            }

            exchanges.addAll(
                slotTransfers
            )

            exchanges.addAll(
                exchangeSmallToLarge(layer = layer + 1)
            ) // Recursive
        }

        return exchanges
    }

    fun normalize(slotType: SlotType, amount: Kin, handler: (Kin) -> Unit) {
        var howManyFit = amount.toKinTruncatingLong() / slotType.getBillValue()
        while (howManyFit > 0) {
            val billsToMove = min(howManyFit, 9)
            val moveAmount = Kin.fromKin(slotType.getBillValue() * billsToMove)

            handler(moveAmount)

            howManyFit -= billsToMove
        }
    }

    fun normalizeLargest(amount: Kin, handler: (Kin) -> Unit) {
        var remainingAmount = amount

        // Starting from largest denomination to the smallest
        // we'll find how many 'bills' from each stack we need
        for (i in 1..slots.size) {
            val slot = slots[slots.size - i] // Backwards

            var howManyFit = remainingAmount.toKinTruncatingLong() / slot.billValue
            while (howManyFit > 0) {
                val billsToMove = min(howManyFit, 9)
                val moveAmount = Kin.fromKin(kin = slot.billValue * billsToMove)

                handler(moveAmount)

                remainingAmount -= moveAmount
                howManyFit -= billsToMove
            }
        }
    }


    /// This function sends money from the organizer to the outgoing
    /// temporary account. It has to solve the interesting problem of
    /// figuring out which denominations to use when making a payment.
    ///
    /// Unfortunately, this is actually a pretty hard
    /// problem to solve optimally.
    /// https://en.wikipedia.org/wiki/Change-making_problem
    ///
    /// We're going to use the following approach, which should be pretty
    /// good most of the time but definitely has room for improvement.
    /// Specifically, we may want to move from a dynamic programming
    /// solution to a greedy solution in the future.
    ///
    /// Algorithm
    ///
    /// 1. Check the total balance to make sure we have enough to send.
    ///
    /// 2. Try using a naive approach where we send from the amounts
    /// currently in the slots. This will fail if we don't have enough of
    /// a particular bill to pay the amount.
    ///
    /// 3. If step 2 fails, start at the smallest denomination and move
    /// upwards while adding everything along the way until we reach a
    /// denomination that is larger than the remaining amount. Then split
    /// and go backwards... (dynamic programming strategy)
    ///
    fun transfer(amount: Kin): List<InternalExchange> {
        if (amount <= 0) {
            throw OrganizerException.InvalidAmountException()
        }

        if (amount > availableBalance) {
            throw OrganizerException.InsufficientTrayBalanceException()
        }

        val startState = this.copy()

        return try {
            withdrawNaively(amount = amount)
        } catch (e: OrganizerException) {
            this.slots = startState.slots.map { it.copy() }
            this.owner = startState.owner.copy()
            this.incoming = startState.incoming.copy()
            this.outgoing = startState.outgoing.copy()
            withdrawDynamically(amount = amount)
        }
    }

    fun withdrawNaively(amount: Kin): List<InternalExchange> {
        if (amount <= 0) {
            throw OrganizerException.InvalidAmountException()
        }

        val container = mutableListOf<InternalExchange>()

        var remainingAmount = amount

        // Starting from largest denomination to the smallest
        // we'll find how many 'bills' from each stack we need
        for (i in 1..slots.size) {
            val slot = slots[slots.size - i] // Backwards

            if (slot.partialBalance <= 0) {
                continue
            }

            val howManyFit = remainingAmount.toKinTruncatingLong() / slot.billValue

            val maxAmount = Kin.fromKin(howManyFit * slot.billValue)
            val howMuchToSend: Kin = if (slot.partialBalance < maxAmount) slot.partialBalance else maxAmount

            if (howMuchToSend > 0) {
                if (slot.partialBalance < howMuchToSend) {
                    throw OrganizerException.InvalidSlotBalanceException()
                }

                val sourceBucket = AccountType.Bucket(slot.type)

                normalize(slotType = slot.type, amount = howMuchToSend) { amountN ->
                        container.add(
                            InternalExchange(
                                from = sourceBucket,
                                to = AccountType.Outgoing,
                                kin = amountN
                        )
                    )
                }

                decrement(sourceBucket, kin = howMuchToSend)
                increment(AccountType.Outgoing, kin = howMuchToSend)

                remainingAmount -= howMuchToSend
            }
        }

        if (remainingAmount >= 1) {
            throw OrganizerException.InvalidSlotBalanceException()
        }

        return container
    }

    fun withdrawDynamically(amount: Kin): List<InternalExchange> {
        if (amount <= 0) {
            throw OrganizerException.InvalidAmountException()
        }

        if (amount > availableBalance) {
            throw OrganizerException.InsufficientTrayBalanceException()
        }

        val step = withdrawDynamicallyStep1(amount = amount)
        val exchanges = withdrawDynamicallyStep2(step = step)

        return step.exchanges + exchanges
    }

    /// This function assumes that the 'naive strategy' withdrawal was already
    /// attempted. We'll iterate over the slots, from smallest to largest, drain
    /// every slot up to the `amount`. Once a slot that is larger than the
    /// remaining amount is reached, the function returns the index at which the
    /// second step should resume.
    ///
    /// Returns the index that should be broken down in step 2.
    ///
    fun withdrawDynamicallyStep1(amount: Kin): InternalDynamicStep {
        val container = mutableListOf<InternalExchange>()
        var remaining = amount

        for (i in slots.indices) {
            val currentSlot = slots[i] // Forwards

            if (currentSlot.partialBalance <= 0) {
                // Try next slot
                continue
            }

            if (remaining.toKinValueDouble() < 1) {
                // Sent it all
                break
            }

            if (remaining.toKinTruncatingLong() < currentSlot.billValue) {
                // If there's a remaining amount and the current
                // bill value is greater, we'll need to break the
                // current slot bill down to lower slots
                break
            }

            val howManyFit = remaining.toKinTruncatingLong() / currentSlot.billValue
            val maxAmount = howManyFit * currentSlot.billValue
            val howMuchToSend =
                min(currentSlot.partialBalance.toKinValueDouble(), maxAmount.toDouble())
                    .let { Kin.fromKin(it) }

            if (howMuchToSend > 0) {
                normalize(slotType = currentSlot.type, amount = howMuchToSend) { kinToSend ->
                    container.add(
                        InternalExchange(
                            from = AccountType.Bucket(currentSlot.type),
                            to = AccountType.Outgoing,
                            kin = kinToSend
                        )
                    )
                }

                // Adjust the slot balance
                decrement(AccountType.Bucket(currentSlot.type), kin = howMuchToSend)
                increment(AccountType.Outgoing,                 kin = howMuchToSend)

                remaining -= howMuchToSend
            }
        }

        var index = slots.indexOfFirst { it.billValue > remaining.toKinTruncatingLong() && it.billCount() > 0 }

        // Only throw an error if there's a
        // non-zero remaining amount, other
        // wise the first step covered the
        // total amount
        if (index == -1 && remaining >= 1) {
            throw OrganizerException.InvalidStepIndexException()
        }

        if (index == -1) index = 0
        return InternalDynamicStep(
            remaining = remaining,
            index = index,
            exchanges = container
        )
    }

    fun withdrawDynamicallyStep2(step: InternalDynamicStep): List<InternalExchange> {
        if (!(step.index > 0 && step.index < slots.size)) {
            return listOf()
        }

        if (step.remaining < 1) {
            return listOf()
        }

        val container = mutableListOf<InternalExchange>()
        var remaining = step.remaining

        val current = slots[step.index]
        val lower = slots[step.index - 1]

        if (current.billCount() < 1) {
            throw OrganizerException.SlotAtIndexEmptyException()
        }

        // Break the current slot into the lower
        // slot and exchange all the way down
        val initialSplitAmount = Kin.fromKin(kin = current.billValue)
        container.add(
            InternalExchange(
                from = AccountType.Bucket(current.type),
                to = AccountType.Bucket(lower.type),
                kin = initialSplitAmount
            )
        )

        // Adjust the slot balance
        decrement(type = AccountType.Bucket(current.type), kin = initialSplitAmount)
        increment(type = AccountType.Bucket(lower.type), kin = initialSplitAmount)


        for (i in (step.index-1 downTo 0)) {
            val currentSlot = slots[i]

            // Split every slot down to the smallest
            // to ensure we have enough bills in each
            if (i > 0) {
                val lowerSlot = slots[i - 1]
                val splitAmount = Kin.fromKin(currentSlot.billValue)

                container.add(
                    InternalExchange(
                        from = AccountType.Bucket(currentSlot.type),
                        to = AccountType.Bucket(lowerSlot.type),
                        kin = splitAmount
                    )
                )

                // Adjust the slot balance
                decrement(AccountType.Bucket(currentSlot.type), splitAmount)
                increment(AccountType.Bucket(lowerSlot.type), splitAmount)
            }

            val howManyFit = remaining.toKinTruncatingLong() / currentSlot.billValue
            val kinToSend = Kin.fromKin(howManyFit * currentSlot.billValue)

            if (howManyFit <= 0) {
                continue
            }

            if (howManyFit > currentSlot.billCount().toInt()) {
                throw OrganizerException.InvalidSlotBalanceException()
            }

            container.add(
                InternalExchange(
                    from = AccountType.Bucket(currentSlot.type),
                    to = AccountType.Outgoing,
                    kin = kinToSend
                )
            )

            // Adjust the slot balance
            decrement(AccountType.Bucket(currentSlot.type), kin = kinToSend)
            increment(AccountType.Outgoing, kin = kinToSend)

            remaining -= kinToSend
        }

        return container
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Tray

        if (slots != other.slots) return false
        if (incoming != other.incoming) return false
        if (outgoing != other.outgoing) return false
        if (mnemonic != other.mnemonic) return false

        return true
    }

    override fun hashCode(): Int {
        var result = slots.hashCode()
        result = 31 * result + incoming.hashCode()
        result = 31 * result + outgoing.hashCode()
        result = 31 * result + mnemonic.hashCode()
        return result
    }

    sealed class OrganizerException : Exception() {
        class InvalidAmountException : OrganizerException()
        class InsufficientTrayBalanceException : OrganizerException()
        class InvalidSlotBalanceException : OrganizerException()
        class InvalidStepIndexException : OrganizerException()
        class SlotAtIndexEmptyException : OrganizerException()
    }
}

data class InternalExchange(
    var from: AccountType,
    var to: AccountType? = null,
    var kin: Kin
)

data class InternalDynamicStep(
    var remaining: Kin,
    var index: Int,
    var exchanges: List<InternalExchange>
)

data class InternalDeposit(
    var to: SlotType,
    var kin: Kin
)