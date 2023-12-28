package com.getcode.model

import com.getcode.solana.keys.PublicKey
import com.getcode.solana.organizer.Relationship
import okhttp3.internal.toImmutableMap
import java.util.Comparator
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RelationshipBox @Inject constructor() {
    private val _publicKeys = mutableMapOf<PublicKey, Relationship>()
    val publicKeys
        get() = _publicKeys.toImmutableMap()

    private val _domains = mutableMapOf<Domain, Relationship>()
    val domains
        get() = _domains.toImmutableMap()


    fun relationships(largestFirst: Boolean = false): List<Relationship> {
        return _domains.values.sortedWith { a, b ->
            val comparisonResult = a.partialBalance.compareTo(b.partialBalance)
            if (largestFirst) -comparisonResult else comparisonResult
        }
    }
    fun relationshipWith(publicKey: PublicKey) = _publicKeys[publicKey]
    fun relationshipWith(domain: Domain) = _domains[domain]

    fun insert(relationship: Relationship) {
        _publicKeys[relationship.getCluster().timelockAccounts.vault.publicKey] = relationship
        _domains[relationship.domain] = relationship
    }
}