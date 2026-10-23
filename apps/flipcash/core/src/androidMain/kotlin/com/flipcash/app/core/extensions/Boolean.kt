package com.flipcash.app.core.extensions

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.flipcash.core.R

@Composable
fun Boolean.toYesOrNo() = if (this) stringResource(R.string.label_yes) else stringResource(R.string.label_no)