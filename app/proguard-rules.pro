# Music App ProGuard Rules
# Add project specific ProGuard rules here.
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*

# Google Play Services
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**
