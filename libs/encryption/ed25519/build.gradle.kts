import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    alias(libs.plugins.flipcash.kmp.test.fixtures)
}

// Compiles `src/commonTest/resources` into a generated `TestFixtures.kt` on `commonTest`, readable
// from every target -- see the `flipcash.kmp.test.fixtures` convention plugin.
testFixtures {
    packageName = "com.getcode.ed25519kmp"
}

// ── C source paths ────────────────────────────────────────────────────────────
// The ed25519 C sources live in the existing ed25519 JNI module.
val ed25519SrcDir = rootProject.file("libs/encryption/ed25519-native/libs/ed25519/src")
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
    AppleTarget("macosArm64",        "macosx",          "arm64"),
    AppleTarget("macosX64",          "macosx",          "x86_64"),
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
        namespace = "com.getcode.encryption.ed25519"
        compileSdk = 37
        minSdk = 29
        withHostTest {}
    }

    iosArm64()
    iosSimulatorArm64()
    iosX64()
    macosArm64()
    macosX64()

    // ── Cinterop + linker wiring for each Apple target ────────────────────────
    targets.withType<KotlinNativeTarget>().configureEach {
        val targetDef = appleTargetDefs.first { it.kotlinName == name }
        val libDir = layout.buildDirectory.dir("cinterop/$name")
        val compileTaskName = "compileEd25519C_$name"
        val cinteropTaskName = "cinteropEd25519${name.replaceFirstChar { it.uppercaseChar() }}"

        compilations["main"].cinterops.create("ed25519") {
            definitionFile = file("cinterop/ed25519.def")
            includeDirs(ed25519SrcDir)
            // Embed the archive in the klib rather than leaving it to each binary's
            // linker flags: a static framework built from this module ships the C
            // objects inside it, so consumers link one artifact and nothing else.
            // Without this the framework exports `ed25519_*` as undefined symbols and
            // only links inside an app that happens to compile the same C itself.
            extraOpts(
                "-staticLibrary", "libored25519.a",
                "-libraryPath", libDir.get().asFile.absolutePath,
            )
        }

        // Both the cinterop binding task and the Kotlin compile task need the
        // static archive to exist before linking.
        tasks.matching { it.name == cinteropTaskName }.configureEach {
            dependsOn(compileTaskName)
        }
        tasks.matching { it.name == "compileKotlin${name.replaceFirstChar { it.uppercaseChar() }}" }.configureEach {
            dependsOn(compileTaskName)
        }
    }

    sourceSets {
        commonMain {
            // Ed25519Kmp expect object + KeyPair — no external deps.
        }
        androidMain {
            dependencies {
                // Delegate to the JNI module for NDK/CMake compilation. `api` (not
                // `implementation`) so the JNI `com.getcode.ed25519.Ed25519` class is
                // transitively re-exported to existing consumers of `:libs:encryption:ed25519`
                // (e.g. :libs:encryption:utils) — keeps the rename consumer-transparent.
                api(project(":libs:encryption:ed25519-native"))
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
