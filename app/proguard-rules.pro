# kotlinx.serialization keeps generated serializers on the companion; R8 full mode
# cannot see the reflective link.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# Filament loads its native library and calls back into these types via JNI.
-keep class com.google.android.filament.** { *; }
-keep class com.google.android.filament.android.** { *; }
