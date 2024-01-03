package com.getcode.util

import android.content.Context
import androidx.compose.runtime.Composable
import com.getcode.model.KinAmount
import com.getcode.util.resources.ResourceHelper
import com.getcode.utils.FormatUtils


@Composable
fun KinAmount.formatted(round: Boolean = true) = if (round) {
    FormatUtils.formatWholeRoundDown(kin.toKin().toDouble())
} else {
    rate.currency.format(this)
}

fun KinAmount.formatted(resources: ResourceHelper, round: Boolean = true) = if (round) {
    FormatUtils.formatWholeRoundDown(kin.toKin().toDouble())
} else {
    rate.currency.format(resources, this)
}