package com.getcode.solana.transactions

import com.getcode.solana.keys.AccountMeta.Companion.compareLexicographically
import com.solana.publickey.SolanaPublicKey
import com.solana.transaction.AccountMeta
import com.solana.transaction.AddressTableLookup
import com.solana.transaction.Instruction
import com.solana.transaction.LegacyMessage
import com.solana.transaction.Message
import com.solana.transaction.VersionedMessage
import com.solana.transaction.toUnsignedTransaction

fun Message.Builder.buildPatched(): Message {
    check(blockhash != null)
    val writableIndexes = mutableListOf<UByte>()
    val readableIndexes = mutableListOf<UByte>()

    val writableSigners = mutableSetOf<SolanaPublicKey>()
    val readOnlySigners = mutableSetOf<SolanaPublicKey>()
    val writableNonSigners = mutableSetOf<SolanaPublicKey>()
    val readOnlyNonSigners = mutableSetOf<SolanaPublicKey>()
    val programIds = mutableSetOf<SolanaPublicKey>()

    instructions.forEach { instruction ->
        instruction.accounts.forEachIndexed { index, account ->
            if (account.isSigner) {
                if (account.isWritable) writableSigners.add(account.publicKey)
                else {
                    readOnlySigners.add(account.publicKey)
                }
            } else {
                if (account.isWritable) writableNonSigners.add(account.publicKey)
                else readOnlyNonSigners.add(account.publicKey)
            }

            if (account.isWritable) {
                writableIndexes += index.toUByte()
            } else {
                readableIndexes += index.toUByte()
            }
        }
        programIds.add(instruction.programId)
    }

    val signers = writableSigners + readOnlySigners

    // add program ids after everything else
    val accounts = signers + writableNonSigners + readOnlyNonSigners + programIds

    val compiledInstructions = instructions.map { instruction ->
        Instruction(
            accounts.indexOfFirst { it == instruction.programId }.toUByte(),
            accounts.map { account ->
                accounts.indexOfFirst { it == account }.toUByte()
            }.toUByteArray().toByteArray(),
            instruction.data
        )
    }

    return LegacyMessage(
        signers.size.toUByte(),
        readOnlySigners.size.toUByte(),
        readOnlyNonSigners.size.toUByte(),
        accounts.toList(),
        blockhash!!,
        compiledInstructions
    )
}

fun Message.inspect() {
    val transaction = toUnsignedTransaction()
    println("############################")
    println("Inspecting Transaction")
    println("Recent Blockhash: ${transaction.message.blockhash}")
    println("Signature Count: ${transaction.message.signatureCount}")
    println("Read-Only Signers: ${transaction.message.readOnlyAccounts}")
    println("Read-Only Non-Signers: ${transaction.message.readOnlyNonSigners}")

    // Infer account flags from Message structure
    val totalAccounts = transaction.message.accounts.size
    val writableSignersCount = transaction.message.signatureCount.toInt() - transaction.message.readOnlyAccounts.toInt()
    val readOnlySignersStart = writableSignersCount
    val readOnlySignersEnd = readOnlySignersStart + transaction.message.readOnlyAccounts.toInt()
    val writableNonSignersEnd = totalAccounts - transaction.message.readOnlyNonSigners.toInt()

    // Inspect instructions
    println("\nInstructions:")
    transaction.message.instructions.forEachIndexed { index, instruction ->
        println("  Instruction $index:")
        println("    Program ID Index: ${instruction.programIdIndex}")
        val programId = transaction.message.accounts.getOrNull(instruction.programIdIndex.toInt())
        if (programId == null) {
            println("    ERROR: Invalid program ID index ${instruction.programIdIndex} (out of bounds for $totalAccounts accounts)")
        } else {
            println("    Program ID: $programId")
        }
        println("    Account Indices: ${instruction.accountIndices.joinToString { it.toInt().toString() }}")
        println("    Instruction Data: ${instruction.data.joinToString { it.toUByte().toString(16).padStart(2, '0') }}")
        // Validate account indices
        instruction.accountIndices.forEachIndexed { accIndex, indexByte ->
            val accIndexInt = indexByte.toInt()
            if (accIndexInt < 0 || accIndexInt >= totalAccounts) {
                println("    ERROR: Invalid account index $accIndexInt at position $accIndex (out of bounds for $totalAccounts accounts)")
            } else {
                val account = transaction.message.accounts[accIndexInt]
                val isSigner = accIndexInt < transaction.message.signatureCount.toInt()
                val isWritable = accIndexInt < writableNonSignersEnd
                println("    Account Index $accIndexInt: $account (isSigner=$isSigner, isWritable=$isWritable)")
            }
        }
    }
    println("############################")
}

// Provide a unique set by publicKey of AccountMeta
// with the highest write permission.
private fun List<AccountMeta>.filterUniqueAccounts(): List<AccountMeta> {
    val container = mutableListOf<AccountMeta>()
    this.forEach { account ->
        var found = false
        var index = 0

        for (existingAccount in container) {
            if (account.publicKey == existingAccount.publicKey) {
                val updatedAccount = existingAccount.copy(
                    isSigner = account.isSigner,
                    isWritable = account.isWritable
                )

                container[index] = updatedAccount
                found = true
                break
            }
            index++
        }

        if (!found) {
            container.add(account)
        }
    }

    return container
}

private val AccountMetaComparator : Comparator<AccountMeta> = Comparator { left, right ->
    fun boolToInt(value: Boolean) = if (value) -1 else 1

    if (left.isSigner != right.isSigner) {
        return@Comparator boolToInt(left.isSigner)
    }

    if (left.isWritable != right.isWritable) {
        return@Comparator boolToInt(left.isWritable)
    }

    compareLexicographically(left.publicKey.bytes, right.publicKey.bytes)
}