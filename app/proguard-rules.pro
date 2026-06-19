# Keep kotlinx.serialization generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.adsbdroid.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.adsbdroid.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
