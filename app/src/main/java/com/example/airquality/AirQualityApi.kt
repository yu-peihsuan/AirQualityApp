package com.example.airquality

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query

import com.google.gson.annotations.SerializedName

// ── 資料結構 ──────────────────────────────────────────────────────────────────

data class AqiRecord(
    val sitename: String,
    val county: String,
    val aqi: String,
    val status: String,
    @SerializedName("pm2.5") val pm25: String,
    val latitude: String,
    val longitude: String,
    val publishtime: String? = null,
    @SerializedName("windspeed") val windSpeed: String = "0",
    @SerializedName("winddirection") val windDirection: String = "0"
)

data class AirQualityResponse(
    val status: String,
    val county: String?,
    val message: String,
    val records: List<AqiRecord> = emptyList()
)

data class StructuredEvent(
    @SerializedName("event_type") val eventType: String?,
    val severity: String?
)

data class NewsRecord(
    val source: String,
    val region: String,
    val title: String,
    val summary: String,
    val url: String,
    @SerializedName("published_at") val publishedAt: String,
    val timestamp: String,
    // 民眾回報專用欄位（爬蟲新聞不含，預設 null）
    val category: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerializedName("structured_event") val structuredEvent: StructuredEvent? = null,
    @SerializedName("is_confirmed") val isConfirmed: Boolean? = null
)

data class NewsResponse(
    val status: String,
    val region: String?,
    val message: String,
    val records: List<NewsRecord> = emptyList()
)

// ── 民眾回報 API 資料結構 ─────────────────────────────────────────────────────

data class ReportRequest(
    val location: String,
    val category: String,
    val description: String,
    val latitude: Double? = null,
    val longitude: Double? = null
    // 不再送出 device_id：後端改由 access token 取得裝置身分，
    // 客戶端自報的識別碼無法作為回報頻率限制的依據。
)

data class ReportResponse(
    val status: String,
    val message: String,
    @SerializedName("is_confirmed") val isConfirmed: Boolean = false
)

// ── RAG 建議 API 資料結構 ─────────────────────────────────────────────────────

data class RagUserProfile(
    @SerializedName("age_group")          val ageGroup: String = "adult",
    @SerializedName("is_pregnant")        val isPregnant: Boolean = false,
    @SerializedName("has_asthma")         val hasAsthma: Boolean = false,
    @SerializedName("has_cardiovascular") val hasCardiovascular: Boolean = false,
    @SerializedName("has_allergy")        val hasAllergy: Boolean = false,
    @SerializedName("other_notes")        val otherNotes: String? = null
)

data class RagAdviceRequest(
    val county: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val aqi: Int? = null,
    val pm25: Double? = null,
    @SerializedName("user_profile") val userProfile: RagUserProfile = RagUserProfile()
)

data class DownwindSource(
    val lat: Double,
    val lng: Double,
    val count: Int,
    val intensity: Double,
    @SerializedName("radius_km")       val radiusKm: Double,
    @SerializedName("dominant_type")   val dominantType: String,
    @SerializedName("distance_km")     val distanceKm: Double,
    @SerializedName("bearing_to_user") val bearingToUser: Double
)

data class RagAdviceResponse(
    val status: String,
    val county: String?,
    val aqi: Int?,
    val pm25: Double?,
    @SerializedName("wind_speed")       val windSpeed: Double?,
    @SerializedName("wind_direction")   val windDirection: Double?,
    @SerializedName("aqi_level")        val aqiLevel: String?,
    val advice: String?,
    @SerializedName("event_context")    val eventContext: String?,
    @SerializedName("is_downwind")      val isDownwind: Boolean?,
    @SerializedName("downwind_sources") val downwindSources: List<DownwindSource>?,
    @SerializedName("retrieved_rules")  val retrievedRules: List<String>?,
    val message: String? = null
)

// ── 天氣資料結構 ──────────────────────────────────────────────────────────────

data class WeatherResponse(
    val status: String,
    val county: String?,
    @SerializedName("is_raining") val isRaining: Boolean = false,
    val weather: String = "",
    val temp: Double? = null,
    val description: String = ""
)

// ── FCM Token 註冊 資料結構 ───────────────────────────────────────────────────

data class FcmTokenRequest(
    val token:      String,
    val county:     String = "",
    val lat:        Double? = null,
    val lng:        Double? = null,
    val conditions: String = ""
)

data class FcmTokenResponse(
    val status: String,
    val message: String
)

