# Keep kotlinx.serialization runtime & generated serializers intact under R8
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class de.fsauter.herbert.ble.**$$serializer { *; }
-keepclassmembers class de.fsauter.herbert.ble.** {
    *** Companion;
}
-keepclasseswithmembers class de.fsauter.herbert.ble.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class de.fsauter.herbert.data.**$$serializer { *; }
-keepclassmembers class de.fsauter.herbert.data.** {
    *** Companion;
}
-keepclasseswithmembers class de.fsauter.herbert.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class de.fsauter.herbert.ui.navigation.**$$serializer { *; }
-keepclassmembers class de.fsauter.herbert.ui.navigation.** {
    *** Companion;
}
-keepclasseswithmembers class de.fsauter.herbert.ui.navigation.** {
    kotlinx.serialization.KSerializer serializer(...);
}
