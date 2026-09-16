# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# ── Retrofit / Gson ───────────────────────────────────────────────────────────
# Retrofit 以反射讀取介面方法的泛型回傳型別與註解，簽章與註解不能被移除。
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# API 介面本身（方法名即為反射對應的依據）
-keep,allowobfuscation interface com.example.airquality.AirQualityApiService { *; }
-keep,allowobfuscation interface com.example.airquality.AuthApiService { *; }

# Gson 以「欄位名 ↔ JSON 鍵名」對應，欄位一旦被改名就對不上，
# 因此所有 API 資料類別的欄位與建構子一律保留。
-keep class com.example.airquality.AqiRecord { *; }
-keep class com.example.airquality.AirQualityResponse { *; }
-keep class com.example.airquality.StructuredEvent { *; }
-keep class com.example.airquality.NewsRecord { *; }
-keep class com.example.airquality.NewsResponse { *; }
-keep class com.example.airquality.ReportRequest { *; }
-keep class com.example.airquality.ReportResponse { *; }
-keep class com.example.airquality.RagUserProfile { *; }
-keep class com.example.airquality.RagAdviceRequest { *; }
-keep class com.example.airquality.DownwindSource { *; }
-keep class com.example.airquality.RagAdviceResponse { *; }
-keep class com.example.airquality.WeatherResponse { *; }
-keep class com.example.airquality.FcmTokenRequest { *; }
-keep class com.example.airquality.FcmTokenResponse { *; }
-keep class com.example.airquality.DailyNotificationRequest { *; }
-keep class com.example.airquality.DailyNotificationResponse { *; }
-keep class com.example.airquality.DailyNotificationTestRequest { *; }
-keep class com.example.airquality.DailyNotificationTestResponse { *; }
-keep class com.example.airquality.HotspotRecord { *; }
-keep class com.example.airquality.HotspotResponse { *; }
-keep class com.example.airquality.EstimateStation { *; }
-keep class com.example.airquality.EstimateInfo { *; }
-keep class com.example.airquality.EstimateResponse { *; }
-keep class com.example.airquality.DeviceRegisterRequest { *; }
-keep class com.example.airquality.AuthResponse { *; }

# Gson 的 TypeToken 需要泛型資訊
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# ── Release 版不保留 verbose／debug log ────────────────────────────────────────
# 這些 log 在 release APK 裡對使用者沒有用途，卻會把 App 內部狀態留在裝置上，
# 被同機的其他程式或使用者匯出的 bug report 一併帶走。
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
}

# ── R8 full mode 下的 Retrofit 泛型簽章 ───────────────────────────────────────
# AGP 8 起 android.enableR8.fullMode 預設開啟，R8 會剝掉「未被 keep 的型別」
# 的泛型簽章。Retrofit 靠反射讀 suspend 函式的回傳型別來挑 converter，簽章一旦
# 被剝掉，它拿到的是原始 Class 而不是 ParameterizedType，於是每個 API 呼叫都會
# 拋出：java.lang.Class cannot be cast to java.lang.reflect.ParameterizedType
#
# Retrofit 2.9.0 尚未內建這組規則（2.10 起才隨函式庫附帶），因此在此補上。
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# suspend 函式的回傳型別是藏在 Continuation 的泛型參數裡傳給 Retrofit 的
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# 帶 @GET/@POST 等註解的介面，其回傳型別一律保留泛型資訊
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>
