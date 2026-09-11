package com.getcode.opencode.internal.solana.utils

import com.getcode.opencode.solana.SolanaTransaction
import com.getcode.solana.keys.base58

/**
 * Diagnostic diff between two [SolanaTransaction]s, printed via [printDiff]/[printMatch]. Kept in
 * `:services:opencode` rather than moving with [SolanaTransaction] into `:libs:solana:encoding`
 * because `printDiff`/`printMatch` depend on `timber.log.Timber`, which is Android-only. Called
 * from nowhere in the codebase today; kept rather than deleted per standing decision.
 *
 * The header line is formatted inline here (matching `MessageHeader.description`'s
 * `"H{...}"` shape) rather than calling that extension: it is `internal` inside
 * `:libs:solana:encoding` now that `MessageHeader` lives there, and this function is the only
 * caller, so inlining avoids widening that visibility for one debug string.
 */
internal fun SolanaTransaction.diff(other: SolanaTransaction) {
    val lhs = this
    val rhs = other

    if (lhs.identifier == rhs.identifier) {
        printMatch("ID")
    } else {
        printDiff(
            title = "ID",
            one = lhs.identifier.base58(),
            two = rhs.identifier.base58()
        )
    }

    if (lhs.signatures == rhs.signatures) {
        printMatch("Signatures")
    } else {
        printDiff(
            title = "Signatures",
            one = lhs.signatures.map { it.base58() },
            two = rhs.signatures.map { it.base58() }
        )
    }

    if (lhs.message.header == rhs.message.header) {
        printMatch("Header")
    } else {
        printDiff(
            title = "Header",
            one = lhs.message.header.let { "H{${it.requiredSignatures}, ${it.readOnlySigners}, ${it.readOnly}}" },
            two = rhs.message.header.let { "H{${it.requiredSignatures}, ${it.readOnlySigners}, ${it.readOnly}}" },
        )
    }

    if (lhs.message.recentBlockhash == rhs.message.recentBlockhash) {
        printMatch("Recent Blockhash")
    } else {
        printDiff(
            title = "Recent Blockhash",
            one = lhs.recentBlockhash.base58(),
            two = rhs.recentBlockhash.base58(),
        )
    }

    if (lhs.message.accountKeys== rhs.message.accountKeys) {
        printMatch("Accounts")
    } else {
        printDiff(
            title = "Accounts",
            one = lhs.message.accountKeys.map { it.description },
            two = rhs.message.accountKeys.map { it.description },
        )
    }

    if (lhs.message.instructions == rhs.message.instructions) {
        printMatch("Instructions")
    } else {
        printDiff(
            title = "Instructions",
            one = lhs.message.instructions.map { it.description },
            two = rhs.message.instructions.map { it.description },
        )
    }
}
