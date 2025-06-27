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

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# Google AI Edge / LiteRT
-keep class com.google.ai.edge.litert.** { *; }
-keep class org.tensorflow.lite.** { *; }
-keepattributes *Annotation*

# MediaPipe
-keep class com.google.mediapipe.** { *; }

# Model files
-keep class * extends com.google.ai.edge.litert.LiteRtModel { *; }

# JNI
-keepclasseswithmembernames class * {
    native <methods>;
}

# Reflection
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod