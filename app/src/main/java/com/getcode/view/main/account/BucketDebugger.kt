package com.getcode.view.main.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.getcode.manager.SessionManager
import com.getcode.model.displayName
import com.getcode.solana.keys.base58
import com.getcode.solana.organizer.AccountType
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.MiddleEllipsisText
import com.getcode.ui.utils.rememberedClickable


@Composable
fun BucketDebugger() {
    val accountList = SessionManager.getOrganizer()?.buckets ?: return

    val buckets = accountList.sortedWith { lhs, rhs ->
        val la = lhs.accountType
        val ra = rhs.accountType
        if (la is AccountType.Relationship && ra is AccountType.Relationship) {
            la.domain.urlString.compareTo(ra.domain.urlString)
        } else {
            la.sortOrder().compareTo(ra.sortOrder())
        }
    }

    val clipboard = LocalClipboardManager.current

    LazyColumn {
        items(buckets) { info ->
            Column(
                modifier = Modifier
                    .rememberedClickable {
                        clipboard.setText(AnnotatedString(info.address.base58()))
                    }
                    .padding(horizontal = CodeTheme.dimens.grid.x3)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = CodeTheme.dimens.grid.x1)
                ) {
                    Text(
                        modifier = Modifier
                            .weight(1f),
                        text = info.displayName,
                        style = CodeTheme.typography.textMedium,
                    )
                }

                Row(
                    modifier = Modifier.padding(vertical = CodeTheme.dimens.grid.x1)
                ) {
                    MiddleEllipsisText(
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .weight(1f)
                            .padding(end = 100.dp),
                        text = info.address.base58(),
                        color = CodeTheme.colors.textSecondary,
                        style = CodeTheme.typography.textSmall
                    )

                    val kinValue = info.balance.toKinValueDouble()
                    val format = if (kinValue % 1 == 0.0) { "%,.0f" } else { "%,.5f" }

                    Text(
                        text = "K ${String.format(format, info.balance.toKinValueDouble())}",
                        color = CodeTheme.colors.textSecondary,
                        style = CodeTheme.typography.textSmall
                    )
                }

                Row(
                    modifier = Modifier.padding(vertical = CodeTheme.dimens.grid.x1)
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = "${info.managementState.name}  •  ${info.blockchainState.name}",
                        color = CodeTheme.colors.textSecondary,
                        style = CodeTheme.typography.textSmall
                    )
                    Text(
                        text = "",
                        color = CodeTheme.colors.textSecondary,
                        style = CodeTheme.typography.textSmall
                    )
                }

                Spacer(modifier = Modifier
                    .padding(vertical = CodeTheme.dimens.grid.x2)
                    .background(BrandLight)
                    .fillMaxWidth()
                    .height(1.dp))
            }
        }
    }
}