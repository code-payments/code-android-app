package com.getcode.crypt

/// Represents a BIP-44/SLIP-10 hierarchical derivation path (e.g. `m/44'/501'/0'/0'`).
class DerivePath(val indexes: List<Index>, val password: String? = null) {
    /// Returns the canonical string form of the path (e.g. `m/44'/501'/0'/0'`).
    fun stringRepresentation(): String {
        val components = indexes.joinToString(separator) { it.stringRepresentation() }
        return "$identifier$separator$components"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DerivePath) return false
        if (indexes != other.indexes) return false
        return true
    }

    override fun hashCode(): Int {
        return indexes.hashCode()
    }

    /// A single index component of a derivation path, optionally hardened.
    data class Index(val value: Int, val hardened: Boolean) {
        /// Returns the string form of this index (e.g. `0'` for a hardened index, `0` otherwise).
        fun stringRepresentation(): String =
            value.toString().let { if (hardened) "$it$hardener" else it }
    }

    companion object {
        /// Parses a derivation path string (e.g. `m/44'/501'/0'/0'`) into a `DerivePath`, or returns `null` if invalid.
        fun newInstance(string: String, password: String? = null): DerivePath? {
            val strings = string.split(separator)
            if (strings.firstOrNull() != identifier) return null
            val indexStrings = strings.drop(1)

            val indexes: List<Index> = indexStrings.map { s ->
                val hardened = s.contains(hardener)
                val value = s.replace(hardener, "").toIntOrNull() ?: return@map null
                Index(value, hardened)
            }.filterNotNull()

            if (indexes.size != indexStrings.count()) return null

            return DerivePath(indexes, password)
        }

        // Primary      - m/44'/501'/0'/0'
        //
        // Incoming     - m/44'/501'/0'/0'/i'/2
        // Outgoing     - m/44'/501'/0'/0'/i'/3
        //
        // Relationship - m/44'/501'/0'/0'/0'/0
        // Swap         - m/44'/501'/0'/0'/1'/0
        //
        // Bucket1      - m/44'/501'/0'/0'/0'/1
        // Bucket10     - m/44'/501'/0'/0'/0'/10
        // Bucket100    - m/44'/501'/0'/0'/0'/100
        // Bucket1k     - m/44'/501'/0'/0'/0'/1000
        // Bucket10k    - m/44'/501'/0'/0'/0'/10000
        // Bucket100k   - m/44'/501'/0'/0'/0'/100000
        // Bucket1m     - m/44'/501'/0'/0'/0'/1000000

        val bucket1    = newInstance("m/44'/501'/0'/0'/0'/1")!!
        val bucket10   = newInstance("m/44'/501'/0'/0'/0'/10")!!
        val bucket100  = newInstance("m/44'/501'/0'/0'/0'/100")!!
        val bucket1k   = newInstance("m/44'/501'/0'/0'/0'/1000")!!
        val bucket10k  = newInstance("m/44'/501'/0'/0'/0'/10000")!!
        val bucket100k = newInstance("m/44'/501'/0'/0'/0'/100000")!!
        val bucket1m   = newInstance("m/44'/501'/0'/0'/0'/1000000")!!
        val primary    = newInstance("m/44'/501'/0'/0'")!!
        val swap       = newInstance("m/44'/501'/0'/0'/1'/0")!!

        /// Returns the derivation path for an incoming bucket at the given index.
        fun getBucketIncoming(index: Int): DerivePath =
            newInstance("m/44'/501'/0'/0'/$index'/2")!!

        /// Returns the derivation path for an outgoing bucket at the given index.
        fun getBucketOutgoing(index: Int): DerivePath =
            newInstance("m/44'/501'/0'/0'/$index'/3")!!

        /// Returns the derivation path for a pool at the given index.
        fun getPool(index: Long): DerivePath =
            newInstance("m/44'/501'/0'/0'/7665'/$index'")!!

        /// Returns the derivation path for a pool rendezvous key at the given index.
        fun getPoolRendezvous(index: Long): DerivePath =
            newInstance("m/44'/501'/0'/0'/2335'/$index'")!!

        /// Returns the relationship derivation path using the given domain host string as the password.
        fun relationship(host: String): DerivePath =
            newInstance("m/44'/501'/0'/0'/0'/0", password = host)!!

        private const val identifier = "m"
        private const val separator = "/"
        private const val hardener = "'"
    }
}
