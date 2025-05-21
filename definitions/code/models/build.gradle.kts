import org.apache.tools.ant.taskdefs.condition.Os
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id(Plugins.android_library)
    id(Plugins.kotlin_android)
    id("com.google.protobuf")
}

val archSuffix = if (Os.isFamily(Os.FAMILY_MAC)) ":osx-x86_64" else ""

version = "0.0.1"
group = "com.codeinc.gen"

dependencies {
    protobuf(project(":definitions:code:protos"))

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
    namespace = "${Android.codeNamespace}.service.models"
    compileSdk = Android.compileSdkVersion
    defaultConfig {
        minSdk = Android.minSdkVersion
        testInstrumentationRunner = Android.testInstrumentationRunner
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    kotlinOptions {
        freeCompilerArgs = listOf("-opt-in=kotlin.RequiresOptIn")
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