package com.getcode.navigation.screens

import cafe.adriel.voyager.navigator.tab.Tab

interface ChildNavTab: Tab {
    val ordinal: Int
}