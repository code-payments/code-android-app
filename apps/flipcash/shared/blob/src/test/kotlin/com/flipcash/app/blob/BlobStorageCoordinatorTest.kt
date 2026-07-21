package com.flipcash.app.blob

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.flipcash.libs.coroutines.DispatcherProvider
import com.flipcash.services.controllers.BlobStorageController
import com.flipcash.services.models.InitiateExternalUploadError
import com.flipcash.services.models.blob.MimeTypeConstraints
import com.flipcash.services.models.blob.UploadPolicy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BlobStorageCoordinatorTest {

    private val controller = mockk<BlobStorageController>()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun TestScope.newCoordinator(): BlobStorageCoordinator {
        val test = UnconfinedTestDispatcher(testScheduler)
        val dispatchers = object : DispatcherProvider {
            override val Default: CoroutineDispatcher = test
            override val Main: CoroutineDispatcher = test
            override val IO: CoroutineDispatcher = test
        }
        return BlobStorageCoordinator(context, controller, dispatchers)
    }

    private fun policy(version: String, ttl: Duration) =
        UploadPolicy(
            version = version,
            ttl = ttl,
            mimeTypeConstraints = listOf(MimeTypeConstraints("image/*", 1_000, null)),
        )

    @Test
    fun `preloaded policy is served from the cache`() = runTest {
        coEvery { controller.getUploadPolicy() } returns Result.success(policy("v1", 1.hours))
        val coordinator = newCoordinator()
        coordinator.reset()

        coordinator.preloadPolicy()

        assertEquals("v1", coordinator.policy.first()?.version)
    }

    @Test
    fun `a policy-denied upload with a new version refreshes the cached policy`() = runTest {
        coEvery { controller.getUploadPolicy() } returns Result.success(policy("v1", 1.hours))
        coEvery { controller.upload(any(), any()) } returns
            Result.failure(InitiateExternalUploadError.UnsupportedType(policyVersion = "v2"))
        val coordinator = newCoordinator()
        coordinator.reset()
        coordinator.preloadPolicy() // seeds v1 (getUploadPolicy #1)

        coordinator.upload(byteArrayOf(1), "image/png")

        // preload + a refresh, because the denied version (v2) differs from the cached one (v1).
        coVerify(exactly = 2) { controller.getUploadPolicy() }
    }

    @Test
    fun `a policy-denied upload with the same version does not refresh`() = runTest {
        coEvery { controller.getUploadPolicy() } returns Result.success(policy("v1", 1.hours))
        coEvery { controller.upload(any(), any()) } returns
            Result.failure(InitiateExternalUploadError.UnsupportedType(policyVersion = "v1"))
        val coordinator = newCoordinator()
        coordinator.reset()
        coordinator.preloadPolicy()

        coordinator.upload(byteArrayOf(1), "image/png")

        coVerify(exactly = 1) { controller.getUploadPolicy() }
    }

    @Test
    fun `a non-policy upload failure does not refresh`() = runTest {
        coEvery { controller.getUploadPolicy() } returns Result.success(policy("v1", 1.hours))
        coEvery { controller.upload(any(), any()) } returns Result.failure(RuntimeException("network"))
        val coordinator = newCoordinator()
        coordinator.reset()
        coordinator.preloadPolicy()

        coordinator.upload(byteArrayOf(1), "image/png")

        coVerify(exactly = 1) { controller.getUploadPolicy() }
    }
}
