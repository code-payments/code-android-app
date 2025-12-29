package com.getcode.opencode.internal.solana.programs

import com.getcode.opencode.internal.solana.extensions.toU16Bytes
import com.getcode.opencode.solana.Instruction
import com.getcode.solana.keys.AccountMeta
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.bytes
import com.getcode.utils.toByteArray
import org.kin.sdk.base.tools.intToByteArray
import org.kin.sdk.base.tools.longToByteArray

@Suppress("ClassName")
internal class CurrencyCreatorProgram_BuyAndDepositIntoVm(
    private val amount: Long,
    private val minOutput: Long,
    private val vmMemoryIndex: Int, // UInt16

    private val buyer: PublicKey,
    private val pool: PublicKey,
    private val currency: PublicKey,
    private val targetMint: PublicKey,
    private val baseMint: PublicKey,
    private val vaultTarget: PublicKey,
    private val vaultBase: PublicKey,
    private val buyerBase: PublicKey,
    private val feeTarget: PublicKey,
    private val feeBase: PublicKey,

    private val vmAuthority: PublicKey,
    private val vm: PublicKey,
    private val vmMemory: PublicKey,
    private val vmOmnibus: PublicKey,
    private val vtaOwner: PublicKey,
) : InstructionType {

    override fun instruction(): Instruction {
        return Instruction(
            program = CurrencyCreatorProgram.address,
            accounts = listOf(
                AccountMeta.writable(publicKey = buyer, signer = true),
                AccountMeta.writable(publicKey = pool),
                AccountMeta.writable(publicKey = currency),
                AccountMeta.writable(publicKey = targetMint),

                AccountMeta.readonly(publicKey = baseMint),

                AccountMeta.writable(publicKey = vaultTarget),
                AccountMeta.writable(publicKey = vaultBase),
                AccountMeta.writable(publicKey = buyerBase),
                AccountMeta.writable(publicKey = feeTarget),

                AccountMeta.readonly(publicKey = feeBase),

                AccountMeta.writable(publicKey = vmAuthority, signer = true),
                AccountMeta.writable(publicKey = vm),
                AccountMeta.writable(publicKey = vmMemory),
                AccountMeta.writable(publicKey = vmOmnibus),

                AccountMeta.readonly(publicKey = vtaOwner),
                AccountMeta.readonly(publicKey = TokenProgram.address),
                AccountMeta.readonly(publicKey = VirtualMachineProgram.address),
            ),
            data = encode()
        )
    }

    override fun encode(): List<Byte> {
        val data = mutableListOf<Byte>()
        data.add(CurrencyCreatorProgram.Command.buyAndDepositIntoVm.value)
        data.addAll(amount.bytes)
        data.addAll(minOutput.bytes)
        data.addAll(vmMemoryIndex.toU16Bytes())

        return data
    }
}