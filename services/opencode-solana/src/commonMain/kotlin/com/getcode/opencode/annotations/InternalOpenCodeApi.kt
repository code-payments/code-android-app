package com.getcode.opencode.annotations

/**
 * Marks declarations that were `internal` to `:services:opencode` before the Solana and model code
 * moved into their own modules. They are public only so `:services:opencode-solana`,
 * `:services:opencode-model` and `:services:opencode` can share them; app code should not use them.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "Internal to the OpenCode service modules.",
)
@Retention(AnnotationRetention.BINARY)
annotation class InternalOpenCodeApi