data class DailyNotificationRequest(
    val token: String,
    val enabled: Boolean,
    val hour: Int? = null,
    val minute: Int? = null
)

data class DailyNotificationResponse(
    val status: String,
    val message: String? = null
)

data class DailyNotificationTestRequest(
    val token: String
)

data class DailyNotificationTestResponse(
    val status: String,
    val message: String? = null,
    val county: String? = null,
    val aqi: Int? = null
)

// ── GIS 熱點 資料結構 ─────────────────────────────────────────────────────────

data class HotspotRecord(
    val lat: Double,
    val lng: Double,
    val count: Int,
    val intensity: Double,
    @SerializedName("radius_km")       val radiusKm: Double,
    @SerializedName("dominant_type")   val dominantType: String,
    @SerializedName("is_calm_wind")    val isCalmWind: Boolean = false,
    @SerializedName("wind_speed")      val windSpeed: Double = 0.0,
    @SerializedName("wind_direction")  val windDirection: Double = 0.0,
)

data class HotspotResponse(
    val status: String,
    val count: Int,
    val hotspots: List<HotspotRecord> = emptyList()
)

// ── IDW 空間插值估計（個人定位點空品數值）──────────────────────────────────

data class EstimateStation(
    val sitename: String,
    val county: String,
    @SerializedName("distance_km") val distanceKm: Double,
    val weight: Double,
    val value: Double,
)

data class EstimateInfo(
    val aqi: Double? = null,
    val pm25: Double? = null,
    val method: String? = null,
    val k: Int? = null,
)

data class EstimateResponse(
    val status: String,
    val estimate: EstimateInfo? = null,
    val stations: List<EstimateStation> = emptyList(),
)

// ── API 介面 ─────────────────────────────────────────────────────────────────

interface AirQualityApiService {
    @GET("api/air_quality")
    suspend fun getAirQuality(@Query("county") county: String? = null): AirQualityResponse

    @GET("api/news")
    suspend fun getNews(@Query("region") region: String? = null): NewsResponse

    @GET("api/user_reports")
    suspend fun getUserReports(@Query("region") region: String? = null): NewsResponse

    @GET("api/fire_alerts")
    suspend fun getFireAlerts(@Query("region") region: String? = null): NewsResponse

    @GET("api/forecast")
    suspend fun getForecast(@Query("county") county: String? = null): NewsResponse

    @GET("api/weather")
    suspend fun getWeather(
        @Query("county") county: String? = null,
        @Query("lat") lat: Double? = null,
        @Query("lng") lng: Double? = null
    ): WeatherResponse

    @GET("api/user_reports/history")
    suspend fun getUserReportsHistory(): NewsResponse

    @POST("api/report")
    suspend fun submitReport(@Body request: ReportRequest): ReportResponse

    @POST("api/rag_advice")
    suspend fun getRagAdvice(@Body request: RagAdviceRequest): RagAdviceResponse

    @POST("api/fcm/register")
    suspend fun registerFcmToken(@Body request: FcmTokenRequest): FcmTokenResponse

    @PUT("api/fcm/daily-notification")
    suspend fun setDailyNotification(@Body request: DailyNotificationRequest): DailyNotificationResponse

    @POST("api/fcm/daily-notification/test")
    suspend fun testDailyNotification(@Body request: DailyNotificationTestRequest): DailyNotificationTestResponse

    @GET("api/air_quality/estimate")
    suspend fun getAqiEstimate(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double
    ): EstimateResponse

    @GET("api/hotspots")
    suspend fun getHotspots(
        @Query("min_reports") minReports: Int = 2,
        @Query("radius_km") radiusKm: Float = 1.5f,
        @Query("top_n") topN: Int = 10
    ): HotspotResponse
}

// ── Retrofit 連線實體 ─────────────────────────────────────────────────────────

// 正式後端（Cloud Run，台灣機房）；本機開發時可暫時改回 http://10.0.2.2:8000/
// 放在檔案層級，讓 AuthManager 的認證用 Retrofit 實體共用同一個位址。
internal const val BASE_URL = "https://airquality-api-968727437042.asia-east1.run.app/"

object RetrofitClient {

    /**
     * AuthInterceptor 為每個請求附上裝置憑證；
     * TokenAuthenticator 在後端回 401 時自動續期並重送一次。
     * 呼叫端（ViewModel／畫面）因此不需要知道 token 的存在。
     */
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor())
            .authenticator(TokenAuthenticator())
            .build()
    }

    val apiService: AirQualityApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AirQualityApiService::class.java)
    }
}
