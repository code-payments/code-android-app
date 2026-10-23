package com.getcode.opencode.internal.domain.mapping

import com.codeinc.opencode.gen.currency.v1.OcpCurrencyService
import com.getcode.opencode.mapper.Mapper
import com.getcode.opencode.model.ui.BillBackground
import com.getcode.opencode.model.ui.TokenBillCustomizations
import javax.inject.Inject

internal class BillCustomizationMapper @Inject constructor(): Mapper<OcpCurrencyService.BillCustomization?, TokenBillCustomizations?> {
    override fun map(from: OcpCurrencyService.BillCustomization?): TokenBillCustomizations? {
        if (from == null) return null
        val colors = from.colorsList.map { it.hex }
        val background = if (colors.count() == 1) {
            BillBackground.Solid(colors.first())
        } else {
            BillBackground.Gradient(colors)
        }
        return TokenBillCustomizations(
            background = background,
            texture = null,
            icon = null,
        )
    }
}