plugins {
    id(Plugins.android_library)
}

android {
    namespace = "${Gradle.codeNamespace}.ed25519"
    compileSdk = Android.compileSdkVersion
    defaultConfig {
        minSdk = Android.minSdkVersion
        ndkVersion = "29.0.14206865"
        externalNativeBuild {
            cmake {
                ndkVersion = "29.0.14206865"
                cppFlags += "-std=c++11"
            }
        }
    }

    compileOptions {
        sourceCompatibility(Versions.java)
        targetCompatibility(Versions.java)
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(Versions.java))
        }
    }

    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
        }
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(Libs.kotlinx_serialization_json)
}
