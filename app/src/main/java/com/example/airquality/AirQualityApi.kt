package com.example.airquality

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
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

// 定義 API 請求行為
interface AirQualityApiService {
    @GET("api/air_quality")
    suspend fun getAirQuality(@Query("county") county: String? = null): AirQualityResponse

    @GET("api/weather")
    suspend fun getWeather(@Query("county") county: String? = null): WeatherResponse
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
