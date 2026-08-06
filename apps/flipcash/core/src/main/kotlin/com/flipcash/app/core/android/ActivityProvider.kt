package com.flipcash.app.core.android

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks the currently-foreground [Activity] so app-scoped services that need a live window can
 * reach one without holding a strong reference or being handed an Activity through every call.
 *
 * Register once from the `Application` via [Application.registerActivityLifecycleCallbacks]. The
 * reference is weak and cleared on pause, so this never leaks an Activity.
 */
@Singleton
class ActivityProvider @Inject constructor() : Application.ActivityLifecycleCallbacks {

    private var reference: WeakReference<Activity>? = null

    /** The resumed Activity, or null when the app has none in the foreground. */
    val current: Activity? get() = reference?.get()

    override fun onActivityResumed(activity: Activity) {
        reference = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (reference?.get() === activity) reference = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
