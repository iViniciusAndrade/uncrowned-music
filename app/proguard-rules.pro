# Uncrowned Music — código embaralhado (R8)
# pontes chamadas pelo JavaScript dos players (YouTube/SoundCloud/Vimeo)
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }
-keepattributes JavascriptInterface
# serviço de reprodução e telas declaradas no manifesto
-keep class com.uncrowned.music.PlaybackService { *; }
-keep class com.uncrowned.music.MainActivity { *; }
-dontwarn org.jetbrains.annotations.**
# IA local (MediaPipe): chamada pela parte nativa
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**
-dontwarn com.google.auto.value.**
-dontwarn javax.lang.model.**
