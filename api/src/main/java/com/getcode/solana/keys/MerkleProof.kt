package com.getcode.solana.keys

import com.getcode.crypt.Sha256Hash
import com.getcode.keys.Hash
import com.getcode.solana.AccountMeta

fun KeyType.verifyContained(merkleRoot: Hash, proof: List<Hash>): Boolean {
    return byteArray.verifyContained(merkleRoot, proof)
}

fun ByteArray.verifyContained(merkleRoot: Hash, proof: List<Hash>): Boolean {
    var hash = Sha256Hash.hash(this)

    val proofNodes = proof.map { it.bytes }
    proofNodes.forEach { n ->
        hash = if (
            AccountMeta.compareLexicographically(hash, n.toByteArray()) < 0 ||
            hash.toList() == n
        ) {
            Sha256Hash.hash(hash + n.toByteArray())
        } else {
            Sha256Hash.hash(n.toByteArray() + hash)
        }
    }

    return hash.toList() == merkleRoot.bytes
}