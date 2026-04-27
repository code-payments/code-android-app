-optimizationpasses 5
-dontusemixedcaseclassnames
-verbose
#-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*,!code/allocation/variable
-obfuscationdictionary shuffled-dictionary.txt
-classobfuscationdictionary shuffled-dictionary.txt

-keepclasseswithmembernames class * {
    native <methods>;
}

-keepclassmembers class **.R$* {
    public static <fields>;
}

# Keep screen names
-keepnames class * implements cafe.adriel.voyager.core.screen.Screen

# Keep AppRoute class names for analytics screen tracking
-keepnames class com.flipcash.app.core.AppRoute
-keepnames class com.flipcash.app.core.AppRoute$**

# Protobuf — keep all generated message classes and their builders.
# Using type-hierarchy rules so new packages / updated gRPC stubs are
# caught automatically instead of listing every gen package.
-keep class com.google.protobuf.** { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite$Builder { *; }

# gRPC — keep generated service stubs (abstract + concrete)
-keep class * extends io.grpc.stub.AbstractStub { *; }
-keep class * implements io.grpc.BindableService { *; }

# Flipcash services
-keep class com.flipcash.services.** { *; }
# Opencode services
-keep class com.getcode.opencode.** { *; }

# Keep our scan classes that interact with native
-keep class com.kik.scan.** { *; }

-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

-keepattributes SourceFile,LineNumberTable        # Keep file names and line numbers.
-keep public class * extends java.lang.Exception

# https://github.com/firebase/firebase-android-sdk/issues/3688
-keep class org.json.** { *; }
-keepclassmembers class org.json.** { *; }

# Keep generic signature of Call, Response (R8 full mode strips signatures from non-kept items).
 -keep,allowobfuscation,allowshrinking interface retrofit2.Call
 -keep,allowobfuscation,allowshrinking class retrofit2.Response

 # With R8 full mode generic signatures are stripped for classes that are not
 # kept. Suspend functions are wrapped in continuations where the type argument
 # is used.
 -keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
