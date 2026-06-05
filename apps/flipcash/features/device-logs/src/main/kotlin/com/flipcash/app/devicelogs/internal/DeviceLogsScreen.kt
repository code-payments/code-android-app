package com.flipcash.app.devicelogs.internal

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flipcash.core.R
import com.getcode.theme.CodeTheme
import com.getcode.ui.core.verticalScrollStateGradient
import com.getcode.ui.theme.CodeScaffold
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

@Composable
internal fun DeviceLogsScreen(viewModel: DeviceLogsScreenViewModel) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    DeviceLogsScreenContent(state = state, dispatch = viewModel::dispatchEvent)
}

@Composable
private fun DeviceLogsScreenContent(
    state: DeviceLogsScreenViewModel.State,
    dispatch: (DeviceLogsScreenViewModel.Event) -> Unit,
) {
    val listState = rememberLazyListState()

    // `state` is a plain parameter value, so reading `state.visibleLines.size`
    // from inside snapshotFlow observes nothing — snapshotFlow only re-emits
    // when a read `State<T>` changes. Funnel it through rememberUpdatedState so
    // each new composition's state becomes visible to the running effect.
    val currentState by rememberUpdatedState(state)

    // With reverseLayout the bottom of the list is index 0. Auto-scroll to
    // the newest line only when the user is already near the bottom.
    val isAtBottom by remember {
        derivedStateOf { listState.firstVisibleItemIndex <= 2 }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            Triple(currentState.visibleLines.size, currentState.isPaused, isAtBottom)
        }
            .distinctUntilChanged { old, new -> old.first == new.first }
            .filter { (size, isPaused, atBottom) -> size > 0 && !isPaused && atBottom }
            .collect {
                listState.animateScrollToItem(0)
            }
    }

    CodeScaffold(
        topBar = {
            FilterRow(
                filter = state.filter,
                onFilterChanged = { dispatch(DeviceLogsScreenViewModel.Event.OnFilterChanged(it)) },
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = CodeTheme.dimens.grid.x3),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PauseResumePill(
                    isPaused = state.isPaused,
                    pausedBacklog = state.pausedBacklog,
                    onClick = { dispatch(DeviceLogsScreenViewModel.Event.TogglePause) },
                )
            }
        }
    ) { padding ->
        LogList(
            lines = state.visibleLines,
            filter = state.filter,
            listState = listState,
            contentPadding = padding,
        )
    }
}

@Composable
private fun FilterRow(
    filter: String,
    onFilterChanged: (String) -> Unit,
) {
    val textColor = Color.White
    BasicTextField(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = CodeTheme.dimens.inset,
                vertical = CodeTheme.dimens.grid.x2,
            )
            .background(
                color = CodeTheme.colors.brandLight,
                shape = RoundedCornerShape(CodeTheme.dimens.grid.x2),
            )
            .padding(
                horizontal = CodeTheme.dimens.grid.x3,
                vertical = CodeTheme.dimens.grid.x2,
            ),
        value = filter,
        onValueChange = onFilterChanged,
        singleLine = true,
        textStyle = TextStyle(color = textColor, fontSize = 14.sp),
        cursorBrush = SolidColor(textColor),
        decorationBox = { innerTextField ->
            if (filter.isEmpty()) {
                Text(
                    text = stringResource(R.string.hint_filterLogs),
                    color = textColor.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            innerTextField()
        },
    )
}

@Composable
private fun LogList(
    lines: List<String>,
    filter: String,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    // SelectionContainer allows the user to select/copy text spanning multiple
    // log lines. LazyColumn only renders visible items, so selection is limited
    // to what's currently on screen — but that's the standard Compose behavior
    // and matches user expectations for a live log viewer.
    SelectionContainer(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollStateGradient(listState),
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding() + CodeTheme.dimens.grid.x2,
                bottom = contentPadding.calculateBottomPadding(),
                start = CodeTheme.dimens.inset,
                end = CodeTheme.dimens.inset,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(
                items = lines.asReversed(),
                key = { it.hashCode() },
            ) { line ->
                LogLine(line = line, filter = filter)
            }
        }
    }
}

@Composable
private fun LogLine(line: String, filter: String) {
    val color = when {
        line.contains("[E]") -> Color(0xFFFF6B6B)
        line.contains("[W]") -> Color(0xFFFFB74D)
        line.contains("[I]") -> Color.White
        line.contains("[A]") -> Color(0xFFFF6B6B)
        else -> Color.White.copy(alpha = 0.6f)
    }
    val annotated = remember(line, filter) { highlightMatches(line, filter) }
    Text(
        modifier = Modifier.fillMaxWidth(),
        text = annotated,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        overflow = TextOverflow.Visible,
        softWrap = true,
    )
}

/**
 * Builds an [AnnotatedString] that highlights every case-insensitive occurrence
 * of [filter] within [line]. If the filter is blank, returns the line unadorned.
 */
private fun highlightMatches(line: String, filter: String): AnnotatedString {
    if (filter.isBlank()) return AnnotatedString(line)
    val highlightStyle = SpanStyle(
        background = Color(0xFFFFEB3B).copy(alpha = 0.35f),
        color = Color.White,
        fontWeight = FontWeight.Bold,
    )
    return buildAnnotatedString {
        append(line)
        var index = 0
        while (index < line.length) {
            val match = line.indexOf(filter, index, ignoreCase = true)
            if (match < 0) break
            addStyle(highlightStyle, match, match + filter.length)
            index = match + filter.length
        }
    }
}

@Composable
private fun PauseResumePill(
    isPaused: Boolean,
    pausedBacklog: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = if (isPaused) {
        if (pausedBacklog > 0) {
            stringResource(R.string.label_pausedBacklog, pausedBacklog)
        } else {
            stringResource(R.string.label_streamPaused)
        }
    } else {
        stringResource(R.string.label_streamLive)
    }

    Box(
        modifier = modifier
            .navigationBarsPadding()
            .background(
                color = CodeTheme.colors.brandMuted,
                shape = CircleShape,
            )
            .clip(CircleShape)
            .clickable { onClick() }
            .padding(
                horizontal = CodeTheme.dimens.grid.x4,
                vertical = CodeTheme.dimens.grid.x2,
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CodeTheme.dimens.grid.x2),
        ) {
            Icon(
                imageVector = if (isPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                contentDescription = null,
                tint = Color.White,
            )
            Text(
                text = label,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
