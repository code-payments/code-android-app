plugins {
    id(Plugins.android_library)
}

android {
    namespace = "${Android.namespace}.ed25519"
    compileSdk = Android.compileSdkVersion
    defaultConfig {
        minSdk = Android.minSdkVersion
        buildToolsVersion = Android.buildToolsVersion
        ndkVersion = "23.1.7779620"
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++11"
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
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
}
