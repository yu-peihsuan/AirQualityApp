package com.example.airquality

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
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
    val publishtime: String? = null
)

data class AirQualityResponse(
    val status: String,
    val county: String?,
    val message: String,
    val records: List<AqiRecord> = emptyList()
)

data class WeatherRecord(
    val sitename: String,
    val county: String,
    val latitude: String? = null,
    val longitude: String? = null,
    @SerializedName("WindSpeed") val windSpeed: String,
    @SerializedName("WindDirection") val windDirection: String
)

data class WeatherResponse(
    val status: String,
    val county: String?,
    val message: String,
    val records: List<WeatherRecord> = emptyList()
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
    @SerializedName("structured_event") val structuredEvent: StructuredEvent? = null
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
    @SerializedName("has_allergy")        val hasAllergy: Boolean = false
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

// ── FCM Token 註冊 資料結構 ───────────────────────────────────────────────────

data class FcmTokenRequest(
    val token: String,
    val county: String = ""
)

data class FcmTokenResponse(
    val status: String,
    val message: String
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

// ── API 介面 ─────────────────────────────────────────────────────────────────

interface AirQualityApiService {
    @GET("api/air_quality")
    suspend fun getAirQuality(@Query("county") county: String? = null): AirQualityResponse

    @GET("api/weather")
    suspend fun getWeather(@Query("county") county: String? = null): WeatherResponse

    @GET("api/news")
    suspend fun getNews(@Query("region") region: String? = null): NewsResponse

    @GET("api/user_reports")
    suspend fun getUserReports(@Query("region") region: String? = null): NewsResponse

    @GET("api/user_reports/history")
    suspend fun getUserReportsHistory(): NewsResponse

    @POST("api/report")
    suspend fun submitReport(@Body request: ReportRequest): ReportResponse

    @POST("api/rag_advice")
    suspend fun getRagAdvice(@Body request: RagAdviceRequest): RagAdviceResponse

    @POST("api/fcm/register")
    suspend fun registerFcmToken(@Body request: FcmTokenRequest): FcmTokenResponse

    @GET("api/hotspots")
    suspend fun getHotspots(
        @Query("min_reports") minReports: Int = 2,
        @Query("radius_km") radiusKm: Float = 1.5f,
        @Query("top_n") topN: Int = 10
    ): HotspotResponse
}

// ── Retrofit 連線實體 ─────────────────────────────────────────────────────────

object RetrofitClient {
    // 10.0.2.2 是 Android 模擬器連向本機電腦 (localhost) 的專用 IP
    private const val BASE_URL = "http://10.0.2.2:8000/"

    val apiService: AirQualityApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AirQualityApiService::class.java)
    }
}
