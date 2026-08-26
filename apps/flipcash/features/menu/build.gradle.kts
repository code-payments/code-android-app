plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.features.menu"
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)

    implementation(libs.bundles.haze)

    implementation(project(":apps:flipcash:shared:appupdates"))
    implementation(project(":apps:flipcash:shared:analytics"))
    implementation(project(":apps:flipcash:shared:authentication"))
    implementation(project(":apps:flipcash:shared:bills"))
    implementation(project(":apps:flipcash:shared:featureflags"))
    implementation(project(":apps:flipcash:shared:menu"))
    implementation(project(":apps:flipcash:shared:funding"))
    implementation(project(":apps:flipcash:shared:shareable"))
    implementation(project(":apps:flipcash:shared:tipping"))
    // Balance for the username gate only — :shared:tokens:core is the narrow half, so the
    // whole token stack does not come with it.
    implementation(project(":apps:flipcash:shared:tokens:core"))
    implementation(project(":apps:flipcash:shared:userflags"))

    implementation(project(":libs:datetime"))
    implementation(project(":libs:messaging"))
    implementation(project(":libs:permissions:bindings"))
}
