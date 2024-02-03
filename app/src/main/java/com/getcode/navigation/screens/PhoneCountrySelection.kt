package com.getcode.navigation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.getcode.theme.BrandLight
import com.getcode.theme.CodeTheme
import com.getcode.theme.White05
import com.getcode.util.PhoneUtils
import com.getcode.util.rememberedClickable
import com.getcode.view.login.PhoneVerifyUiModel
import com.getcode.view.login.PhoneVerifyViewModel

@Composable
fun PhoneCountrySelection(
    viewModel: PhoneVerifyViewModel,
    onSelection: () -> Unit = { },
) {
    val state by viewModel.uiFlow.collectAsState()
    PhoneCountrySelection(state = state) {
        viewModel.setCountryCode(it)
        onSelection()
    }
}

@Composable
private fun PhoneCountrySelection(
   state: PhoneVerifyUiModel,
   onSelection: (PhoneUtils.CountryLocale) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(state.countryLocalesFiltered) { countryCode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .rememberedClickable { onSelection(countryCode) },
            ) {
                countryCode.resId?.let { resId ->
                    Image(
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .padding(start = CodeTheme.dimens.inset)
                            .size(CodeTheme.dimens.grid.x5)
                            .clip(CodeTheme.shapes.large),
                        painter = painterResource(id = resId),
                        contentDescription = ""
                    )
                }
                Text(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = CodeTheme.dimens.inset)
                        .padding(start = CodeTheme.dimens.inset)
                        .align(Alignment.CenterVertically),
                    text = countryCode.name,
                    style = CodeTheme.typography.body1.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    modifier = Modifier
                        .padding(CodeTheme.dimens.inset)
                        .align(Alignment.CenterVertically),
                    color = BrandLight,
                    text = "+${countryCode.phoneCode}",
                    style = CodeTheme.typography.body1.copy(fontWeight = FontWeight.Bold)
                )
            }
            Divider(
                color = White05,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
            )
        }
    }
}