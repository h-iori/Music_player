# Music App ProGuard Rules
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*

# Google Play Services
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# Keep data models used in JSON serialization & backup
-keep class com.ioristudios.music.external.** { *; }
-keep class com.ioristudios.music.data.model.** { *; }
-keep class com.ioristudios.music.data.playback.** { *; }
