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
-keep class com.mygemma3n.aiapp.data.** { *; }
-keep class com.mygemma3n.aiapp.feature.**.data.** { *; }
-keep class com.mygemma3n.aiapp.shared_utilities.** { *; }

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

# R8 Missing Classes Rules - Generated automatically by Android Gradle plugin
-dontwarn aQute.bnd.annotation.baseline.BaselineIgnore
-dontwarn aQute.bnd.annotation.spi.ServiceConsumer
-dontwarn aQute.bnd.annotation.spi.ServiceProvider
-dontwarn afu.org.checkerframework.dataflow.qual.Pure
-dontwarn afu.org.checkerframework.dataflow.qual.SideEffectFree
-dontwarn afu.org.checkerframework.framework.qual.EnsuresQualifierIf
-dontwarn afu.org.checkerframework.framework.qual.EnsuresQualifiersIf
-dontwarn com.google.firebase.crashlytics.buildtools.reloc.afu.org.checkerframework.checker.formatter.qual.ConversionCategory
-dontwarn com.google.firebase.crashlytics.buildtools.reloc.afu.org.checkerframework.checker.formatter.qual.ReturnsFormat
-dontwarn com.google.firebase.crashlytics.buildtools.reloc.afu.org.checkerframework.checker.nullness.qual.EnsuresNonNull
-dontwarn com.google.firebase.crashlytics.buildtools.reloc.afu.org.checkerframework.checker.regex.qual.Regex
-dontwarn com.google.firebase.crashlytics.buildtools.reloc.org.checkerframework.checker.formatter.qual.ConversionCategory
-dontwarn com.google.firebase.crashlytics.buildtools.reloc.org.checkerframework.checker.formatter.qual.ReturnsFormat
-dontwarn com.google.firebase.crashlytics.buildtools.reloc.org.checkerframework.checker.nullness.qual.EnsuresNonNull
-dontwarn edu.umd.cs.findbugs.annotations.Nullable
-dontwarn edu.umd.cs.findbugs.annotations.SuppressFBWarnings
-dontwarn javax.lang.model.SourceVersion
-dontwarn javax.lang.model.element.Element
-dontwarn javax.lang.model.element.ElementKind
-dontwarn javax.lang.model.type.TypeMirror
-dontwarn javax.lang.model.type.TypeVisitor
-dontwarn javax.lang.model.util.SimpleTypeVisitor8
-dontwarn javax.naming.InvalidNameException
-dontwarn javax.naming.NamingException
-dontwarn javax.naming.directory.Attribute
-dontwarn javax.naming.directory.Attributes
-dontwarn javax.naming.ldap.LdapName
-dontwarn javax.naming.ldap.Rdn
-dontwarn javax.servlet.ServletContextEvent
-dontwarn javax.servlet.ServletContextListener
-dontwarn javax.xml.stream.Location
-dontwarn javax.xml.stream.XMLStreamException
-dontwarn javax.xml.stream.XMLStreamReader
-dontwarn net.sf.saxon.Configuration
-dontwarn net.sf.saxon.dom.DOMNodeWrapper
-dontwarn net.sf.saxon.om.Item
-dontwarn net.sf.saxon.om.NamespaceUri
-dontwarn net.sf.saxon.om.NodeInfo
-dontwarn net.sf.saxon.om.Sequence
-dontwarn net.sf.saxon.om.SequenceTool
-dontwarn net.sf.saxon.sxpath.IndependentContext
-dontwarn net.sf.saxon.sxpath.XPathDynamicContext
-dontwarn net.sf.saxon.sxpath.XPathEvaluator
-dontwarn net.sf.saxon.sxpath.XPathExpression
-dontwarn net.sf.saxon.sxpath.XPathStaticContext
-dontwarn net.sf.saxon.sxpath.XPathVariable
-dontwarn net.sf.saxon.tree.wrapper.VirtualNode
-dontwarn net.sf.saxon.value.DateTimeValue
-dontwarn net.sf.saxon.value.GDateValue
-dontwarn org.apache.avalon.framework.logger.Logger
-dontwarn org.apache.log.Hierarchy
-dontwarn org.apache.log.Logger
-dontwarn org.apache.log4j.Level
-dontwarn org.apache.log4j.Logger
-dontwarn org.apache.log4j.Priority
-dontwarn org.ietf.jgss.GSSContext
-dontwarn org.ietf.jgss.GSSCredential
-dontwarn org.ietf.jgss.GSSException
-dontwarn org.ietf.jgss.GSSManager
-dontwarn org.ietf.jgss.GSSName
-dontwarn org.ietf.jgss.Oid
-dontwarn org.osgi.framework.Bundle
-dontwarn org.osgi.framework.BundleContext
-dontwarn org.osgi.framework.FrameworkUtil
-dontwarn org.osgi.framework.ServiceReference
-dontwarn org.osgi.framework.wiring.BundleRevision
-dontwarn reactor.blockhound.integration.BlockHoundIntegration

# Additional size optimizations
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkParameterIsNotNull(...);
    public static void checkNotNullParameter(...);
    public static void checkExpressionValueIsNotNull(...);
    public static void checkNotNullExpressionValue(...);
}

# Remove debug logging in release
-assumenosideeffects class java.util.logging.Logger {
    public static *** getLogger(...);
    public *** log(...);
    public *** logp(...);
    public *** logrb(...);
}

# Google Cloud Speech API and Protobuf - required for speech recognition functionality
-keep class com.google.cloud.speech.** { *; }
-keep class com.google.longrunning.** { *; }
-keep class com.google.rpc.** { *; }
-keep class com.google.cloud.location.** { *; }
-keep class com.google.api.** { *; }

# Protobuf classes (part of Google Cloud dependencies)
-keep class com.google.protobuf.** { *; }
-keep class com.google.protobuf.Descriptors$** { *; }
-keep class com.google.protobuf.DynamicMessage { *; }
-keep class com.google.protobuf.GeneratedMessage { *; }
-keep class com.google.protobuf.MapEntry { *; }
-keep class com.google.protobuf.MapField** { *; }
-keep class com.google.protobuf.Message$** { *; }
-keep class com.google.protobuf.ProtocolMessageEnum { *; }
-keep class com.google.protobuf.util.JsonFormat$** { *; }
-dontwarn com.google.protobuf.**

# Jetty ALPN/NPN (optional dependencies for gRPC networking)
-dontwarn org.eclipse.jetty.alpn.**
-dontwarn org.eclipse.jetty.npn.**

# gRPC networking stack
-keep class io.grpc.** { *; }
-dontwarn io.grpc.**
-dontwarn io.netty.**