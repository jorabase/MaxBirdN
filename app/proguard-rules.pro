# ============================================================================
# MaxBird Anti-Decompilation & Maximum R8 Obfuscation Security Rules
# Prevents reverse engineering, decompilation via MT Manager, JADX, Bytecode viewers, and APKTool
# ============================================================================

# 1. Aggressive Class & Package Flattening / Obfuscation
-repackageclasses ''
-allowaccessmodification
-dontusemixedcaseclassnames
-overloadaggressively
-mergeinterfacesaggressively

# 2. Strip Source File Names, Line Numbers, Variable Tables & Debug Information
-renamesourcefileattribute ""
-keepattributes !SourceFile,!LineNumberTable,!LocalVariableTable,!LocalVariableTypeTable,!SourceDebugExtension,!Deprecated,!MethodParameters

# Keep essential runtime annotations and signatures for Kotlin Coroutines, Moshi, & Reflection
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions

# 3. Strip All Sensitive Logs & Print Statements in Release Builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** wtf(...);
    public static *** println(...);
}

-assumenosideeffects class java.lang.Throwable {
    public void printStackTrace();
}

-assumenosideeffects class java.io.PrintStream {
    public void println(...);
    public void print(...);
}

# 4. Anti-Tamper & Security Engine Protection
-keep class com.example.security.** { *; }

# 5. Moshi & API DTO Models Protection (Prevent serialization breakdown)
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
    @com.squareup.moshi.JsonClass <fields>;
}
-keep @com.squareup.moshi.JsonClass class * { *; }
-keep class com.example.api.** {
    <fields>;
    <methods>;
}

# 6. Retrofit & OkHttp Rules
-keepattributes *Annotation*,Signature
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn okhttp3.**

# 7. Room Database Persistence Rules
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# 8. Jetpack Compose Rules
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
    @androidx.compose.runtime.ReadOnlyComposable <methods>;
}
-keep class androidx.compose.ui.** { *; }

# 9. Firebase & Google Play Services Rules
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# 10. ExoPlayer / Media3 & Coil Image Loading
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
-keep class coil.** { *; }
-dontwarn coil.**
