package com.flipcash.app.core.extensions

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import com.flipcash.core.resources.Res
import com.flipcash.core.resources.label_no
import com.flipcash.core.resources.label_yes

@Composable
fun Boolean.toYesOrNo() = if (this) stringResource(Res.string.label_yes) else stringResource(Res.string.label_no)