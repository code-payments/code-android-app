import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.metro)
}

kotlin {
    jvmToolchain(21)
}

metro {
    // Dagger ignores Kotlin defaults, so shared classes like RealChatCoordinator list defaulted
    // parameters that Android always injects. Metro's default treats those as optional and orders
    // the graph without them, which hands the factory a null provider at startup.
    optionalBindingBehavior.set(dev.zacsweers.metro.gradle.OptionalBindingBehavior.REQUIRE_OPTIONAL_BINDING)
    interop {
        includeJavax()
        includeDagger()
        enableDaggerRuntimeInterop.set(true)
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(libs.jetbrains.compose.material3)
    implementation(project(":services:opencode"))
    implementation(project(":apps:flipcash:features:balance"))
    implementation(project(":apps:flipcash:core"))
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
    implementation(project(":apps:flipcash:shared:blob"))
    implementation(project(":apps:flipcash:shared:chat"))
    implementation(project(":apps:flipcash:shared:chat-ui"))
    implementation(project(":apps:flipcash:features:chat-list"))
    implementation(project(":libs:emojis"))
    implementation(project(":libs:puppet"))
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.dagger)
    implementation(libs.grpc.okhttp)
    implementation(libs.coil3)
    implementation(libs.coil3.network)
    implementation(libs.event.bus)

    testImplementation(project(":apps:flipcash:startup-contract"))
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
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
    description = "Renders the wallet (or, with -Pscreen=link, the link screen) off-screen to a PNG (-Pout=<file>)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.flipcash.desktop.SnapshotMainKt")
    args(
        providers.gradleProperty("out").getOrElse(layout.buildDirectory.file("wallet.png").get().asFile.path),
        providers.gradleProperty("screen").getOrElse("wallet"),
    )
}

// The Puppet host: drives the Chats screen headlessly over seeded data, JSON requests on stdin and
// responses on stdout. `installPuppet` builds a launcher that runs without Gradle, so a session pays
// no Gradle startup and gets no build output mixed into its stdout.
val puppetJvmArgs = listOf("-Dopencode.processLifecycle.autoStart=false", "-Djava.awt.headless=true")
val puppetMain = "com.flipcash.desktop.puppet.PuppetMainKt"

tasks.register<JavaExec>("puppet") {
    group = "application"
    description = "Runs the Puppet host through Gradle: JSON requests on stdin, responses on stdout."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set(puppetMain)
    standardInput = System.`in`
    jvmArgs(puppetJvmArgs)
}

// A script over the built classpath rather than a copied lib/ folder: several modules build a jar
// with the same name (core-jvm.jar), so copies would collide. The host runs from this checkout
// anyway, and pinning the toolchain's java keeps it on JDK 21 whatever is on PATH.
tasks.register("installPuppet") {
    group = "application"
    description = "Writes a launcher for the Puppet host at build/install/puppet-host/bin/puppet-host."
    val runtime = sourceSets["main"].runtimeClasspath
    val java = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }
        .map { it.executablePath.asFile.absolutePath }
    val script = layout.buildDirectory.file("install/puppet-host/bin/puppet-host")
    val jvmArgs = puppetJvmArgs.joinToString(" ")
    val main = puppetMain
    val classpath = provider { runtime.asPath }
    inputs.files(runtime)
    outputs.file(script)
    doLast {
        val file = script.get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |#!/bin/sh
            |# Generated by :apps:desktop:installPuppet. Rerun it after a code change.
            |exec "${java.get()}" $jvmArgs -cp '${classpath.get()}' $main "${'$'}@"
            |""".trimMargin(),
        )
        file.setExecutable(true)
    }
}

tasks.withType<JavaExec>().configureEach {
    devMnemonic?.let { environment("FLIPCASH_DEV_MNEMONIC", it) }
}
