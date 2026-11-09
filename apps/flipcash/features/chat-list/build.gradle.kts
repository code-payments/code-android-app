plugins {
    alias(libs.plugins.flipcash.kmp.app.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.ksp)
}

kotlin {
    android {
        namespace = "${Gradle.flipcashNamespace}.features.chatlist"
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":apps:flipcash:core"))
            implementation(project(":apps:flipcash:core-ui"))
            implementation(project(":apps:flipcash:shared:analytics"))
            implementation(project(":apps:flipcash:shared:chat"))
            implementation(project(":apps:flipcash:shared:chat-ui"))
            implementation(project(":apps:flipcash:shared:common-ui"))
            implementation(project(":apps:flipcash:shared:theme"))
            implementation(project(":apps:flipcash:shared:tokens"))
            implementation(project(":services:flipcash"))
            implementation(project(":services:opencode"))
            implementation(project(":libs:coroutines"))
            implementation(project(":ui:components"))
            implementation(project(":ui:core"))
            implementation(project(":ui:navigation"))
            implementation(project(":ui:resources"))
            implementation(project(":ui:theme"))

            implementation(libs.jetbrains.compose.runtime)
            implementation(libs.jetbrains.compose.foundation)
            implementation(libs.jetbrains.compose.animation)
            implementation(libs.jetbrains.compose.ui)
            implementation(libs.jetbrains.compose.material)
            implementation(libs.jetbrains.compose.material3)
            implementation(libs.jetbrains.compose.material.icons.extended)
            implementation(libs.jetbrains.compose.components.resources)
            implementation(libs.jetbrains.compose.ui.tooling.preview)
            implementation(libs.bundles.haze)
            implementation(libs.javax.inject)
            implementation(libs.kotlinx.serialization.core)
        }
        androidMain.dependencies {
            // What flipcash.android.feature supplied before the move to KMP.
            implementation(libs.bundles.hilt)
            implementation(project(":libs:messaging"))
            implementation(project.dependencies.platform(libs.compose.bom))
            implementation(libs.bundles.compose)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.mockk)
            implementation(project(":apps:flipcash:shared:analytics:test-fixtures"))
        }
        // Puppet: the real ChatsViewModel over a seeded fixture, replaying the shared behavior files
        // (test-vectors/behaviors/chats/, synced from the orchestrator). See ChatsBehaviorTest.
        jvmTest.dependencies {
            implementation(project(":apps:flipcash:shared:analytics"))
            implementation(project(":apps:flipcash:shared:chat"))
            implementation(project(":apps:flipcash:shared:chat-ui"))
            implementation(project(":apps:flipcash:shared:persistence:sources"))
            implementation(project(":apps:flipcash:shared:persistence:db"))
            implementation(project(":apps:flipcash:shared:tokens"))
            implementation(project(":services:flipcash"))
            implementation(project(":services:opencode"))
            implementation(project(":libs:coroutines"))
            implementation(project(":libs:encryption:chat-cipher"))
            implementation(project(":libs:puppet"))
            implementation(project(":ui:resources"))
            implementation(project(":apps:flipcash:startup-contract"))
            implementation(libs.event.bus)
            implementation(libs.kotlinx.serialization.json)
        }
        getByName("androidHostTest").dependencies {
            implementation(kotlin("test"))
            implementation(libs.bundles.unit.testing)
            implementation(libs.robolectric)
            implementation(project(":ui:resources:test-fixtures"))
            implementation(project(":libs:test-utils"))
            implementation(project(":apps:flipcash:shared:analytics:test-fixtures"))
        }
    }
}

dependencies {
    add("kspAndroid", libs.hilt.android.compiler)
    add("kspAndroid", libs.hilt.compiler)
    add("kspJvm", libs.dagger.compiler)
    add("jvmMainImplementation", libs.dagger)
}

// ChatsBehaviorTest replays these. `-Ppuppet.behaviors=<dir or file>` points it at others, such as
// one file an agent is writing; the default is the synced copy of the orchestrator's behaviors.
tasks.named<Test>("jvmTest") {
    val behaviors = providers.gradleProperty("puppet.behaviors")
        .map { rootProject.file(it) }
        .orElse(rootProject.layout.projectDirectory.dir("test-vectors/behaviors/chats").asFile)
    inputs.files(behaviors).withPropertyName("puppetBehaviors").optional()
    systemProperty("puppet.behaviors", behaviors.get().absolutePath)
}

// The Chats host as a process, for the orchestrator's `puppet` CLI (`ls`/`do` sessions and
// `puppet test android`). This writes a launcher over the jvmTest classpath, so a session runs
// without Gradle: no Gradle startup per session and no build output on the host's stdout. Rerun it
// after a code change; `puppet` does that for you.
tasks.register("puppetLauncher") {
    group = "puppet"
    description = "Writes build/puppet/chats-host, which runs the Chats Puppet host on stdin/stdout."
    val test = kotlin.jvm().compilations.getByName("test")
    val runtime = files(test.output.allOutputs, test.runtimeDependencyFiles)
    dependsOn(test.compileTaskProvider, test.output.allOutputs)
    val java = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) }
        .map { it.executablePath.asFile.absolutePath }
    val script = layout.buildDirectory.file("puppet/chats-host")
    val classpath = provider { runtime.asPath }
    inputs.files(runtime).withPropertyName("runtime")
    outputs.file(script)
    doLast {
        val file = script.get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            |#!/bin/sh
            |# Generated by :apps:flipcash:features:chat-list:puppetLauncher. Rerun it after a code change.
            |exec "${java.get()}" -Dopencode.processLifecycle.autoStart=false -cp '${classpath.get()}' com.flipcash.app.puppet.chats.ChatsPuppetMainKt "${'$'}@"
            |""".trimMargin(),
        )
        file.setExecutable(true)
    }
}
