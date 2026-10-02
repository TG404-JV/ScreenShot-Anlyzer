# R8 / ProGuard rules for ScreenIntel (com.littech.screenintel)

# Keep model data classes
-keepclassmembers class com.littech.screenintel.model.** {
    <fields>;
    public <methods>;
}

# Keep classifier and extracted entity models for JSON serialization
-keepclassmembers class com.littech.screenintel.core.classifier.** {
    <fields>;
    public <methods>;
}

# Keep Kotlin Serialization metadata and navigation keys
-keepclassmembers class * implements androidx.navigation3.runtime.NavKey {
    <fields>;
    public <methods>;
}

# Keep ViewModels and constructors for ViewModelProvider reflection
-keep class * extends androidx.lifecycle.ViewModel {
    <init>();
    <init>(...);
}
-keep class * extends androidx.lifecycle.AndroidViewModel {
    <init>(android.app.Application);
    <init>(android.app.Application, ...);
}
-keepclassmembers class com.littech.screenintel.ui.main.MainScreenViewModel {
    public <init>(...);
}

# Keep WorkManager Worker classes
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.littech.screenintel.data.sync.** { *; }

# Keep ML Kit text recognition
-keep class com.google.mlkit.vision.** { *; }
-dontwarn com.google.mlkit.vision.**

# Keep Coroutines internals
-dontwarn kotlinx.coroutines.**

# Strip debug logs in release
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ----------------------------------------------------
# AGGRESSIVE CLASS RENAMING, REPACKAGING & OBFUSCATION
# ----------------------------------------------------
# Repackage all obfuscated classes into a single package to compress DEX strings
-repackageclasses 'com.littech.screenintel.obf'
-allowaccessmodification
-mergeinterfacesaggressively
-overloadaggressively

# Rename source file attributes while preserving line numbers for Play Console stacktrace deobfuscation
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

