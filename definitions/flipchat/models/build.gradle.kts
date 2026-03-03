import org.apache.tools.ant.taskdefs.condition.Os
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id(Plugins.android_library)
    id(Plugins.kotlin_android)
    id("com.google.protobuf")
}

val archSuffix = if (Os.isFamily(Os.FAMILY_MAC)) ":osx-x86_64" else ""

version = "0.0.1"
group = "com.codeinc.fc.gen"

dependencies {
    protobuf(project(":definitions:flipchat:protos"))

    implementation(Libs.grpc_protobuf_lite)
    implementation(Libs.grpc_stub)

    // Kotlin Generation
    implementation(Libs.grpc_kotlin)
    implementation(Libs.protobuf_kotlin_lite)
    implementation(Libs.kotlinx_coroutines_core)
}

kotlin {
    jvmToolchain(Versions.java.toInt())
}

android {
    namespace = "${Gradle.codeNamespace}.defs.fc.models"
    compileSdk = Android.compileSdkVersion
    defaultConfig {
        minSdk = Android.minSdkVersion
        testInstrumentationRunner = Android.testInstrumentationRunner
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(Versions.java))
    }

    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(Versions.java))
        optIn.addAll(
            "kotlin.time.ExperimentalTime",
            "kotlin.ExperimentalUnsignedTypes",
            "kotlin.RequiresOptIn"
        )
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${Versions.protobuf}$archSuffix"
    }
    plugins {
        create("java") {
            artifact = "io.grpc:protoc-gen-grpc-java:${Versions.grpc}"
        }
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${Versions.grpc}"
        }
        create("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:1.4.1:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                create("java") {
                    option("lite")
                }
                create("grpc") {
                    option("lite")
                }
                create("grpckt") {
                    option("lite")
                }
            }
            it.builtins {
                create("kotlin") {
                    option("lite")
                }
            }
        }
    }
}
