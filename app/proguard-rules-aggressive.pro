# Aggressive ProGuard rules for maximum size reduction

# Remove all logging in release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# Remove Timber logging completely
-assumenosideeffects class timber.log.Timber** {
    public static *** tag(...);
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# Remove Firebase logging (if not critical)
-assumenosideeffects class com.google.firebase.**.logging.** {
    *;
}

# Aggressive optimization options
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification

# Remove unused resources more aggressively
-dontwarn androidx.**
-dontwarn com.google.android.material.**

# Remove reflection-based access to reduce metadata
-dontwarn sun.misc.Unsafe
-dontwarn javax.annotation.**

# Optimize string operations (more specific to avoid Object methods)
-assumenosideeffects class java.lang.String {
    public java.lang.String intern() return null;
}

# Remove debug-only classes
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkNotNull(...);
    static void checkParameterIsNotNull(...);
    static void checkNotNullParameter(...);
    static void checkExpressionValueIsNotNull(...);
    static void checkNotNullExpressionValue(...);
    static void checkReturnedValueIsNotNull(...);
    static void checkFieldIsNotNull(...);
}

# Additional protobuf warnings from build
-dontwarn com.google.protobuf.AbstractMessage$Builder
-dontwarn com.google.protobuf.AbstractMessage$BuilderParent
-dontwarn com.google.protobuf.AbstractMessage
-dontwarn com.google.protobuf.Descriptors$Descriptor
-dontwarn com.google.protobuf.Descriptors$FileDescriptor
-dontwarn com.google.protobuf.ExtensionRegistry
-dontwarn com.google.protobuf.GeneratedMessage$GeneratedExtension
-dontwarn com.google.protobuf.GeneratedMessageV3$Builder
-dontwarn com.google.protobuf.GeneratedMessageV3$BuilderParent
-dontwarn com.google.protobuf.GeneratedMessageV3$FieldAccessorTable
-dontwarn com.google.protobuf.GeneratedMessageV3
-dontwarn com.google.protobuf.Message
-dontwarn com.google.protobuf.MessageOrBuilder
-dontwarn com.google.protobuf.RepeatedFieldBuilderV3
-dontwarn com.google.protobuf.SingleFieldBuilderV3
-dontwarn com.google.protobuf.TypeRegistry$Builder
-dontwarn com.google.protobuf.TypeRegistry
-dontwarn com.google.protobuf.UnknownFieldSet
-dontwarn io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts
-dontwarn io.grpc.netty.shaded.io.grpc.netty.InternalNettyChannelCredentials
-dontwarn io.grpc.netty.shaded.io.grpc.netty.InternalProtocolNegotiator$ClientFactory
-dontwarn io.grpc.netty.shaded.io.netty.handler.ssl.SslContext
-dontwarn io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder
-dontwarn io.grpc.netty.shaded.io.netty.util.AsciiString