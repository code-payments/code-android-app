package com.getcode.buildlogic.resources

import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Sync

/**
 * Exposes a module's Android `res/` directory to Compose Multiplatform resources.
 *
 * Android keeps reading `res/` through `R`, so its behaviour does not change; common code reads
 * the same strings and drawables through the generated `Res` class. The files stay in one place;
 * this only maps the layout. Compose resources reject Android's `nodpi` qualifier, so
 * `drawable-nodpi` is served as plain `drawable`. Pass the result to
 * `compose.resources.customDirectory(sourceSetName = "commonMain", directoryProvider = ...)`.
 */
fun Project.androidResAsComposeResources(resDir: String = "src/androidMain/res"): Provider<Directory> {
    val sync = tasks.register("syncAndroidResForCompose", Sync::class.java) {
        from(layout.projectDirectory.dir(resDir)) {
            include("values/**", "drawable/**", "drawable-*/**", "font/**")
        }
        eachFile { path = path.replaceFirst("drawable-nodpi/", "drawable/") }
        includeEmptyDirs = false
        into(layout.buildDirectory.dir("generated/androidResForCompose"))
    }
    return layout.dir(sync.map { it.destinationDir })
}
