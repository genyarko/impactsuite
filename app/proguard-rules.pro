# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Google AI Edge LiteRT / TensorFlow Lite
-keep class com.google.ai.edge.litert.** { *; }
-keep class org.tensorflow.lite.** { *; }
-keepclassmembers class * {
    @org.tensorflow.lite.annotations.UsedByReflection *;
}

# Room Database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * {
    @androidx.room.* <fields>;
}

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class com.google.gson.stream.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep data classes
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}
-keep,allowobfuscation @interface com.google.gson.annotations.SerializedName

# Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Hilt
-keep,allowobfuscation,allowshrinking @dagger.hilt.android.EarlyEntryPoint class *
-keep,allowobfuscation,allowshrinking @dagger.hilt.android.EntryPoint class *

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context,androidx.work.WorkerParameters);
}
-keep class androidx.work.** { *; }

# CameraX
-keep class androidx.camera.** { *; }
-keep interface androidx.camera.** { *; }

# Model classes
-keep class com.example.mygemma3n.data.** { *; }
-keep class com.example.mygemma3n.feature.**.data.** { *; }
-keep class com.example.mygemma3n.shared_utilities.** { *; }

# Enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Logging
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

-assumenosideeffects class timber.log.Timber* {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# Crashlytics
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# General Android
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-dontwarn android.arch.**
-dontwarn android.lifecycle.**
-keep class android.arch.** { *; }
-keep class android.lifecycle.** { *; }

# MediaPipe optimizations
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# Remove unused Google Play Services
-dontwarn com.google.android.gms.mlkit.**
-dontwarn com.google.mlkit.vision.segmentation.**

# PDF processing handled by Android native PdfRenderer + ML Kit
# DOCX processing uses Apache POI
-dontwarn org.apache.poi.**

# Network optimization
-dontwarn okhttp3.internal.**
-dontwarn retrofit2.Platform$Java8