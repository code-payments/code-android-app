package com.getcode.solana.instructions.programs

import com.getcode.utils.DataSlice.toLong
import com.getcode.vendor.Base58

class TimelockProgram {

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
    companion object: com.getcode.solana.instructions.CommandType<Command>() {
        override val address = com.getcode.solana.keys.PublicKey(
            Base58.decode("time2Z2SCnn3qYg3ULKVtdkh8YmZ5jFdKicnA1W2YnJ").toList()
        )
        val legacyAddress = com.getcode.solana.keys.PublicKey(
            Base58.decode("timeDBoQGL52du9K7EtrhkJSqpiFapE9dHrmDVkuZx6").toList()
        )

        override val commandByteLength: Int get() = Long.SIZE_BYTES

        override fun commandLookup(bytes: ByteArray): Command? {
            return Command.entries.firstOrNull { it.value == bytes.toLong() }
        }
    }
}