# Preserve source file names and line numbers for stack traces (call site tracking, Bugsnag)
-keepattributes SourceFile,LineNumberTable

# Keep every navigation route class. `annotatedEntry` derives each screen's root test
# tag from `T::class.simpleName` (NavMetadata.screenRootTag), and `getSimpleName()` on a
# nested class reads the `InnerClasses` attribute, which R8 emits only for classes matched
# by a full -keep. Under -keepnames the binary name survives but the attribute does not, so
# `AppRoute.Main.Scanner` reports `AppRoute$Main$Scanner` and the tag becomes
# `app_route$main$scanner_screen`. The global -keepattributes InnerClasses does not change
# that, and neither does reading the binary name in Kotlin: routes outside AppRoute, such as
# `OnboardingStep`, need their names kept regardless. Matching on the NavKey supertype covers
# both hierarchies and any future one. Class-only, with no member wildcard, so members stay
# shrinkable and renameable; the whole rule costs 137 classes and 16 KB.
#
# Only builds with BuildConfig.UI_TESTABLE expose these tags as resource-ids, so this buys
# nothing for the shipping release — it lets Maestro run against a minified build.
-keep class * implements androidx.navigation3.runtime.NavKey

# Protobuf keep rules ship with the contract packages themselves, from 0.3.0 on:
# com.flipcash:{ocp,flipcash2}-client-protocol carry them in META-INF/proguard/, which
# R8 reads straight out of the jar. Downgrading either pin below 0.3.0 silently removes
# the app's only protobuf keep rule.

# gRPC — keep the generated client stubs
-keep class * extends io.grpc.stub.AbstractStub { *; }

# Keep our scan classes that interact with native. The scanner's JNI constructs
# these from C++ by name (FindClass("com/kik/scan/UsernameKikCode"), GetMethodID
# for <init>) and reads their backing fields directly (GetFieldID for "_username",
# "nativePtr"), none of which AGP's default native-methods rule covers. Five
# classes, so the wildcard costs little.
-keep class com.kik.scan.** { *; }

-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# Error telemetry reads exception names at runtime — Coinbase onramp traces send
# `it::class.simpleName` as `errorType`, and Events.kt does the same for analytics.
# Those are plain strings by the time they leave the device, so the Bugsnag mapping
# upload cannot repair them; the names have to survive obfuscation. Nothing reads
# the members, and an exception nothing throws need not survive, so this is
# -keepnames rather than a full keep.
-keepnames public class * extends java.lang.Throwable
