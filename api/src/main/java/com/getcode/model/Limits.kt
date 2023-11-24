package com.getcode.model

data class Limits(
    /// Maximum quarks that may be deposited at any time. Server will guarantee
    /// this threshold will be below enforced dollar value limits, while also
    /// ensuring sufficient funds are available for a full organizer that supports
    /// max payment sends. Total dollar value limits may be spread across many deposits.
    val maxDeposit: Kin,

    /// Remaining send limits keyed by currency
    val map: Map<CurrencyCode, Double>,
    ) {
    companion object {
        fun newInstance(map: Map<String, Double>, maxDeposit: Kin): Limits {
            val limitsMap =
                map.mapNotNull {
                    val currencyCode = CurrencyCode.tryValueOf(it.key) ?: return@mapNotNull null
                    Pair(currencyCode, it.value)
                }.toMap()
            return Limits(maxDeposit, limitsMap)
        }
    }
}