package com.getcode.navigation.core

import com.getcode.navigation.AppHome
import com.getcode.navigation.AppRegion
import com.getcode.navigation.testNavigator
import kotlin.test.Test
import kotlin.test.assertEquals

class CodeNavigatorHarnessTest {

    @Test
    fun `push and pop mutate the backstack headlessly`() {
        val nav = testNavigator(AppHome)

        nav.navigate(AppRegion)
        assertEquals(listOf(AppHome, AppRegion), nav.backStack.toList())

        nav.navigateBack()
        assertEquals(listOf(AppHome), nav.backStack.toList())
    }
}
