package com.getcode.view.main.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.getcode.manager.SessionManager
import com.getcode.solana.keys.base58
import com.getcode.solana.organizer.AccountType
import com.getcode.theme.BrandLight
import com.getcode.theme.BrandLight
import com.getcode.view.components.MiddleEllipsisText


@Composable
fun AccountDebugBuckets() {
    val accountInfo = SessionManager.getOrganizer()?.getAccountInfo()?.values?.toList() ?: return

    val accountList = accountInfo.toList().sortedBy {
        when (it.accountType) {
            AccountType.Primary -> 0
            AccountType.Incoming -> 1
            AccountType.Outgoing -> 2
            is AccountType.Bucket -> (it.accountType as AccountType.Bucket).type.ordinal + 3
            AccountType.RemoteSend -> 100
        }
    }

    LazyColumn {
        items(accountList) { info ->
            val name = when (val accountType = info.accountType) {
                is AccountType.Bucket -> accountType.type.name.replace("Bucket", "")
                AccountType.Incoming -> "Incoming ${info.index}"
                AccountType.Outgoing -> "Outgoing ${info.index}"
                AccountType.Primary -> "Primary"
                AccountType.RemoteSend -> "Remote Send"
            }

            Column(
                modifier = Modifier
                    .padding(horizontal = 15.dp)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 5.dp)
                ) {
                    Text(
                        modifier = Modifier
                            .weight(1f),
                        text = name,
                        style = MaterialTheme.typography.body1,
                    )
                }

                Row(
                    modifier = Modifier.padding(vertical = 5.dp)
                ) {
                    MiddleEllipsisText(
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .weight(1f)
                            .padding(end = 100.dp),
                        text = info.address.base58(),
                        color = BrandLight,
                        style = MaterialTheme.typography.body2
                    )

                    val kinValue = info.balance.toKinValueDouble()
                    val format = if (kinValue % 1 == 0.0) { "%,.0f" } else { "%,.5f" }

                    Text(
                        text = "K ${String.format(format, info.balance.toKinValueDouble())}",
                        color = BrandLight,
                        style = MaterialTheme.typography.body2
                    )
                }

                Row(
                    modifier = Modifier.padding(vertical = 5.dp)
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        text = "${info.managementState.name}  •  ${info.blockchainState.name}",
                        color = BrandLight,
                        style = MaterialTheme.typography.body2
                    )
                    Text(
                        text = "",
                        color = BrandLight,
                        style = MaterialTheme.typography.body2
                    )
                }

                Spacer(modifier = Modifier
                    .padding(vertical = 10.dp)
                    .background(BrandLight)
                    .fillMaxWidth()
                    .height(1.dp))
            }
        }
    }
}