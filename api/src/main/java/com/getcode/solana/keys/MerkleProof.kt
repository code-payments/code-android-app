package com.getcode.solana.keys

import com.getcode.crypt.Sha256Hash
import com.getcode.solana.AccountMeta

class CryptoUtils {

    companion object {
        /**
         * Verifies if the current instance (as ByteArray) is contained within the given merkleRoot,
         * based on the provided proof.
         *
         * @param merkleRoot The root hash of the Merkle tree.
         * @param proof The list of hashes that constitute the proof of inclusion.
         * @return Boolean indicating whether the instance is contained within the merkleRoot.
         */
        fun ByteArray.verifyMerkleProof(merkleRoot: Hash, proof: List<Hash>): Boolean {
            var currentHash = Sha256Hash.hash(this)

            proof.forEach { proofHash ->
                val proofBytes = proofHash.bytes
                currentHash = generateHashForProof(currentHash, proofBytes)
            }

            return currentHash.toList() == merkleRoot.bytes
        }

        /**
         * Generates a hash for the current level of the proof, based on the current hash and the proof node.
         *
         * @param currentHash The current hash in the proof verification process.
         * @param proofNode The node (hash) from the proof list.
         * @return The hash for the current level of the proof.
         */
        private fun generateHashForProof(currentHash: ByteArray, proofNode: ByteArray): ByteArray {
            return if (AccountMeta.compareLexicographically(currentHash, proofNode) < 0 || currentHash.toList() == proofNode.toList()) {
                Sha256Hash.hash(currentHash + proofNode)
            } else {
                Sha256Hash.hash(proofNode + currentHash)
            }
        }
    }
}
