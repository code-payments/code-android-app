package com.getcode.util.resources

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

@Composable
actual fun stringRes(id: Int): String = stringResource(id)

@Composable
actual fun stringRes(id: Int, vararg formatArgs: Any): String = stringResource(id, *formatArgs)

@Composable
actual fun pluralStringRes(id: Int, count: Int, vararg formatArgs: Any): String =
    pluralStringResource(id, count, *formatArgs)
