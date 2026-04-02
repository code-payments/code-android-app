plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.appupdates"
}

dependencies {
    api(libs.google.play.app.updates.runtime)
    api(libs.google.play.app.updates.ktx)

    implementation(project(":apps:flipcash:shared:userflags"))
}
