# Stored settings and history are kotlinx.serialization classes; keep their generated serializers.
-keepclassmembers @kotlinx.serialization.Serializable class app.sift.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class app.sift.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class app.sift.**
-keep class <1>$$serializer { *; }
