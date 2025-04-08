-optimizationpasses 5
-dontusemixedcaseclassnames
-verbose
#-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*,!code/allocation/variable
-obfuscationdictionary shuffled-dictionary.txt
-classobfuscationdictionary shuffled-dictionary.txt

-keepclasseswithmembernames class * {
    native <methods>;
}
#-keepclasseswithmembers class * {
#    public <init>(android.content.Context, android.util.AttributeSet);
#}

#-keepclasseswithmembers class * {
#    public <init>(android.content.Context, android.util.AttributeSet, int);
#}

#-keepclassmembers class * extends android.app.Activity {
#   public void *(android.view.View);
#}

#-keepclassmembers enum * {
#    public static **[] values();
#    public static ** valueOf(java.lang.String);
#}

-keepclassmembers class **.R$* {
    public static <fields>;
}

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

-keep public class * extends java.lang.Exception

# https://github.com/firebase/firebase-android-sdk/issues/3688
-keep class org.json.** { *; }
-keepclassmembers class org.json.** { *; }

 # With R8 full mode generic signatures are stripped for classes that are not
 # kept. Suspend functions are wrapped in continuations where the type argument
 # is used.
 -keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation