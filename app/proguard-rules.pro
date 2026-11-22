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
