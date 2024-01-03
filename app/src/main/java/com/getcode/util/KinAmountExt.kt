package com.getcode.util

import com.getcode.model.KinAmount
import com.getcode.utils.FormatUtils

fun KinAmount.formatted(round: Boolean = true) = if (round) {
    FormatUtils.formatWholeRoundDown(kin.toKin().toDouble())
} else {
    rate.currency.format(this)
}