pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven(url = "https://jitpack.io")
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "dagger.hilt.android.plugin") {
                useModule("com.google.dagger:hilt-android-gradle-plugin:${requested.version}")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.PREFER_SETTINGS
    repositories {
        google()
        mavenCentral()
        maven(url = "https://plugins.gradle.org/m2/")
        maven(url = "https://maven.fpregistry.io/releases")
        maven(url = "https://jitpack.io")
        maven(url = "https://central.sonatype.com/repository/maven-snapshots/")
    }
}

rootProject.name = "Code"

include(
    // app containers
    ":apps:flipcash:app",

    // flipcash modules
    ":apps:flipcash:core",
    ":libs:test-utils",
    // shared flipcash coordinators/controllers/viewmodels/services
    ":apps:flipcash:shared:accesskey",
    ":apps:flipcash:shared:analytics",
    ":apps:flipcash:shared:appsettings",
    ":apps:flipcash:shared:authentication",
    ":apps:flipcash:shared:activityfeed",
    ":apps:flipcash:shared:bills",
    ":apps:flipcash:shared:bill-customization",
    ":apps:flipcash:shared:onramp:common",
    ":apps:flipcash:shared:onramp:coinbase",
    ":apps:flipcash:shared:onramp:deeplinks",
    ":apps:flipcash:shared:appupdates",
    ":apps:flipcash:shared:google-play-billing",
    ":apps:flipcash:shared:currency-selection:core",
    ":apps:flipcash:shared:currency-selection:ui",
    ":apps:flipcash:shared:featureflags",
    ":apps:flipcash:shared:ksp",
    ":apps:flipcash:shared:menu",
    ":apps:flipcash:shared:navigation-flow",
    ":apps:flipcash:shared:notifications",
    ":apps:flipcash:shared:payments",
    ":apps:flipcash:shared:permissions",
    ":apps:flipcash:shared:persistence:db",
    ":apps:flipcash:shared:persistence:provider",
    ":apps:flipcash:shared:persistence:sources",
    ":apps:flipcash:shared:phone",
    ":apps:flipcash:shared:router",
    ":apps:flipcash:shared:session",
    ":apps:flipcash:shared:shareable",
    ":apps:flipcash:shared:tokens",
    ":apps:flipcash:shared:theme",
    ":apps:flipcash:shared:userflags",
    ":apps:flipcash:shared:workers",
    ":apps:flipcash:shared:web",
    // flipcash features
    ":apps:flipcash:features:login",
    ":apps:flipcash:features:scanner",
    ":apps:flipcash:features:cash",
    ":apps:flipcash:features:balance",
    ":apps:flipcash:features:menu",
    ":apps:flipcash:features:purchase",
    ":apps:flipcash:features:lab",
    ":apps:flipcash:features:appsettings",
    ":apps:flipcash:features:appupdates",
    ":apps:flipcash:features:deposit",
    ":apps:flipcash:features:advanced",
    ":apps:flipcash:features:myaccount",
    ":apps:flipcash:features:backupkey",
    ":apps:flipcash:features:shareapp",
    ":apps:flipcash:features:withdrawal",
    ":apps:flipcash:features:payments",
    ":apps:flipcash:features:onramp",
    ":apps:flipcash:features:contact-verification",
    ":apps:flipcash:features:tokens",
    ":apps:flipcash:features:transactions",
    ":apps:flipcash:features:bill-customization",
    ":apps:flipcash:features:discovery",
    ":apps:flipcash:features:userflags",

    // protobuf model and service implementations for the Open Code Protocol
    ":definitions:opencode:models",
    ":definitions:opencode:protos",

    // protobuf model and service implementations for Flipcash
    ":definitions:flipcash:models",
    ":definitions:flipcash:protos",

    // Internal libs
    ":libs:analytics",
    ":libs:biometrics",
    ":libs:code-detection",
    ":libs:coroutines",
    ":libs:crypto:kin",
    ":libs:crypto:solana",
    ":libs:currency",
    ":libs:currency-math",
    ":libs:datetime",
    ":libs:emojis",
    ":libs:encryption:base58",
    ":libs:encryption:ed25519",
    ":libs:encryption:hmac",
    ":libs:encryption:keys",
    ":libs:encryption:mnemonic",
    ":libs:encryption:sha256",
    ":libs:encryption:sha512",
    ":libs:encryption:utils",

    ":libs:locale:bindings",
    ":libs:locale:impl",
    ":libs:locale:public",

    ":libs:logging",
    ":libs:messaging",
    ":libs:models",
    ":libs:network:exchange",
    ":libs:opengraph",

    ":libs:network:coinbase:onramp",

    ":libs:network:connectivity:bindings",
    ":libs:network:connectivity:impl",
    ":libs:network:connectivity:public",

    ":libs:network:jwt",

    ":libs:permissions:bindings",
    ":libs:permissions:impl",
    ":libs:permissions:public",

    ":libs:quickresponse",
    ":libs:requests",
    ":libs:search",

    ":libs:vibrator:bindings",
    ":libs:vibrator:impl",
    ":libs:vibrator:public",

    // Services definition for app and lib access
    ":services:legacy-shared",
    ":services:opencode",
    ":services:opencode-compose",
    ":services:flipcash",
    ":services:flipcash-compose",

    // common UI
    ":ui:analytics",
    ":ui:biometrics",
    ":ui:core",
    ":ui:components",
    ":ui:emojis",
    ":ui:navigation",
    ":ui:resources",
    ":ui:scanner",
    ":ui:testing",
    ":ui:theme",

    // 3rd party imported dependencies
    ":vendor:kik:scanner",
    ":vendor:tipkit:tipkit",
    ":vendor:tipkit:tipkit-m2",
    ":vendor:opencv:sdk",
)
