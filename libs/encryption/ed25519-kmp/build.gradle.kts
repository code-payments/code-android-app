import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

// ── C source paths ────────────────────────────────────────────────────────────
// The ed25519 C sources live in the existing ed25519 JNI module.
val ed25519SrcDir = rootProject.file("libs/encryption/ed25519/libs/ed25519/src")
val ed25519CSources = fileTree(ed25519SrcDir) {
    include("*.c")
    // key_exchange.c is omitted — not part of our public API surface.
    // seed.c is omitted — uses platform entropy; KMP API takes a caller-supplied seed.
    exclude("key_exchange.c", "seed.c")
}

// ── Static-library compilation per Apple target ───────────────────────────────
//
// Kotlin/Native cinterop only generates Kotlin bindings from the header; it does
// not compile C sources for Apple targets. We produce a static archive
// (libored25519.a) for each target and tell the linker about it.
//
// Output: build/cinterop/<targetName>/libored25519.a

data class AppleTarget(val kotlinName: String, val sdk: String, val arch: String)

val appleTargetDefs = listOf(
    AppleTarget("iosArm64",          "iphoneos",        "arm64"),
    AppleTarget("iosSimulatorArm64", "iphonesimulator", "arm64"),
    AppleTarget("iosX64",            "iphonesimulator", "x86_64"),
)

appleTargetDefs.forEach { target ->
    val outDir = layout.buildDirectory.dir("cinterop/${target.kotlinName}")
    tasks.register("compileEd25519C_${target.kotlinName}", Exec::class) {
        group = "cinterop"
        description = "Compile ed25519 C sources into a static archive for ${target.kotlinName}"

        inputs.files(ed25519CSources)
        outputs.dir(outDir)

        doFirst {
            outDir.get().asFile.mkdirs()
        }

        // Compile each .c → .o then archive all .o into libored25519.a in one shell invocation.
        commandLine("sh", "-c", buildString {
            val compileLines = ed25519CSources.files.joinToString(" && ") { src ->
                val obj = outDir.get().file(src.nameWithoutExtension + ".o").asFile.absolutePath
                "xcrun -sdk ${target.sdk} clang -arch ${target.arch} -O2" +
                    " -c \"${src.absolutePath}\"" +
                    " -I\"${ed25519SrcDir.absolutePath}\"" +
                    " -o \"$obj\""
            }
            val objPaths = ed25519CSources.files.joinToString(" ") { src ->
                "\"${outDir.get().file(src.nameWithoutExtension + ".o").asFile.absolutePath}\""
            }
            val libPath = outDir.get().file("libored25519.a").asFile.absolutePath
            append(compileLines)
            append(" && ar rcs \"$libPath\" $objPaths")
        })
    }
}

kotlin {
    android {
        namespace = "com.getcode.encryption.ed25519kmp"
        compileSdk = 37
        minSdk = 29
        withHostTest {}
    }

    iosArm64()
    iosSimulatorArm64()
    iosX64()

    // ── Cinterop + linker wiring for each Apple target ────────────────────────
    targets.withType<KotlinNativeTarget>().configureEach {
        val targetDef = appleTargetDefs.first { it.kotlinName == name }
        val libDir = layout.buildDirectory.dir("cinterop/$name")
        val compileTaskName = "compileEd25519C_$name"
        val cinteropTaskName = "cinteropEd25519${name.replaceFirstChar { it.uppercaseChar() }}"

        compilations["main"].cinterops.create("ed25519") {
            defFile = file("cinterop/ed25519.def")
            includeDirs(ed25519SrcDir)
        }

        // Both the cinterop binding task and the Kotlin compile task need the
        // static archive to exist before linking.
        tasks.matching { it.name == cinteropTaskName }.configureEach {
            dependsOn(compileTaskName)
        }
        tasks.matching { it.name == "compileKotlin${name.replaceFirstChar { it.uppercaseChar() }}" }.configureEach {
            dependsOn(compileTaskName)
        }

        binaries.configureEach {
            linkerOpts("-L${libDir.get().asFile.absolutePath}", "-lored25519")
        }
    }

    sourceSets {
        commonMain {
            // Ed25519Kmp expect object + KeyPair — no external deps.
        }
        androidMain {
            dependencies {
                // Delegate to the existing JNI module — no NDK recompilation.
                implementation(project(":libs:encryption:ed25519"))
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
}
