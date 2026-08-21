plugins {
    alias(libs.plugins.flipcash.android.feature)
}

android {
    namespace = "${Gradle.flipcashNamespace}.features.menu"
}

dependencies {
    implementation(project(":apps:flipcash:shared:appupdates"))
    implementation(project(":apps:flipcash:shared:analytics"))
    implementation(project(":apps:flipcash:shared:authentication"))
    implementation(project(":apps:flipcash:shared:bills"))
    implementation(project(":apps:flipcash:shared:featureflags"))
    implementation(project(":apps:flipcash:shared:menu"))
    implementation(project(":apps:flipcash:shared:funding"))
    implementation(project(":apps:flipcash:shared:shareable"))
    implementation(project(":apps:flipcash:shared:tipping"))
    implementation(project(":apps:flipcash:shared:userflags"))

    implementation(project(":libs:datetime"))
    implementation(project(":libs:messaging"))
    implementation(project(":libs:permissions:bindings"))
}
