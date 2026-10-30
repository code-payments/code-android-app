import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.metro)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

metro {
    interop {
        includeJavax()
        includeDagger()
        enableDaggerRuntimeInterop.set(true)
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(libs.jetbrains.compose.material3)
    implementation(libs.jetbrains.compose.material.icons.extended)
    implementation(libs.kotlinx.serialization.json)
    implementation(project(":services:opencode"))
    implementation(project(":apps:flipcash:features:balance"))
    implementation(project(":apps:flipcash:core"))
    implementation(project(":apps:flipcash:core-ui"))
    implementation(project(":apps:flipcash:card-expand"))
    implementation(project(":apps:flipcash:shared:common-ui"))
    implementation(project(":ui:components"))
    implementation(project(":apps:flipcash:shared:transaction-history"))
    implementation(project(":apps:flipcash:shared:tokens"))
    implementation(project(":apps:flipcash:shared:funding"))
    implementation(project(":apps:flipcash:shared:userflags"))
    implementation(project(":apps:flipcash:shared:analytics"))
    implementation(project(":apps:flipcash:shared:persistence:sources"))
    implementation(project(":apps:flipcash:shared:persistence:db"))
    implementation(project(":services:flipcash"))
    implementation(project(":libs:coroutines"))
    implementation(project(":libs:logging"))
    implementation(project(":ui:resources"))
    implementation(project(":ui:theme"))
    implementation(project(":apps:flipcash:shared:theme"))
    implementation(project(":services:opencode-compose"))
    implementation(project(":libs:locale:public"))
    implementation(project(":libs:network:connectivity:public"))
    implementation(project(":libs:encryption:chat-cipher"))
    implementation(project(":apps:flipcash:shared:device-link"))
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.dagger)
    implementation(libs.grpc.okhttp)
    implementation(libs.coil3)
    implementation(libs.coil3.network)
    implementation(libs.event.bus)

    testImplementation(project(":apps:flipcash:startup-contract"))
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(compose.desktop.currentOs)
}

tasks.withType<Test>().configureEach {
    // No backend in tests: keep the exchange's rate stream from starting when the graph builds it.
    systemProperty("opencode.processLifecycle.autoStart", "false")
}

// Dev-only sign-in phrase (see DesktopSession), forwarded only when local.properties has one. It goes in
// the environment, not a -D flag, so it stays out of the process's command line.
val devMnemonic = tryReadProperty(rootProject.rootDir, "flipcash.test.mnemonic", "")
    .takeIf { it.isNotBlank() }

compose.desktop {
    application {
        mainClass = "com.flipcash.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Flipcash"
            packageVersion = "1.0.0"
        }
    }
}

tasks.register<JavaExec>("snapshot") {
    group = "application"
    description = "Renders a screen off-screen to a PNG: -Pout=<file> -Pscreen=chat|money|you|link -Pwidth=<dp> -Pheight=<dp>."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.flipcash.desktop.SnapshotMainKt")
    args(
        providers.gradleProperty("out").getOrElse(layout.buildDirectory.file("snapshot.png").get().asFile.path),
        providers.gradleProperty("screen").getOrElse("chat"),
        providers.gradleProperty("width").getOrElse("1040"),
        providers.gradleProperty("height").getOrElse("760"),
    )
}

tasks.withType<JavaExec>().configureEach {
    devMnemonic?.let { environment("FLIPCASH_DEV_MNEMONIC", it) }
}
