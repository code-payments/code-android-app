import org.apache.tools.ant.taskdefs.condition.Os

plugins {
    alias(libs.plugins.flipcash.android.library)
    id("com.google.protobuf")
}

val archSuffix = if (Os.isFamily(Os.FAMILY_MAC)) ":osx-x86_64" else ""

version = "0.0.1"
group = "com.codeinc.opencode.gen"

dependencies {
    protobuf(project(":definitions:opencode:protos"))

    implementation(libs.grpc.protobuf.lite)
    implementation(libs.grpc.stub)

    // Kotlin Generation
    implementation(libs.grpc.kotlin)
    implementation(libs.protobuf.kotlin.lite)
}

android {
    namespace = "${Gradle.codeNamespace}.defs.opencode.models"
}

val protobufVersion = libs.versions.protobuf.asProvider().get()
val grpcVersion = libs.versions.grpc.asProvider().get()

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${protobufVersion}$archSuffix"
    }
    plugins {
        create("java") {
            artifact = "io.grpc:protoc-gen-grpc-java:${grpcVersion}"
        }
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${grpcVersion}"
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
