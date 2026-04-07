package com.example.airquality

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

import com.google.gson.annotations.SerializedName

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

// 定義從後端接收的資料結構 (對應你 Python 後端的格式)
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

data class NewsRecord(
    val source: String,
    val region: String,
    val title: String,
    val summary: String,
    val url: String,
    @SerializedName("published_at") val publishedAt: String,
    val timestamp: String
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

// 定義 API 請求行為
interface AirQualityApiService {
    @GET("api/air_quality")
    suspend fun getAirQuality(@Query("county") county: String? = null): AirQualityResponse

    @GET("api/weather")
    suspend fun getWeather(@Query("county") county: String? = null): WeatherResponse

    @GET("api/news")
    suspend fun getNews(@Query("region") region: String? = null): NewsResponse

    @POST("api/report")
    suspend fun submitReport(@Body request: ReportRequest): ReportResponse
}

// 建立連線實體
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
