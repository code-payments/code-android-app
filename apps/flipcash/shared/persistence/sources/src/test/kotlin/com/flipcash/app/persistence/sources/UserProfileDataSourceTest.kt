package com.flipcash.app.persistence.sources

import app.cash.turbine.test
import com.flipcash.app.persistence.FlipcashDatabase
import com.flipcash.services.models.UserProfile
import com.getcode.utils.hexEncodedString
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertEquals

/**
 * A group preview is attributed from `user_profiles`. These pin the two properties the chat list's
 * launch behavior rests on: a name stored by one session is in the next session's first emission,
 * and re-storing an unchanged name does not make the list rebuild.
 */
@RunWith(RobolectricTestRunner::class)
class UserProfileDataSourceTest {

    private val dataSource = UserProfileDataSource()
    private val userId = ByteArray(16) { 3 }.toList()
    private val profile = UserProfile.Empty.copy(displayName = "Ada", userId = userId)

    @Before
    fun setUp() {
        FlipcashDatabase.init(RuntimeEnvironment.getApplication(), ENTROPY)
    }

    @After
    fun tearDown() {
        FlipcashDatabase.closeDb()
    }

    @Test
    fun `a name stored in one session is in the first emission of the next`() = runTest {
        dataSource.store(userId, profile)

        // Process restart: the database closes and the same account's file is reopened.
        FlipcashDatabase.closeDb()
        FlipcashDatabase.init(RuntimeEnvironment.getApplication(), ENTROPY)

        dataSource.observeProfiles().test {
            assertEquals("Ada", awaitItem()[userId.hexEncodedString()]?.displayName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `storing the same name again does not re-emit`() = runTest {
        dataSource.store(userId, profile)

        dataSource.observeProfiles().test {
            assertEquals("Ada", awaitItem()[userId.hexEncodedString()]?.displayName)

            dataSource.store(userId, profile)
            expectNoEvents()

            dataSource.store(userId, profile.copy(displayName = "Ada L"))
            assertEquals("Ada L", awaitItem()[userId.hexEncodedString()]?.displayName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private companion object {
        const val ENTROPY = "dGVzdC1lbnRyb3B5LWZvci11c2VyLXByb2ZpbGVz"
    }
}
