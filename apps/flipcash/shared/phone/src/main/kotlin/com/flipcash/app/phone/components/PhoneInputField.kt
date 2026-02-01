package com.flipcash.app.phone.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.flipcash.app.phone.CountryLocale
import com.flipcash.app.phone.LocalPhoneUtils
import com.flipcash.app.phone.PhoneUtils
import com.flipcash.app.phone.components.transformations.PhoneNumberOutputTransformation
import com.flipcash.app.theme.FlipcashPreview
import com.flipcash.shared.phone.R
import com.getcode.opencode.compose.ExchangeStub
import com.getcode.opencode.compose.LocalExchange
import com.getcode.theme.CodeTheme
import com.getcode.ui.components.TextInput
import com.getcode.ui.components.VerticalDivider
import com.getcode.ui.core.rememberAnimationScale
import com.getcode.ui.core.rememberedClickable
import com.getcode.ui.core.scaled
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PhoneInputField(
    state: TextFieldState,
    openCountrySelector: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "",
    phoneUtils: PhoneUtils = LocalPhoneUtils.current,
    locale: CountryLocale = phoneUtils.defaultCountryLocale,
    focusManager: FocusManager = LocalFocusManager.current,
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    val outputTransformation = remember(phoneUtils, locale) {
        PhoneNumberOutputTransformation(phoneUtils, locale)
    }

    val animationScale by rememberAnimationScale()
    val composeScope = rememberCoroutineScope()
    TextInput(
        modifier = modifier
            .focusRequester(focusRequester),
        state = state,
        enabled = enabled,
        style = CodeTheme.typography.textLarge.copy(color = CodeTheme.colors.textMain),
        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
        onKeyboardAction = { onSubmit() },
        maxLines = 1,
        placeholder = placeholder,
        outputTransformation = outputTransformation,
        leadingIcon = {
            Row {
                Row(
                    modifier = Modifier
                        .height(CodeTheme.dimens.grid.x12)
                        .rememberedClickable {
                            composeScope.launch {
                                focusManager.clearFocus(true)
                                delay(300.scaled(animationScale))
                                openCountrySelector()
                            }
                        },
                ) {
                    locale.resId?.let { resId ->
                        Image(
                            modifier = Modifier
                                .align(CenterVertically)
                                .padding(start = CodeTheme.dimens.grid.x3)
                                .size(CodeTheme.dimens.staticGrid.x5)
                                .clip(CodeTheme.shapes.large),
                            painter = painterResource(resId),
                            contentDescription = "",
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(vertical = CodeTheme.dimens.border)
                            .padding(horizontal = CodeTheme.dimens.grid.x3,),
                        contentAlignment = Center
                    ) {
                        Text(
                            modifier = Modifier.padding(top = CodeTheme.dimens.border),
                            style = CodeTheme.typography.textLarge,
                            color = CodeTheme.colors.textMain,
                            text = "+${locale.phoneCode}"
                        )
                    }
                }
                VerticalDivider(
                    color = CodeTheme.colors.border
                )
            }
        }
    )
}

@Preview
@Composable
private fun Preview_PhoneEntry() {
    FlipcashPreview {
        val exchange = ExchangeStub(
            providedRates = emptyMap(),
            context = LocalContext.current
        )

        val phoneUtils = PhoneUtils(LocalContext.current, exchange).apply {
            defaultCountryLocale = CountryLocale(
                name = "United States",
                1,
                " US",
                R.drawable.ic_flag_us
            )
        }

        CompositionLocalProvider(
            LocalExchange provides exchange,
            LocalPhoneUtils provides phoneUtils,
        ) {
            Box(
                modifier = Modifier
                    .background(CodeTheme.colors.background)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
                ) {
                    PhoneInputField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CodeTheme.dimens.inset)
                            .height(CodeTheme.dimens.grid.x12),
                        focusManager = LocalFocusManager.current,
                        focusRequester = remember { FocusRequester() },
                        placeholder = "Phone Number",
                        state = remember { TextFieldState() },
                        openCountrySelector = {},
                        onSubmit = {}
                    )

                    PhoneInputField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = CodeTheme.dimens.inset)
                            .height(CodeTheme.dimens.grid.x12),
                        focusManager = LocalFocusManager.current,
                        focusRequester = remember { FocusRequester() },
                        placeholder = "Phone Number",
                        state = remember { TextFieldState(initialText = "5869802333") },
                        openCountrySelector = {},
                        onSubmit = {}
                    )
                }
            }
        }
    }
}