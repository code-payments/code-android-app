package com.flipcash.app.updates.inject

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.flipcash.app.core.android.VersionInfo
import com.flipcash.shared.appupdates.BuildConfig
import com.flipcash.app.updates.AppUpdateController
import com.flipcash.app.updates.ReleaseStage
import com.flipcash.app.updates.ReleaseStageProvider
import com.flipcash.app.updates.internal.DataStoreUpdateToastSeenStore
import com.flipcash.app.updates.internal.FlexibleUpdates
import com.flipcash.app.updates.internal.GooglePlayAppUpdateController
import com.flipcash.app.userflags.UserFlagsCoordinator
import com.flipcash.libs.coroutines.DispatcherProvider
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import java.io.File

@Module
@InstallIn(ActivityComponent::class)  // ← Activity-scoped!
object AppUpdateModule {

    /**
     * Debug builds only: with this file in the app's files dir, the optional-update path talks to a
     * fake Play that offers a priority-5 update, so the toast can be seen on an emulator.
     * `adb shell run-as com.flipcash.app.android touch files/debug-optional-update`
     */
    private const val DEBUG_OPTIONAL_UPDATE_MARKER = "debug-optional-update"

    @Provides
    @ActivityScoped
    fun provideInAppUpdateManager(
        @ActivityContext context: Context,
        versionInfo: VersionInfo,
        userFlagsCoordinator: UserFlagsCoordinator,
        dispatchers: DispatcherProvider,
        releaseStageProvider: ReleaseStageProvider,
    ): AppUpdateController {
        val debugOverride = BuildConfig.DEBUG &&
            File(context.filesDir, DEBUG_OPTIONAL_UPDATE_MARKER).exists()
        val optionalManager: AppUpdateManager = if (debugOverride) {
            FakeAppUpdateManager(context).apply {
                setUpdateAvailable(versionInfo.versionCode + 1)
                setUpdatePriority(5)
            }
        } else {
            AppUpdateManagerFactory.create(context)
        }
        val flexibleUpdates = FlexibleUpdates(
            manager = optionalManager,
            currentVersionCode = versionInfo.versionCode,
            // Debug and internal-track builds see Play's internal track, not the store.
            isStoreBuild = {
                debugOverride ||
                    (!BuildConfig.DEBUG && releaseStageProvider.resolvedStage == ReleaseStage.Production)
            },
            isInForeground = {
                ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            },
        )
        return GooglePlayAppUpdateController(
            context = context,
            versionInfo = versionInfo,
            userFlags = userFlagsCoordinator,
            dispatchers = dispatchers,
            flexibleUpdates = flexibleUpdates,
            toastSeenStore = DataStoreUpdateToastSeenStore(context.applicationContext),
        )
    }
}
