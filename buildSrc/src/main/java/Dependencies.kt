@file:Suppress("ConstPropertyName")


object Android {
    const val codeNamespace = "com.getcode"
    const val flipchatNamespace = "xyz.flipchat"
    const val flipcashNamespace = "com.flipcash"

    const val compileSdkVersion = 35
    const val minSdkVersion = 24
    const val targetSdkVersion = 35
    const val testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    const val buildToolsVersion = "34.0.0"
}

sealed class Packaging(
    majorVersion: Int,
    minorVersion: Int,
    patchVersion: Int,
) {
    val versionName = "$majorVersion.$minorVersion.$patchVersion"

    object Code: Packaging(
        majorVersion = 2,
        minorVersion = 1,
        patchVersion = 14,
    )

    object Flipcash: Packaging(
        majorVersion = 0,
        minorVersion = 2,
        patchVersion = 0,
    )

    object Flipchat: Packaging(
        majorVersion = 1,
        minorVersion = 0,
        patchVersion = 10,
    )
}

object Versions {
    const val java = "17"
    const val kotlin = "2.1.20"
    const val kotlinx_coroutines = "1.9.0"
    const val kotlinx_serialization = "1.7.3"
    const val kotlinx_datetime = "0.6.1"

    private const val ksp = "2.0.1"
    const val kotlin_ksp = "$kotlin-$ksp"

    const val android_gradle_build_tools = "8.7.1"
    const val google_services = "4.4.2"

    const val androidx_appcompat = "1.7.0"
    const val androidx_activity = "1.7.2"
    const val androidx_annotation = "1.7.1"
    const val androidx_biometrics = "1.2.0-alpha05"
    const val androidx_camerax = "1.3.2"
    const val androidx_credentials = "1.5.0"
    const val androidx_core = "1.13.1"
    const val androidx_constraint_layout = "2.1.3"
    const val androidx_lifecycle = "2.7.0"
    const val androidx_navigation = "2.8.0"
    const val androidx_browser = "1.4.0"
    const val androidx_paging = "3.2.1"
    const val androidx_room = "2.7.0"
    const val androidx_work = "2.10.1"
    const val sqlcipher = "4.5.1@aar"

    const val compose = "2025.05.00"

    const val compose_activities: String = "1.8.2"
    const val compose_view_models: String = "2.6.2"
    const val compose_navigation: String = "2.8.0"
    const val compose_paging = "3.3.0"
    const val compose_webview = "0.33.6"

    const val hilt = "2.56.2"
    const val hilt_jetpack = "1.2.0"
    const val okhttp = "4.12.0"
    const val retrofit = "2.11.0"
    const val rxjava: String = "3.1.3"
    const val rxandroid: String = "3.0.0"

    const val compose_accompanist: String = "0.24.2-alpha"
    const val compose_coil: String = "3.0.0"
    const val kin_sdk: String = "1.0.1"
    const val grpc_android: String = "1.33.1"
    const val slf4j: String = "1.7.25"
    const val firebase_bom: String = "33.1.0"
    const val crashlytics_gradle: String = "3.0.2"
    const val play_service_auth = "21.0.0"
    const val play_service_auth_phone = "18.0.2"
    const val google_play_billing = "7.1.1"

    const val grpc: String = "1.62.2"
    const val grpc_okhttp: String = "1.33.1"
    const val grpc_kotlin: String = "1.4.1"
    const val protobuf: String = "3.25.3"

    const val mp_android_chart: String = "v3.1.0"
    const val lib_phone_number_port: String = "8.12.43"
    const val lib_phone_number_google: String = "8.12.54"
    const val zxing: String = "3.3.2"

    const val androidx_test_runner = "1.4.0"
    const val junit = "4.13.1"
    const val androidx_junit = "1.1.3"
    const val espresso = "3.4.0"
    const val mixpanel = "6.4.0"

    const val markwon = "4.6.2"
    const val timber = "5.0.1"
    const val voyager = "1.1.0-beta03"
    const val protobuf_plugin = "0.9.4"

    const val sodium_bindings = "0.9.0"

    const val desugaring = "2.1.2"

    const val eventBus = "0.1.0"
}

object Classpath {
    const val android_gradle_build_tools =
        "com.android.tools.build:gradle:${Versions.android_gradle_build_tools}"
    const val kotlin_hilt_plugin = "com.google.dagger:hilt-android-gradle-plugin:${Versions.hilt}"
    const val androidx_navigation_safeargs =
        "androidx.navigation:navigation-safe-args-gradle-plugin:${Versions.androidx_navigation}"
    const val kotlin_gradle_plugin = "org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.kotlin}"
    const val kotlin_serialization_plugin =
        "org.jetbrains.kotlin:kotlin-serialization:${Versions.kotlin}"
    const val google_services = "com.google.gms:google-services:${Versions.google_services}"
    const val protobuf_plugin =
        "com.google.protobuf:protobuf-gradle-plugin:${Versions.protobuf_plugin}"

