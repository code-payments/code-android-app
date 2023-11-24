package com.getcode.view.main.giveKin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.getcode.R
import com.getcode.theme.*
import com.getcode.view.components.SwipeableView

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun CurrencyList(
    uiModel: CurrencyUiModel,
    onUpdateCurrencySearchFilter: (String) -> Unit,
    onSelectedCurrencyChanged: (String) -> Unit,
    setCurrencySelectorVisible: (Boolean) -> Unit,
    onRecentCurrencyRemoved: (String) -> Unit,
) {
    Column(
        modifier = Modifier.imePadding()
    ) {
        TextField(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(bottom = 10.dp)
                .padding(horizontal = 15.dp),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = White50,
                ) },
            placeholder = { Text(
                stringResource(id = R.string.subtitle_searchCurrencies),
                style = MaterialTheme.typography.subtitle1.copy(
                    fontSize = 16.sp,
                )
            ) },
            trailingIcon = {
                if (uiModel.currencySearchText.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            onUpdateCurrencySearchFilter("")
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = null,
                            tint = White50,
                        )
                    }
                }
            },
            value = uiModel.currencySearchText,
            onValueChange = {
                onUpdateCurrencySearchFilter(it)
            },
            textStyle = MaterialTheme.typography.subtitle1.copy(
                fontSize = 16.sp,
            ),
            singleLine = true,
            colors = inputColors(),
            shape = RoundedCornerShape(size = 5.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brand)
                .wrapContentHeight()
        ) {
            items(uiModel.listItems) { listItem ->
                val isSelectable = listItem is CurrencyListItem.RegionCurrencyItem
                val isDisabled = listItem is CurrencyListItem.RegionCurrencyItem && listItem.currency.rate <= 0
                val currencyCode = when (listItem) {
                    is CurrencyListItem.RegionCurrencyItem -> listItem.currency.code
                    else -> ""
                }

                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(if (listItem !is CurrencyListItem.TitleItem) 70.dp else 60.dp)
                ) {
                    Divider(
                        color = White05,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .align(Alignment.BottomCenter)
                    )

                    when (listItem) {
                        is CurrencyListItem.TitleItem -> {
                            Row(modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(horizontal = 20.dp)
                            ) {
                                Text(
                                    modifier = Modifier.padding(bottom = 10.dp),
                                    style = MaterialTheme.typography.caption.copy(
                                        fontSize = 14.sp,
                                    ),
                                    color = BrandLight,
                                    text = listItem.text
                                )
                            }
                        }
                        is CurrencyListItem.RegionCurrencyItem -> {
                            SwipeableView(
                                isSwipeEnabled = listItem.isRecent,
                                leftSwiped = {
                                    onRecentCurrencyRemoved(listItem.currency.code)
                                },
                                leftSwipeCard = {
                                    if (listItem.isRecent) ListSwipeDeleteCard()
                                }
                            ) {
                                ListRowItem(
                                    listItem.currency.resId,
                                    listItem.currency.name,
                                    isSelectable,
                                    uiModel.selectedCurrencyCode.orEmpty() == currencyCode,
                                    isDisabled,
                                ) {
                                    onSelectedCurrencyChanged(currencyCode)
                                    setCurrencySelectorVisible(false)
                                }

                                Divider(
                                    color = White05,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .align(Alignment.BottomCenter)
                                )
                            }
                        }
                    }

                }
            }
        }
    }
}

@Composable
private fun ListSwipeDeleteCard() {
    Box(
        modifier = Modifier.background(TopError),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_delete),
            contentDescription = "",
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 20.dp)
                .size(30.dp),
        )
    }
}

@Composable
private fun ListRowItem(resId: Int?, title: String, isSelectable: Boolean, isSelected: Boolean, isDisabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brand)
            .let {
                if (isSelectable && !isDisabled) {
                    it.clickable { onClick() }
                } else it
            }
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.CenterStart)
                .alpha(if (isDisabled) 0.25f else 1.0f)
        ) {
            resId?.let {
                Image(
                    modifier = Modifier
                        .padding(end = 15.dp)
                        .size(30.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .align(Alignment.CenterVertically),
                    painter = painterResource(resId),
                    contentDescription = ""
                )
            }
            Column(
                modifier = Modifier
                    .wrapContentWidth()
                    .align(Alignment.CenterVertically),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.body1
                )
            }
        }

        if (isSelectable) {
            Image(
                modifier = Modifier
                    .wrapContentWidth()
                    .align(Alignment.CenterEnd)
                    .alpha(if (isDisabled) 0.25f else 1.0f),
                painter = painterResource(
                    if (isSelected)
                        R.drawable.ic_checked_blue else R.drawable.ic_unchecked
                ),
                contentDescription = ""
            )
        }
    }
}