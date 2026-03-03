plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.shared.tokens"
}

dependencies {
    implementation(libs.androidx.datastore)

    implementation(libs.androidx.lifecycle.process)

    implementation(project(":apps:flipcash:shared:activityfeed"))
    implementation(project(":apps:flipcash:shared:analytics"))
    implementation(project(":apps:flipcash:shared:featureflags"))
    implementation(project(":apps:flipcash:shared:onramp:deeplinks"))
    implementation(project(":apps:flipcash:shared:persistence:sources"))
    implementation(project(":apps:flipcash:shared:shareable"))
    implementation(project(":libs:datetime"))
    implementation(project(":libs:messaging"))
}
