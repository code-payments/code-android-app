package com.getcode.opencode.model.financial

data class TokenResult(
    val token: Token,
    val source: DataSource,
)

enum class DataSource {
    Cache,
    Memory,
    Network,
    ;
}
