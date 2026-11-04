# libwebrtc is consumed as a prebuilt AAR; keep its JNI surface intact.
-keep class org.webrtc.** { *; }
-keep class io.github.webrtcsdk.** { *; }
-dontwarn org.webrtc.**

# Ktor / OkHttp
-dontwarn org.slf4j.**
-dontwarn kotlinx.coroutines.debug.**
-keepclassmembers class io.ktor.** { volatile <fields>; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.churchmedia.intercom.core.signaling.** {
    *** Companion;
}
-keepclasseswithmembers class com.churchmedia.intercom.core.signaling.** {
    kotlinx.serialization.KSerializer serializer(...);
}
