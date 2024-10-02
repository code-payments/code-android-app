package com.getcode.solana.instructions.programs

import com.getcode.solana.AccountMeta
import com.getcode.solana.Instruction
import com.getcode.solana.instructions.InstructionType
import com.getcode.solana.keys.PublicKey

data class TimelockAccounts(val state: PublicKey, val vault: PublicKey)

    ///   Create an associated token account for the given wallet address and token mint
    ///   Accounts expected by this instruction:
    ///
    ///   0. `[writeable,signer]` Funding account (must be a system account)
    ///   1. `[writeable]` Associated token account address to be created
    ///   2. `[]` Wallet address for the new associated token account
    ///   3. `[]` The token mint for the new associated token account
    ///   4. `[]` System program
    ///   5. `[]` SPL Token program
    ///   6. `[]` Rent sysvar
    ///
    ///   Reference:
    ///   https://github.com/solana-labs/solana-program-library/blob/0639953c7dd0f5228c3ceda3ba68fece3b46ff1d/associated-token-account/program/src/lib.rs#L54
    ///

class AssociatedTokenProgram_CreateAccount(
    val subsidizer: PublicKey,
    val owner: PublicKey,
    val associatedTokenAccount: PublicKey,
    val mint: PublicKey
): InstructionType {
    override fun instruction(): Instruction {
        return Instruction(
            program = AssociatedTokenProgram.address,
            accounts = listOf(
                AccountMeta.writable(publicKey = subsidizer, signer = true),
                AccountMeta.writable(publicKey = associatedTokenAccount),
                AccountMeta.readonly(publicKey = owner),
                AccountMeta.readonly(publicKey = mint),
                AccountMeta.readonly(publicKey = SystemProgram.address),
                AccountMeta.readonly(publicKey = TokenProgram.address),
                AccountMeta.readonly(publicKey = SysVar.rent.address()),
            ),
            data = encode()
        )
    }

    override fun encode(): List<Byte> {
        return listOf()
    }

    companion object {
        fun newInstance(instruction: Instruction): AssociatedTokenProgram_CreateAccount {
            if (instruction.accounts.size != 7) throw Exception()

            val accounts = instruction.accounts.map { it.publicKey }

            return AssociatedTokenProgram_CreateAccount(
                subsidizer = accounts[0],
                owner = accounts[1],
                associatedTokenAccount = accounts[2],
                mint = accounts[3],
            )
        }
    }
}
