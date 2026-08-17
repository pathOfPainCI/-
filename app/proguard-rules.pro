# kotlinx.serialization: keep generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.jizhang.app.**$$serializer { *; }
-keepclassmembers class com.jizhang.app.** { *** Companion; }
-keepclasseswithmembers class com.jizhang.app.** { kotlinx.serialization.KSerializer serializer(...); }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
