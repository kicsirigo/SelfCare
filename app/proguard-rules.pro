# Preserve kotlinx.serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.selfcare.app.**$$serializer { *; }
-keepclassmembers class com.selfcare.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.selfcare.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Preserve Room entities & bundled ML Kit / Play Services task classes
-keep class com.selfcare.app.data.local.entity.** { *; }
-dontwarn com.google.mlkit.**
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }
-keep class com.google.android.odml.** { *; }

# Preserve ONNX Runtime JNI classes & native callbacks
-dontwarn ai.onnxruntime.**
-keep class ai.onnxruntime.** { *; }
