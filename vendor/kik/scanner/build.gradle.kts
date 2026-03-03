plugins {
    alias(libs.plugins.flipcash.android.library.compose)
}

android {
    namespace = "com.kik.kikx"

    defaultConfig {
        ndkVersion = "29.0.14206865"
        externalNativeBuild {
            cmake {
                ndkVersion = "29.0.14206865"
                cppFlags += "-std=c++11"
                cppFlags += listOf(
                    "-O3",
                    "-ffast-math",
                    "-funsafe-math-optimizations",
                    "-funroll-loops",
                )
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
        }
    }
}

dependencies {
    implementation(libs.androidx.camerax.core)
    implementation(libs.javax.inject)
    implementation(libs.hilt.android)
    implementation(project(":libs:code-detection"))
    implementation(project(":libs:encryption:ed25519"))
    implementation(project(":vendor:opencv:sdk"))
}
