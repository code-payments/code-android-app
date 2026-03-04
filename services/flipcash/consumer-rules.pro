# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# libsodium
-keep class com.ionspin.kotlin.crypto.** { *; }
-keep class com.sun.jna.** { *; }
-dontwarn java.awt.Component
-dontwarn java.awt.GraphicsEnvironment
-dontwarn java.awt.HeadlessException
-dontwarn java.awt.Window