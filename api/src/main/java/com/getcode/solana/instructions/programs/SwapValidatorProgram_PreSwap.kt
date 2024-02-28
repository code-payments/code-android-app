package com.getcode.solana.instructions.programs

import com.getcode.network.repository.toByteArray
import com.getcode.solana.AccountMeta
import com.getcode.solana.Instruction
import com.getcode.solana.instructions.InstructionType
import com.getcode.solana.keys.PublicKey

class SwapValidatorProgram_PreSwap(
    val preSwapState: PublicKey,
    val user: PublicKey,
    val source: PublicKey,
    val destination: PublicKey,
    val nonce: PublicKey,
    val payer: PublicKey,
    val remainingAccounts: List<AccountMeta>,
): InstructionType {
    override fun instruction(): Instruction {
        val accounts = listOf(
            AccountMeta.writable(publicKey = preSwapState),
            AccountMeta.readonly(publicKey = user),
            AccountMeta.readonly(publicKey = source),
            AccountMeta.readonly(publicKey = destination,),
            AccountMeta.readonly(publicKey = nonce),
            AccountMeta.writable(publicKey = payer, signer = true),
            AccountMeta.readonly(publicKey = SystemProgram.address),
            AccountMeta.readonly(publicKey = SysVar.rent.address())
        )

        return Instruction(
            program = SwapValidatorProgram.address,
            accounts = accounts,
            data = encode()
        )
    }

    override fun encode(): List<Byte> {
        val data = mutableListOf<Byte>()
        data.addAll(SwapValidatorProgram.Command.preSwap.value.toByteArray().toList())
        return data
    }

    companion object {
        fun newInstance(instruction: Instruction): SwapValidatorProgram_PreSwap {
            return SwapValidatorProgram_PreSwap(
                preSwapState = instruction.accounts[0].publicKey,
                user = instruction.accounts[1].publicKey,
                source = instruction.accounts[2].publicKey,
                destination = instruction.accounts[3].publicKey,
                nonce = instruction.accounts[4].publicKey,
                payer = instruction.accounts[5].publicKey,
                remainingAccounts = instruction.accounts.drop(6)
            )
        }
    }
}