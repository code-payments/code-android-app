package com.getcode.model.intents

import com.codeinc.gen.common.v1.CodeModel.InstructionAccount
import com.codeinc.gen.transaction.v2.CodeTransactionService
import com.getcode.model.toHash
import com.getcode.model.toPublicKey
import com.getcode.solana.keys.AccountMeta
import com.getcode.solana.keys.Hash
import com.getcode.solana.keys.PublicKey
import com.google.protobuf.ByteString

data class SwapConfigParameters(
    val payer: PublicKey,
    val swapProgram: PublicKey,
    val nonce: PublicKey,
    val blockHash: Hash,
    val maxToSend: Long,
    val minToReceive: Long,
    val computeUnitLimit: Int,
    val computeUnitPrice: Long,
    val swapAccounts: List<AccountMeta>,
    val swapData: ByteString,
) {
    companion object {
        operator fun invoke(proto: CodeTransactionService.SwapResponse.ServerParameters): SwapConfigParameters? {
            return runCatching {
                val payer = proto.payer.value.toByteArray().toPublicKey()
                val swapProgram = proto.swapProgram.value.toByteArray().toPublicKey()
                val nonce = proto.nonce.value.toByteArray().toPublicKey()
                val blockHash = proto.recentBlockhash.value.toByteArray().toHash()

                SwapConfigParameters(
                    payer = payer,
                    swapProgram = swapProgram,
                    nonce = nonce,
                    blockHash = blockHash,
                    maxToSend = proto.maxToSend,
                    minToReceive = proto.minToReceive,
                    computeUnitLimit = proto.computeUnitLimit,
                    computeUnitPrice = proto.computeUnitPrice,
                    swapAccounts = proto.swapIxnAccountsList.mapNotNull { it.meta() },
                    swapData = proto.swapIxnData
                )
            }.getOrNull()
        }
    }
}

private fun InstructionAccount.meta(): AccountMeta? = runCatching {
    val publicKey = PublicKey(account.value.toList())
    AccountMeta(
        publicKey = publicKey,
        isSigner = isSigner,
        isWritable = isWritable,
        isPayer = false,
        isProgram = false
    )
}.getOrNull()