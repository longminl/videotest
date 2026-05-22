-keepattributes Signature
-keepattributes *Annotation*

# Retrofit
-keep class com.videocollect.app.api.** { *; }
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# Gson
-keep class com.videocollect.app.api.models.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
