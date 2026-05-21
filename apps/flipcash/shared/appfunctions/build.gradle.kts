plugins {
    alias(libs.plugins.flipcash.android.library)
    id("com.google.devtools.ksp")
    id("dagger.hilt.android.plugin")
}

ksp {
    arg("appfunctions:generateMetadataFromSchema", "true")
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.appfunctions"
}

dependencies {
    implementation(libs.appfunctions)
    implementation(libs.appfunctions.service)
    ksp(libs.appfunctions.compiler)

    implementation(libs.bundles.hilt)
    ksp(libs.bundles.hilt.compiler)

    implementation(project(":apps:flipcash:core"))
    implementation(project(":apps:flipcash:shared:tokens"))
    implementation(project(":apps:flipcash:shared:activityfeed"))
    implementation(project(":apps:flipcash:shared:featureflags"))
    implementation(project(":services:flipcash"))
    implementation(project(":services:opencode"))
    implementation(project(":libs:encryption:keys"))
}
