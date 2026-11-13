// Canonical protobuf encoding for Wire messages: known fields in field-number order with oneof
// members in place, map entries sorted by key, unknown fields last. This is the order protobuf-java
// and Go write, and the order the OCP and Flipcash servers re-marshal a request into before they
// verify its signature. Wire's own encode() writes oneof members after the regular fields.
//
// Depends only on the Wire runtime (and okio through it), so the OCP transport and the Flipcash chat
// and client modules can share it without depending on each other.
plugins {
    alias(libs.plugins.flipcash.kmp.library)
    alias(libs.plugins.flipcash.kmp.js)
}

kotlin {
    android {
        namespace = "com.getcode.wire.canonical"
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.wire.runtime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
