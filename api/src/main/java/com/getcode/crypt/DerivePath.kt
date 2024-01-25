package com.getcode.crypt

import com.getcode.model.Domain

class DerivePath(val indexes: List<Index>, val password: String? = null) {
    fun stringRepresentation(): String {
        val components = indexes.joinToString(separator) { it.stringRepresentation() }
        return "$identifier$separator$components"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DerivePath

        if (indexes != other.indexes) return false

        return true
    }

    override fun hashCode(): Int {
        return indexes.hashCode()
    }

    data class Index(val value: Int, val hardened: Boolean) {
        fun stringRepresentation(): String =
            value.toString().let { if (hardened) "$it$hardener" else it }
    }

    companion object {
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

        val bucket1    = newInstance("m/44'/501'/0'/0'/0'/1")!!
        val bucket10   = newInstance("m/44'/501'/0'/0'/0'/10")!!
        val bucket100  = newInstance("m/44'/501'/0'/0'/0'/100")!!
        val bucket1k   = newInstance("m/44'/501'/0'/0'/0'/1000")!!
        val bucket10k  = newInstance("m/44'/501'/0'/0'/0'/10000")!!
        val bucket100k = newInstance("m/44'/501'/0'/0'/0'/100000")!!
        val bucket1m   = newInstance("m/44'/501'/0'/0'/0'/1000000")!!
        val primary    = newInstance("m/44'/501'/0'/0'")!!

        fun getBucketIncoming(index: Int): DerivePath {
            return newInstance("m/44'/501'/0'/0'/$index'/2")!!
        }

        fun getBucketOutgoing(index: Int): DerivePath {
            return newInstance("m/44'/501'/0'/0'/$index'/3")!!
        }

        fun relationship(domain: Domain): DerivePath {
            return newInstance("m/44'/501'/0'/0'/0'/0", password = domain.relationshipHost)!!
        }

        private const val identifier = "m"
        private const val separator = "/"
        private const val hardener = "'"
    }
}