    const val crashlytics_gradle =
        "com.google.firebase:firebase-crashlytics-gradle:${Versions.crashlytics_gradle}"
    const val bugsnag = "com.bugsnag:bugsnag-android-gradle-plugin:8.+"
    const val firebase_perf = "com.google.firebase:perf-plugin:1.4.2"
    const val secrets_gradle_plugin =
        "com.google.android.libraries.mapsplatform.secrets-gradle-plugin:secrets-gradle-plugin:2.0.1"
    const val versioning_gradle_plugin = "de.nanogiants:android-versioning:2.4.0"
}

object Plugins {
    const val android_application = "com.android.application"
    const val android_library = "com.android.library"
    const val androidx_navigation_safeargs = "androidx.navigation.safeargs.kotlin"
    const val kotlin_android = "kotlin-android"
    const val kotlin_parcelize = "kotlin-parcelize"
    const val kotlin_ksp = "com.google.devtools.ksp"
    const val kotlin_kapt = "kotlin-kapt"
    const val kotlin_serialization = "org.jetbrains.kotlin.plugin.serialization"
    const val hilt = "dagger.hilt.android.plugin"
    const val google_services = "com.google.gms.google-services"
    const val firebase_crashlytics = "com.google.firebase.crashlytics"
    const val firebase_perf = "com.google.firebase.firebase-perf"
    const val bugsnag = "com.bugsnag.android.gradle"
    const val secrets_gradle_plugin =
        "com.google.android.libraries.mapsplatform.secrets-gradle-plugin"
    const val versioning_gradle_plugin = "de.nanogiants.android-versioning"
    const val jetbrains_compose_compiler = "org.jetbrains.kotlin.plugin.compose"
}

object Libs {
    const val android_desugaring = "com.android.tools:desugar_jdk_libs:${Versions.desugaring}"
    const val androidx_appcompat = "androidx.appcompat:appcompat:${Versions.androidx_appcompat}"
    const val androidx_activity = "androidx.activity:activity-ktx:{${Versions.androidx_activity}"
    const val androidx_annotation = "androidx.annotation:annotation:${Versions.androidx_annotation}"
    const val androidx_biometrics = "androidx.biometric:biometric:${Versions.androidx_biometrics}"
    const val androidx_camerax_core = "androidx.camera:camera-core:${Versions.androidx_camerax}"
    const val androidx_camerax_camera2 =
        "androidx.camera:camera-camera2:${Versions.androidx_camerax}"
    const val androidx_camerax_lifecycle =
        "androidx.camera:camera-lifecycle:${Versions.androidx_camerax}"
    const val androidx_camerax_view = "androidx.camera:camera-view:${Versions.androidx_camerax}"
    const val androidx_core = "androidx.core:core-ktx:${Versions.androidx_core}"
    const val androidx_constraint_layout =
        "androidx.constraintlayout:constraintlayout:${Versions.androidx_constraint_layout}"

    const val androidx_credentials = "androidx.credentials:credentials:${Versions.androidx_credentials}"
    const val androidx_credentials_play_auth = "androidx.credentials:credentials-play-services-auth:${Versions.androidx_credentials}"

    const val androidx_localbroadcastmanager = "androidx.localbroadcastmanager:localbroadcastmanager:1.0.0"
    const val androidx_lifecycle_runtime =
        "androidx.lifecycle:lifecycle-runtime-ktx:${Versions.androidx_lifecycle}"
    const val androidx_navigation_fragment =
        "androidx.navigation:navigation-fragment-ktx:${Versions.androidx_navigation}"
    const val androidx_navigation_ui =
        "androidx.navigation:navigation-ui-ktx:${Versions.androidx_navigation}"
    const val androidx_browser = "androidx.browser:browser:${Versions.androidx_browser}"
    const val androidx_paging_runtime =
        "androidx.paging:paging-runtime-ktx:${Versions.androidx_paging}"

    const val androidx_room_runtime = "androidx.room:room-runtime:${Versions.androidx_room}"
    const val androidx_room_rxjava3 = "androidx.room:room-rxjava3:${Versions.androidx_room}"
    const val androidx_room_compiler = "androidx.room:room-compiler:${Versions.androidx_room}"
    const val androidx_room_ktx = "androidx.room:room-ktx:${Versions.androidx_room}"
    const val androidx_room_paging = "androidx.room:room-paging:${Versions.androidx_room}"

