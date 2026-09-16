package com.flipcash.app.lab.internal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.codeinc.flipcash.gen.Flipcash2ContractInfo
import com.codeinc.opencode.gen.OcpContractInfo
import com.getcode.theme.CodeTheme

/**
 * One backend contract package, as the build carries it.
 *
 * [commit] is the upstream proto commit the package was generated from, already shortened by the
 * package itself so both platforms truncate the same way. A package built from a local proto sync
 * reports `LOCAL` instead of a SHA, which is what [isLocal] flags -- that build's contract is not
 * reproducible from anything published, so it is worth seeing at a glance.
 */
internal data class ContractInfo(
    val name: String,
    val version: String,
    val commit: String,
    val isLocal: Boolean,
) {
    val detail: String get() = "$version · $commit"
}

internal fun contractInfo(): List<ContractInfo> = listOf(
    ContractInfo(
        name = "ocp",
        version = OcpContractInfo.VERSION,
        commit = OcpContractInfo.shortProtoCommit,
        isLocal = OcpContractInfo.isLocal,
    ),
    ContractInfo(
        name = "flipcash2",
        version = Flipcash2ContractInfo.VERSION,
        commit = Flipcash2ContractInfo.shortProtoCommit,
        isLocal = Flipcash2ContractInfo.isLocal,
    ),
)

@Composable
internal fun ContractInfoRow(
    info: ContractInfo,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = CodeTheme.dimens.inset, vertical = CodeTheme.dimens.grid.x2),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = info.name,
            style = CodeTheme.typography.textMedium,
            color = CodeTheme.colors.textMain,
        )
        Text(
            text = info.detail,
            style = CodeTheme.typography.textSmall,
            color = if (info.isLocal) CodeTheme.colors.warning else CodeTheme.colors.textSecondary,
        )
    }
}
