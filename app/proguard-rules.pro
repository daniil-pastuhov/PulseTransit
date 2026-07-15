# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/daniil.pastuhov/Library/Android/sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.

# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep rules here:

# Ktor
-keep class io.ktor.** { *; }

# kotlinx-serialization
-keepattributes *Annotation*, EnclosingMethod, Signature
-keepnames class kotlinx.serialization.internal.GeneratedSerializer { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keepclassmembers class * {
    @kotlinx.serialization.Serializer *;
}

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Koin
-keep class org.koin.** { *; }
