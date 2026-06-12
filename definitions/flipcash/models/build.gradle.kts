import dev.bmcreations.protovalidate.gradle.ProtoVariant
import org.apache.tools.ant.taskdefs.condition.Os

plugins {
    alias(libs.plugins.flipcash.android.library)
    alias(libs.plugins.protobuf)
    alias(libs.plugins.protobuf.validate)
}

val archSuffix = if (Os.isFamily(Os.FAMILY_MAC)) {
    if (System.getProperty("os.arch") == "aarch64") ":osx-aarch_64" else ":osx-x86_64"
} else ""

version = "0.0.1"
group = "com.codeinc.flipcash.gen"

dependencies {
    protobuf(project(":definitions:flipcash:protos"))

    implementation(libs.grpc.protobuf.lite)
    implementation(libs.grpc.stub)

    // Kotlin Generation
    implementation(libs.grpc.kotlin)
    implementation(libs.protobuf.kotlin.lite)
}

android {
    namespace = "${Gradle.flipcashNamespace}.defs.models"
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

protovalidate {
    variant.set(ProtoVariant.PGV)
}