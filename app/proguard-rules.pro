# Stored settings and history are kotlinx.serialization classes; keep their generated serializers.
-keepclassmembers @kotlinx.serialization.Serializable class app.notishade.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class app.notishade.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class app.notishade.**
-keep class <1>$$serializer { *; }
