package com.flipcash.app.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Provider tests with coroutine dispatcher rule
 *
 * Note this calls `setMain` so that, for instance, viewModelScope will use this in place of
 * `Dispatchers.Main`.
 *
 * References
 * https://developer.android.com/codelabs/advanced-android-kotlin-training-testing-survey#3
 * https://stackoverflow.com/a/71808251
 * https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-test/MIGRATION.md
 */
@ExperimentalCoroutinesApi
class MainCoroutineRule(val dispatcher: TestDispatcher = StandardTestDispatcher()) : TestWatcher() {

    override fun starting(description: Description?) {
        super.starting(description)
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description?) {
        super.finished(description)
        Dispatchers.resetMain()
    }
}

