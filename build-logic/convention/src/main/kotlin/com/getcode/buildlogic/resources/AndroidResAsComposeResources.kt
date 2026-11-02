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
        // aapt reads `\'`, `\"`, `\@` and `\?` as escapes; Compose resources keep the backslash,
        // so "can\'t" would render with it. The plain character is valid XML text.
        // The filter is not a tracked input, so name its version here to rerun the copy when it changes.
        inputs.property("escapeFilter", "unescape-1")
        filesMatching("values*/*.xml") {
            filter { line -> ANDROID_ESCAPE.replace(line) { it.value.substring(1) } }
        }
        includeEmptyDirs = false
        into(layout.buildDirectory.dir("generated/androidResForCompose"))
    }
    return layout.dir(sync.map { it.destinationDir })
}

private val ANDROID_ESCAPE = Regex("""\\['"@?]""")
