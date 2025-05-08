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

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep class net.sqlcipher.** { *; }

## Code API
-keep class com.codeinc.gen.** {*;}
-keep class com.google.protobuf.** { *; }

# Keep our scan classes that interact with native
-keep class com.kik.scan.** { *; }

# BouncyCastle
-keep public class org.bouncycastle.** # Refine this further!
-keepclassmembers class org.bouncycastle.crypto.** {
    <methods>;
}

-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

-keepattributes SourceFile,LineNumberTable        # Keep file names and line numbers.
-keep public class * extends java.lang.Exception
-keep public class * extends com.getcode.network.repository.ErrorSubmitIntent
-keep public class * extends com.getcode.network.repository.ErrorSubmitIntentException
-keep public class * extends com.getcode.network.repository.WithdrawException
-keep public class * extends com.getcode.network.repository.FetchUpgradeableIntentsException
-keep public class * extends com.getcode.network.repository.AirdropException

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

# libsodium
-keep class com.ionspin.kotlin.crypto.** { *; }
-keep class com.sun.jna.** { *; }
-dontwarn java.awt.Component
-dontwarn java.awt.GraphicsEnvironment
-dontwarn java.awt.HeadlessException
-dontwarn java.awt.Window