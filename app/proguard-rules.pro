# Type-safe navigation routes are serialized by name.
-keepclassmembers class ** {
    *** Companion;
}
-keepclasseswithmembers class com.edi.hub.** {
    kotlinx.serialization.KSerializer serializer(...);
}
