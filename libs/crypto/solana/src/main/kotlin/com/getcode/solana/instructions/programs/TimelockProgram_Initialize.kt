package com.getcode.solana.instructions.programs

import com.getcode.solana.keys.AccountMeta
import com.getcode.solana.Instruction
import com.getcode.solana.instructions.programs.TimelockProgram.Command
import com.getcode.utils.toByteArray
import org.kin.sdk.base.tools.byteArrayToLong
import org.kin.sdk.base.tools.longToByteArray

// Reference: https://github.com/code-wallet/code-server/blob/master/pkg/solana/timelock/instruction_initialize.go
class TimelockProgram_Initialize(
    val nonce: com.getcode.solana.keys.PublicKey,
    val timelock: com.getcode.solana.keys.PublicKey,
    val vault: com.getcode.solana.keys.PublicKey,
    val vaultOwner: com.getcode.solana.keys.PublicKey,
    val mint: com.getcode.solana.keys.PublicKey,
    val timeAuthority: com.getcode.solana.keys.PublicKey,
    val payer: com.getcode.solana.keys.PublicKey,
    val lockout: Long
) : com.getcode.solana.instructions.InstructionType {
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
        data.addAll(Command.initialize.value.toByteArray().toList())
        data.addAll(lockout.longToByteArray().toList())

        return data
    }

    companion object {
        fun newInstance(instruction: Instruction): TimelockProgram_Initialize {
            val data = TimelockProgram.parse(
                command = Command.initialize,
                instruction = instruction,
                expectingAccounts = 10
            )

            return TimelockProgram_Initialize(
                nonce = instruction.accounts[0].publicKey,
                timelock = instruction.accounts[1].publicKey,
                vault = instruction.accounts[2].publicKey,
                vaultOwner = instruction.accounts[3].publicKey,
                mint = instruction.accounts[4].publicKey,
                timeAuthority = instruction.accounts[5].publicKey,
                payer = instruction.accounts[6].publicKey,
                lockout = data.remaining.toByteArray().byteArrayToLong()
            )
        }
    }
}