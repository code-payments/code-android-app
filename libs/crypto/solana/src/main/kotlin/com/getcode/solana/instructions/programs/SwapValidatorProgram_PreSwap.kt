package com.getcode.solana.instructions.programs

import com.getcode.solana.keys.AccountMeta
import com.getcode.solana.Instruction
import com.getcode.utils.toByteArray

class SwapValidatorProgram_PreSwap(
    val preSwapState: com.getcode.solana.keys.PublicKey,
    val user: com.getcode.solana.keys.PublicKey,
    val source: com.getcode.solana.keys.PublicKey,
    val destination: com.getcode.solana.keys.PublicKey,
    val nonce: com.getcode.solana.keys.PublicKey,
    val payer: com.getcode.solana.keys.PublicKey,
    val remainingAccounts: List<AccountMeta>,
): com.getcode.solana.instructions.InstructionType {
    override fun instruction(): Instruction {
        val accounts = mutableListOf(
            AccountMeta.writable(publicKey = preSwapState),
            AccountMeta.readonly(publicKey = user),
            AccountMeta.readonly(publicKey = source),
            AccountMeta.readonly(publicKey = destination),
            AccountMeta.readonly(publicKey = nonce),
            AccountMeta.writable(publicKey = payer, signer = true),
            AccountMeta.readonly(publicKey = SystemProgram.address),
            AccountMeta.readonly(publicKey = SysVar.rent.address())
        )

        accounts.addAll(remainingAccounts)

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
            SwapValidatorProgram.parse(
                command = SwapValidatorProgram.Command.preSwap,
                instruction = instruction,
                expectingAccounts = null
            )

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