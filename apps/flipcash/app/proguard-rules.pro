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

# Flipcash protos
-keep class com.codeinc.flipcash.gen.** {*;}
# Flipcash services
-keep class com.flipcash.services.** {*;}
# Opencode protos
-keep class com.codeinc.opencode.gen.** {*;}
# Opencode services
-keep class com.getcode.opencode.** {*;}

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

# https://github.com/firebase/firebase-android-sdk/issues/3688
-keep class org.json.** { *; }
-keepclassmembers class org.json.** { *; }

 # With R8 full mode generic signatures are stripped for classes that are not
 # kept. Suspend functions are wrapped in continuations where the type argument
 # is used.
 -keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
