package com.getcode.solana.instructions.programs

import com.getcode.solana.keys.PublicKey
import com.getcode.vendor.Base58

///   Send tokens from one accounts to another. Accounts expected by this instruction:
///
///   ## Single owner/delegate
///
///   0. `[writable]` The source account.
///   1. `[writable]` The destination account.
///   2. `[signer]` The source account's owner/delegate.
///
///   ## Multisignature owner/delegate
///
///   0. `[writable]` The source account.
///   1. `[writable]` The destination account.
///   2. `[]` The source account's multisignature owner/delegate.
///   3. ..3+M `[signer]` M signer accounts.
///
///   Reference:
///   https://github.com/solana-labs/solana-program-library/blob/b011698251981b5a12088acba18fad1d41c3719a/token/program/src/instruction.rs#L76-L91
///

open class MemoProgram(val data: List<Byte>) {
    companion object {
        val address = PublicKey(Base58.decode("Memo1UhkJRfHyvLMcVucJwxXeuD728EqVDDwQDxFMNo").toList())
    }
}