    const val androidx_work = "androidx.work:work-runtime:${Versions.androidx_work}"
    const val sqlcipher = "net.zetetic:android-database-sqlcipher:${Versions.sqlcipher}"

    const val coil3 = "io.coil-kt.coil3:coil-compose:${Versions.compose_coil}"
    const val coil3_network = "io.coil-kt.coil3:coil-network-okhttp:${Versions.compose_coil}"

    const val hilt = "com.google.dagger:hilt-android:${Versions.hilt}"
    const val hilt_worker = "androidx.hilt:hilt-work:${Versions.hilt_jetpack}"
    const val hilt_android_compiler = "com.google.dagger:hilt-android-compiler:${Versions.hilt}"
    const val hilt_compiler = "androidx.hilt:hilt-compiler:${Versions.hilt_jetpack}"
    const val hilt_android_test = "com.google.dagger:hilt-android-testing:${Versions.hilt}"

    const val kotlin_stdlib = "org.jetbrains.kotlin:kotlin-stdlib:${Versions.kotlin}"
    const val kotlinx_coroutines_core =
        "org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinx_coroutines}"
    const val kotlinx_coroutines_rx3 =
        "org.jetbrains.kotlinx:kotlinx-coroutines-rx3:${Versions.kotlinx_coroutines}"
    const val kotlinx_coroutines_test =
        "org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.kotlinx_coroutines}"
    const val kotlinx_collections_immutable =
        "org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.6"
    const val kotlinx_datetime =
        "org.jetbrains.kotlinx:kotlinx-datetime:${Versions.kotlinx_datetime}"
    const val kotlinx_serialization_core =
        "org.jetbrains.kotlinx:kotlinx-serialization-core:${Versions.kotlinx_serialization}"
    const val kotlinx_serialization_json =
        "org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinx_serialization}"

    const val okhttp = "com.squareup.okhttp3:okhttp:${Versions.okhttp}"
    const val okhttp_logging_interceptor =
        "com.squareup.okhttp3:logging-interceptor:${Versions.okhttp}"

    const val androidx_datastore = "androidx.datastore:datastore-preferences:1.1.1"
    const val androidx_constraint_layout_compose =
        "androidx.constraintlayout:constraintlayout-compose:1.0.1"
    const val androidx_lifecycle_viewmodel =
        "androidx.lifecycle:lifecycle-viewmodel-ktx:${Versions.androidx_lifecycle}"

    const val compose_bom = "androidx.compose:compose-bom:${Versions.compose}"
    const val compose_accompanist =
        "com.google.accompanist:accompanist-systemuicontroller:${Versions.compose_accompanist}"
    const val compose_animation = "androidx.compose.animation:animation"
    const val compose_ui = "androidx.compose.ui:ui"
    const val compose_ui_graphics = "androidx.compose.ui:ui-graphics"
    const val compose_ui_tools = "androidx.compose.ui:ui-tooling"
    const val compose_ui_tools_preview =
        "androidx.compose.ui:ui-tooling-preview"
    const val compose_foundation = "androidx.compose.foundation:foundation"
    const val compose_material = "androidx.compose.material:material"
    const val compose_materialIconsCore = "androidx.compose.material:material-icons-core"
    const val compose_materialIconsExtended =
        "androidx.compose.material:material-icons-extended-android"
    const val compose_activities =
        "androidx.activity:activity-compose:${Versions.compose_activities}"
    const val compose_view_models =
        "androidx.lifecycle:lifecycle-viewmodel-compose:${Versions.compose_view_models}"
    const val compose_livedata = "androidx.compose.runtime:runtime-livedata"
    const val compose_navigation =
        "androidx.navigation:navigation-compose:${Versions.compose_navigation}"
    const val compose_paging = "androidx.paging:paging-compose:${Versions.compose_paging}"
    const val compose_voyager_navigation =
        "cafe.adriel.voyager:voyager-navigator:${Versions.voyager}"
    const val compose_voyager_navigation_hilt =
        "cafe.adriel.voyager:voyager-hilt:${Versions.voyager}"
    const val compose_voyager_navigation_bottomsheet =
        "cafe.adriel.voyager:voyager-bottom-sheet-navigator:${Versions.voyager}"
    const val compose_voyager_navigation_tabs =
        "cafe.adriel.voyager:voyager-tab-navigator:${Versions.voyager}"
    const val compose_voyager_navigation_transitions =
        "cafe.adriel.voyager:voyager-transitions:${Versions.voyager}"
    const val compose_webview = "io.github.kevinnzou:compose-webview:${Versions.compose_webview}"

    const val rxjava = "io.reactivex.rxjava3:rxjava:${Versions.rxjava}"
    const val rxandroid = "io.reactivex.rxjava3:rxandroid:${Versions.rxandroid}"

