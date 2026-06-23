# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keep class com.gratia.music.data.model.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Media3
-keep class androidx.media3.** { *; }
