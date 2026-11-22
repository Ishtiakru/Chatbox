-keepattributes Signature,*Annotation*,EnclosingMethod
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}
-keep class com.example.data.model.** { *; }
