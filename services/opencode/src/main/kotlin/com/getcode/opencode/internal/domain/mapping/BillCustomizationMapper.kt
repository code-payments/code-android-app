package com.getcode.opencode.internal.domain.mapping

import com.codeinc.opencode.gen.currency.v1.CurrencyService
import com.getcode.opencode.mapper.Mapper
import com.getcode.opencode.model.ui.BillBackground
import com.getcode.opencode.model.ui.TokenBillCustomizations
import javax.inject.Inject

internal class BillCustomizationMapper @Inject constructor(): Mapper<CurrencyService.BillCustomization?, TokenBillCustomizations?> {
    override fun map(from: CurrencyService.BillCustomization?): TokenBillCustomizations? {
        if (from == null) return null
        val colors = from.colorsList.map { it.hex }
        return TokenBillCustomizations(
            background = BillBackground.Gradient(colors),
            texture = null,
            icon = null,
        )
    }
}