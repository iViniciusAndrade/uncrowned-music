# Uncrowned Music — código embaralhado (R8)
# pontes chamadas pelo JavaScript dos players (YouTube/SoundCloud/Vimeo)
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }
-keepattributes JavascriptInterface
# serviço de reprodução e telas declaradas no manifesto
-keep class com.uncrowned.music.PlaybackService { *; }
-keep class com.uncrowned.music.MainActivity { *; }
-dontwarn org.jetbrains.annotations.**
