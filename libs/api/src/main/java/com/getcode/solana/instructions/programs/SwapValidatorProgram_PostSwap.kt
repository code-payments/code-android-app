package com.getcode.solana.instructions.programs

import com.getcode.network.repository.toByteArray
import com.getcode.solana.AccountMeta
import com.getcode.solana.Instruction
import com.getcode.solana.instructions.InstructionType
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.DataSlice.consume
import com.getcode.utils.DataSlice.toLong

class SwapValidatorProgram_PostSwap(
    val stateBump: Byte,
    val maxToSend: Long,
    val minToReceive: Long,
    val preSwapState: PublicKey,
    val source: PublicKey,
    val destination: PublicKey,
    val payer: PublicKey,
) : InstructionType {

    override fun instruction(): Instruction {
        return Instruction(
            program = SwapValidatorProgram.address,
            accounts = listOf(
                AccountMeta.writable(publicKey = preSwapState),
                AccountMeta.readonly(publicKey = source),
                AccountMeta.readonly(publicKey = destination),
                AccountMeta.writable(publicKey = payer, signer = true),
            ),
            data = encode()
        )
    }

    override fun encode(): List<Byte> {
        val data = mutableListOf<Byte>()
        data.addAll(SwapValidatorProgram.Command.postSwap.value.toByteArray().toList())
        data.add(stateBump)
        data.addAll(maxToSend.toByteArray().toList())
        data.addAll(minToReceive.toByteArray().toList())

        return data
    }

    companion object {
        fun newInstance(instruction: Instruction): SwapValidatorProgram_PostSwap {
            val data = SwapValidatorProgram.parse(SwapValidatorProgram.Command.postSwap, instruction, null)

            val stateStride = Byte.SIZE_BYTES
            val (stateBump, bumpRemaining) = data.remaining.consume(stateStride).let {
                it.consumed.toByteArray().toLong().toByte() to it.remaining
            }

            val maxToSendStride = ULong.SIZE_BYTES
            val (maxToSend, maxToSendRemaining) = bumpRemaining.consume(maxToSendStride).let {
                it.consumed.toByteArray().toLong() to it.remaining
            }

            val minToReceiveStride = ULong.SIZE_BYTES
            val (minToReceive, _) = maxToSendRemaining.consume(minToReceiveStride).let {
                it.consumed.toByteArray().toLong() to it.remaining
            }

            return SwapValidatorProgram_PostSwap(
                stateBump = stateBump,
                maxToSend = maxToSend,
                minToReceive = minToReceive,
                preSwapState = instruction.accounts[0].publicKey,
                source = instruction.accounts[1].publicKey,
                destination = instruction.accounts[2].publicKey,
                payer = instruction.accounts[3].publicKey,
            )
        }
    }
}