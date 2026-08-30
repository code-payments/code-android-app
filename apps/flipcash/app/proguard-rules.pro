# Preserve source file names and line numbers for stack traces (call site tracking, Bugsnag)
-keepattributes SourceFile,LineNumberTable

# Keep AppRoute class names. `annotatedEntry` derives each screen's root test tag
# from the route's simple name (NavMetadata.screenRootTag), so obfuscating these
# renames every screen-root resource-id the UI tests address.
-keepnames class com.flipcash.app.core.AppRoute
-keepnames class com.flipcash.app.core.AppRoute$**

# Protobuf — keep all generated message classes and their builders.
# Using type-hierarchy rules so new packages / updated gRPC stubs are
# caught automatically instead of listing every gen package.
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite$Builder { *; }

# gRPC — keep generated service stubs (abstract + concrete)
-keep class * extends io.grpc.stub.AbstractStub { *; }
-keep class * implements io.grpc.BindableService { *; }

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
