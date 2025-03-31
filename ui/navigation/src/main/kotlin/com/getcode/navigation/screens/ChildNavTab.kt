package com.getcode.navigation.screens

import cafe.adriel.voyager.navigator.tab.Tab
import com.getcode.navigation.core.NavigationLocator

interface ChildNavTab: Tab {
    val ordinal: Int
    var childNav: NavigationLocator
}