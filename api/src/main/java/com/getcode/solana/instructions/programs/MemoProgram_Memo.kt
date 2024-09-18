package com.getcode.solana.instructions.programs

import com.getcode.model.SocialUser
import com.getcode.solana.AgoraMemo
import com.getcode.solana.Instruction
import com.getcode.solana.TransferType

class MemoProgram_Memo(data: List<Byte>) : MemoProgram(data) {
    fun instruction(): Instruction {
        return Instruction(
            program = address,
            accounts = listOf(),
            data = encode()
        )
    }

    fun encode() = data

    companion object {
        fun newInstance(transferType: TransferType, kreIndex: Int): MemoProgram_Memo {
            return MemoProgram_Memo(
                data = AgoraMemo(transferType = transferType, appIndex = kreIndex).encode().toList()
            )
        }

        fun newInstance(tipMetadata: SocialUser): MemoProgram_Memo {
            val memo = "tip:${tipMetadata.platform}:${tipMetadata.username}"

            return MemoProgram_Memo(memo.toByteArray().toList())
        }

        /*fun newInstance(instruction: Instruction): MemoProgram_Memo {
            MemoProgram.parse(instruction = instruction, expectingAccounts = 0)
            return MemoProgram_Memo(
                data = instruction.data
            )
        }*/
    }
}