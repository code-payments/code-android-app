package com.flipcash.app.contact.verification.internal.phone

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.app.phone.CountryLocale
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.shared.phone.R
import com.getcode.theme.CodeTheme
import androidx.compose.foundation.clickable

@Composable
internal fun PhoneCountryCodeScreen(
    viewModel: PhoneVerificationViewModel
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    PhoneCountryCodeScreenContent(state, viewModel::dispatchEvent)
}

@Composable
private fun PhoneCountryCodeScreenContent(
    state: PhoneVerificationViewModel.State,
    dispatchEvent: (PhoneVerificationViewModel.Event) -> Unit,
) {
    PhoneCountrySelectionList(
        availableLocales = state.countryLocales,
        modifier = Modifier.fillMaxSize()
    ) {
        dispatchEvent(PhoneVerificationViewModel.Event.OnCountrySelected(it))
    }
}

@Composable
private fun PhoneCountrySelectionList(
    availableLocales: List<CountryLocale>,
    modifier: Modifier = Modifier,
    onSelection: (CountryLocale) -> Unit
) {
    LazyColumn(modifier) {
        items(availableLocales, key = { it.countryCode }) { countryCode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelection(countryCode) },
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
                    style = CodeTheme.typography.textMedium,
                    color = CodeTheme.colors.textMain
                )
                Text(
                    modifier = Modifier
                        .padding(CodeTheme.dimens.inset)
                        .align(Alignment.CenterVertically),
                    color = CodeTheme.colors.textSecondary,
                    text = "+${countryCode.phoneCode}",
                    style = CodeTheme.typography.textMedium,
                )
            }
            Divider(
                color = CodeTheme.colors.dividerVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
            )
        }
    }
}

@Preview
@Composable
private fun Preview_CountrySelection() {
    FlipcashPreview {
        Box(modifier = Modifier.fillMaxSize().background(CodeTheme.colors.background)) {
            PhoneCountrySelectionList(
                availableLocales = listOf(
                    CountryLocale(
                        name = "United States",
                        phoneCode = 1,
                        countryCode = "US",
                        resId = R.drawable.ic_flag_us,
                    ),
                    CountryLocale(
                        name = "United Kingdom",
                        phoneCode = 44,
                        countryCode = "GB",
                        resId = R.drawable.ic_flag_gb,
                    ),
                    CountryLocale(
                        name = "Canada",
                        phoneCode = 1,
                        countryCode = "CA",
                        resId = R.drawable.ic_flag_ca,
                    ),
                    CountryLocale(
                        name = "Australia",
                        phoneCode = 61,
                        countryCode = "AU",
                        resId = R.drawable.ic_flag_au,
                    ),
                    CountryLocale(
                        name = "New Zealand",
                        phoneCode = 64,
                        countryCode = "NZ",
                        resId = R.drawable.ic_flag_nz,
                    ),
                    CountryLocale(
                        name = "Singapore",
                        phoneCode = 65,
                        countryCode = "SG",
                        resId = R.drawable.ic_flag_sg,
                    ),
                    CountryLocale(
                        name = "Malaysia",
                        phoneCode = 60,
                        countryCode = "MY",
                        resId = R.drawable.ic_flag_my,
                    ),
                    CountryLocale(
                        name = "Philippines",
                        phoneCode = 63,
                        countryCode = "PH",
                        resId = R.drawable.ic_flag_ph,
                    ),
                    CountryLocale(
                        name = "Indonesia",
                        phoneCode = 62,
                        countryCode = "ID",
                        resId = R.drawable.ic_flag_id,
                    ),
                    CountryLocale(
                        name = "Vietnam",
                        phoneCode = 84,
                        countryCode = "VN",
                        resId = R.drawable.ic_flag_vn,
                    ),
                ),
                modifier = Modifier.fillMaxSize()
            ) {}
        }
    }
}