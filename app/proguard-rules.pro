# Keep kotlinx.serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.sarbikbetal.kestrel.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.sarbikbetal.kestrel.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
