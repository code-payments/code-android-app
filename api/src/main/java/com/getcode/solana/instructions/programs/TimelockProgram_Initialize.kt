package com.getcode.solana.instructions.programs

import com.getcode.network.repository.toByteArray
import com.getcode.solana.AccountMeta
import com.getcode.solana.Instruction
import com.getcode.solana.instructions.InstructionType
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.DataSlice.consume
import org.kin.sdk.base.tools.byteArrayToLong
import org.kin.sdk.base.tools.longToByteArray

// Reference: https://github.com/code-wallet/code-server/blob/master/pkg/solana/timelock/instruction_initialize.go
class TimelockProgram_Initialize(
    val nonce: PublicKey,
    val timelock: PublicKey,
    val vault: PublicKey,
    val vaultOwner: PublicKey,
    val mint: PublicKey,
    val timeAuthority: PublicKey,
    val payer: PublicKey,
    val lockout: Long
) : InstructionType {
    override fun instruction(): Instruction {
        return Instruction(
            program = TimelockProgram.address,
            accounts = listOf(
                AccountMeta.readonly(publicKey = nonce),
                AccountMeta.writable(publicKey = timelock),
                AccountMeta.writable(publicKey = vault),
                AccountMeta.readonly(publicKey = vaultOwner),
                AccountMeta.readonly(publicKey = mint),
                AccountMeta.readonly(publicKey = timeAuthority, signer = true),
                AccountMeta.writable(publicKey = payer, signer = true),
                AccountMeta.readonly(publicKey = TokenProgram.address),
                AccountMeta.readonly(publicKey = SystemProgram.address),
                AccountMeta.readonly(publicKey = SysVar.rent.address()),
            ),
            data = encode()
        )
    }

    override fun encode(): List<Byte> {
        val data = mutableListOf<Byte>()
        data.addAll(TimelockProgram.Companion.Command.initialize.value.toByteArray().toList())
        data.addAll(lockout.longToByteArray().toList())

        return data
    }

    companion object {
        fun newInstance(instruction: Instruction): TimelockProgram_Initialize {
            val data = TimelockProgram.parse(instruction = instruction, expectingAccounts = 10)
            //val lockout = data.consume(1)

            return TimelockProgram_Initialize(
                nonce = instruction.accounts[0].publicKey,
                timelock = instruction.accounts[1].publicKey,
                vault = instruction.accounts[2].publicKey,
                vaultOwner = instruction.accounts[3].publicKey,
                mint = instruction.accounts[4].publicKey,
                timeAuthority = instruction.accounts[5].publicKey,
                payer = instruction.accounts[6].publicKey,
                lockout = data.toByteArray().byteArrayToLong()
            )
        }
    }
}