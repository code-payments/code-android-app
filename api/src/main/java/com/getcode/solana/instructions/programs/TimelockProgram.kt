package com.getcode.solana.instructions.programs

import com.getcode.solana.Instruction
import com.getcode.solana.keys.PublicKey
import com.getcode.utils.DataSlice.consume
import com.getcode.vendor.Base58

class TimelockProgram {
    companion object {
        val address = PublicKey(Base58.decode("time2Z2SCnn3qYg3ULKVtdkh8YmZ5jFdKicnA1W2YnJ").toList())
        val legacyAddress = PublicKey(Base58.decode("timeDBoQGL52du9K7EtrhkJSqpiFapE9dHrmDVkuZx6").toList())

        enum class Command(val value: Long) {
            initialize               ("ED9B980D1F6DAFAF".toULong(16).toLong()),
            activate                 ("52AA37976423CBC2".toULong(16).toLong()),
            transferWithAuthority    ("A5474581C0DE8044".toULong(16).toLong()),
            revokeLockWithAuthority  ("90C908ABF23AB5E5".toULong(16).toLong()),
            deactivateLock           ("0D8E1C71AC21702C".toULong(16).toLong()),
            withdraw                 ("22A16D949C4612B7".toULong(16).toLong()),
            closeAccounts            ("01CAFA22E95EDEAB".toULong(16).toLong()),
            burnDustWithAuthority    ("2D4E7C0EDAFF2A27".toULong(16).toLong()),
        }

        fun parse(instruction: Instruction, expectingAccounts: Int): List<Byte> {
            return instruction.data.consume(8).remaining
        }
    }
}