    const val slf4j = "org.slf4j:slf4j-android:${Versions.slf4j}"
    const val grpc_android = "io.grpc:grpc-android:${Versions.grpc_android}"
    const val kin_sdk = "org.kin.sdk.android:base:${Versions.kin_sdk}"

    const val firebase_bom = "com.google.firebase:firebase-bom:${Versions.firebase_bom}"
    const val firebase_analytics = "com.google.firebase:firebase-analytics"
    const val firebase_crashlytics = "com.google.firebase:firebase-crashlytics"
    const val firebase_messaging = "com.google.firebase:firebase-messaging"
    const val firebase_installations = "com.google.firebase:firebase-installations"
    const val firebase_perf = "com.google.firebase:firebase-perf"

    const val play_integrity = "com.google.android.play:integrity:1.3.0"
    const val play_service_auth =
        "com.google.android.gms:play-services-auth:${Versions.play_service_auth}"
    const val play_service_auth_phone =
        "com.google.android.gms:play-services-auth-api-phone:${Versions.play_service_auth_phone}"

    const val google_play_billing_runtime = "com.android.billingclient:billing:${Versions.google_play_billing}"
    const val google_play_billing_ktx = "com.android.billingclient:billing-ktx:${Versions.google_play_billing}"

    const val grpc_okhttp = "io.grpc:grpc-okhttp:${Versions.grpc_okhttp}"
    const val grpc_kotlin = "io.grpc:grpc-kotlin-stub:${Versions.grpc_kotlin}"
    const val grpc_protobuf = "io.grpc:grpc-protobuf:${Versions.grpc}"
    const val grpc_protobuf_lite = "io.grpc:grpc-protobuf-lite:${Versions.grpc}"
    const val grpc_stub = "io.grpc:grpc-stub:${Versions.grpc}"
    const val protobuf_java = "com.google.protobuf:protobuf-java:${Versions.protobuf}"
    const val protobuf_kotlin_lite = "com.google.protobuf:protobuf-kotlin-lite:${Versions.protobuf}"

    const val inject = "javax.inject:javax.inject:1"

    const val mp_android_chart = "com.github.PhilJay:MPAndroidChart:${Versions.mp_android_chart}"
    const val lib_phone_number_port =
        "io.michaelrocks:libphonenumber-android:${Versions.lib_phone_number_port}"
    const val lib_phone_number_google =
        "com.googlecode.libphonenumber:libphonenumber:${Versions.lib_phone_number_google}"
    const val hilt_nav_compose = "androidx.hilt:hilt-navigation-compose:${Versions.hilt_jetpack}"
    const val zxing = "com.google.zxing:core:${Versions.zxing}"

    const val retrofit = "com.squareup.retrofit2:retrofit:${Versions.retrofit}"
    const val retrofit_converter = "com.squareup.retrofit2:converter-gson:${Versions.retrofit}"

    const val androidx_test_runner =
        "androidx.test:runner:${Versions.androidx_test_runner}"
    const val androidx_junit =
        "androidx.test.ext:junit:${Versions.androidx_junit}"
    const val junit =
        "junit:junit:${Versions.junit}"
    const val espresso_core = "androidx.test.espresso:espresso-core:${Versions.espresso}"
    const val espresso_contrib = "androidx.test.espresso:espresso-contrib:${Versions.espresso}"
    const val espresso_intents = "androidx.test.espresso:espresso-intents:${Versions.espresso}"
    const val mixpanel = "com.mixpanel.android:mixpanel-android:${Versions.mixpanel}"

    const val markwon_core = "io.noties.markwon:core:${Versions.markwon}"
    const val markwon_linkify = "io.noties.markwon:linkify:${Versions.markwon}"
    const val markwon_ext_strikethrough = "io.noties.markwon:ext-strikethrough:${Versions.markwon}"

    const val timber = "com.jakewharton.timber:timber:${Versions.timber}"
    const val bugsnag = "com.bugsnag:bugsnag-android:5.+"

    const val cloudy = "com.github.skydoves:cloudy:0.1.2"

    const val sodium_bindings =
        "com.ionspin.kotlin:multiplatform-crypto-libsodium-bindings-android:${Versions.sodium_bindings}"

    const val fingerprint_pro = "com.fingerprint.android:pro:2.4.0"

    const val haze = "dev.chrisbanes.haze:haze:0.7.3"
    const val rinku = "dev.theolm:rinku:1.1.0"
    const val rinku_compose = "dev.theolm:rinku-compose-ext:1.1.0"

    const val eventBus = "io.github.hoc081098:channel-event-bus:${Versions.eventBus}"
}
