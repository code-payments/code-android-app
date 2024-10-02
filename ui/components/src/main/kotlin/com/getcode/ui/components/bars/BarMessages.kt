package com.getcode.ui.components.bars

import com.getcode.manager.BottomBarManager
import com.getcode.manager.TopBarManager
import kotlinx.coroutines.flow.MutableStateFlow

class BarMessages {
    val topBar = MutableStateFlow<TopBarManager.TopBarMessage?>(null)
    val bottomBar = MutableStateFlow<BottomBarManager.BottomBarMessage?>(null)
}