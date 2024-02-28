package com.getcode.solana.instructions.programs

import com.getcode.model.Kin
import com.getcode.network.repository.toByteArray
import com.getcode.solana.AccountMeta
import com.getcode.solana.Instruction
import com.getcode.solana.instructions.InstructionType
import com.getcode.solana.instructions.programs.TimelockProgram.Command
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.DataSlice.consume
import org.kin.sdk.base.tools.byteArrayToLong
import org.kin.sdk.base.tools.longToByteArray

class TimelockProgram_BurnDustWithAuthority(
    val timelock: PublicKey,
    val vault: PublicKey,
    val vaultOwner: PublicKey,
    val timeAuthority: PublicKey,
    val mint: PublicKey,
    val payer: PublicKey,
    val bump: Byte,
    val maxAmount: Kin,
    val legacy: Boolean = false,
) : InstructionType {
    override fun instruction(): Instruction {
        return Instruction(
            program = if (legacy) TimelockProgram.legacyAddress else TimelockProgram.address,
            accounts = listOf(
                AccountMeta.writable(publicKey = timelock),
                AccountMeta.writable(publicKey = vault),

                AccountMeta.readonly(publicKey = vaultOwner, signer = true),
                AccountMeta.readonly(publicKey = timeAuthority, signer = true),

                AccountMeta.writable(publicKey = mint),
                AccountMeta.writable(publicKey = payer, signer = true),

                AccountMeta.readonly(publicKey = TokenProgram.address),
                AccountMeta.readonly(publicKey = SystemProgram.address),
            ),
            data = encode()
        )
    }

    override fun encode(): List<Byte> {
        val data = mutableListOf<Byte>()
        data.addAll(Command.burnDustWithAuthority.value.toByteArray().toList())
        data.add(bump)
        data.addAll(maxAmount.quarks.longToByteArray().toList())
        return data
    }

    companion object {
        fun newInstance(instruction: Instruction): TimelockProgram_BurnDustWithAuthority {
            val data = TimelockProgram.parse(
                command = Command.burnDustWithAuthority,
                instruction = instruction,
                expectingAccounts = 8
            )


            val bump = data.remaining.consume(1)
            val maxAmount = bump.remaining.consume(8).consumed.toByteArray().byteArrayToLong()

            return TimelockProgram_BurnDustWithAuthority(
                timelock = instruction.accounts[0].publicKey,
                vault = instruction.accounts[1].publicKey,
                vaultOwner = instruction.accounts[2].publicKey,
                timeAuthority = instruction.accounts[3].publicKey,
                mint = instruction.accounts[4].publicKey,
                payer = instruction.accounts[5].publicKey,
                bump = bump.consumed.first(),
                maxAmount = Kin.fromQuarks(maxAmount)
            )
        }
    }
}