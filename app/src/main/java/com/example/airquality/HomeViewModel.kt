package com.example.airquality

import android.content.Context
import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.*

sealed class AqiUiState {
    object Loading : AqiUiState()
    data class Success(
        val data: AirQualityResponse,
        val nearestRecord: AqiRecord,
        val displayRegion: String
    ) : AqiUiState()
    data class Error(val message: String) : AqiUiState()
}

sealed class WeatherUiState {
    object Loading : WeatherUiState()
    data class Success(val nearestRecord: WeatherRecord) : WeatherUiState()
    data class Error(val message: String) : WeatherUiState()
}

sealed class RagAdviceUiState {
    object Idle : RagAdviceUiState()
    object Loading : RagAdviceUiState()
    data class Success(val response: RagAdviceResponse) : RagAdviceUiState()
    data class Error(val message: String) : RagAdviceUiState()
}

class HomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<AqiUiState>(AqiUiState.Loading)
    val uiState: StateFlow<AqiUiState> = _uiState.asStateFlow()

    private val _weatherState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val weatherState: StateFlow<WeatherUiState> = _weatherState.asStateFlow()

    private val _ragAdviceState = MutableStateFlow<RagAdviceUiState>(RagAdviceUiState.Idle)
    val ragAdviceState: StateFlow<RagAdviceUiState> = _ragAdviceState.asStateFlow()

    // GPS 座標（由 fetchAirQualityByLocation 設定，供下風處判斷使用）
    private var _userLat: Double? = null
    private var _userLng: Double? = null

    fun fetchAirQuality(context: Context?, address: String) {
        viewModelScope.launch {
            _uiState.value = AqiUiState.Loading
            _weatherState.value = WeatherUiState.Loading

            try {
                val aqiResponse = RetrofitClient.apiService.getAirQuality(null)
                val aqiRecords = aqiResponse.records ?: emptyList()
                if (aqiRecords.isEmpty()) throw Exception("沒有取得 AQI 資料")

                val (lat, lng, displayRegion) = getCoordinates(context, address)
                val nearestAqi = findNearestStation(aqiRecords, lat, lng)
                _uiState.value = AqiUiState.Success(aqiResponse, nearestAqi, displayRegion)

                // 取得最近氣象站
                fetchWeatherForStation(lat, lng)

            } catch (e: Exception) {
                _uiState.value = AqiUiState.Error("AQI 取得失敗: ${e.localizedMessage}")
                _weatherState.value = WeatherUiState.Error("氣象資料取得失敗")
                Log.e("HomeViewModel", "fetchAirQuality failed", e)
            }
        }
    }

    private suspend fun fetchWeatherForStation(targetLat: Double, targetLng: Double) {
        viewModelScope.launch {
            try {
                val weatherResponse = RetrofitClient.apiService.getWeather(null)
                val weatherRecords = weatherResponse.records ?: emptyList()
                if (weatherRecords.isEmpty()) {
                    _weatherState.value = WeatherUiState.Error("沒有氣象資料")
                    return@launch
                }

                val nearestWeather = findNearestStation(weatherRecords, targetLat, targetLng)
                _weatherState.value = WeatherUiState.Success(nearestWeather)

            } catch (e: Exception) {
                _weatherState.value = WeatherUiState.Error("氣象資料取得失敗")
                Log.e("HomeViewModel", "fetchWeatherForStation failed", e)
            }
        }
    }

    fun fetchAirQualityByLocation(context: Context, location: Location, fallbackAddress: String) {
        viewModelScope.launch {
            _uiState.value = AqiUiState.Loading
            _weatherState.value = WeatherUiState.Loading
            try {
                // 使用 Google Maps Geocoding API 反向地理編碼，取得縣市名
                val region = googleReverseGeocodeCounty(location.latitude, location.longitude)
                    ?: fallbackAddress.take(6).ifBlank { "目前位置" }

                val aqiResponse = RetrofitClient.apiService.getAirQuality(null)
                val aqiRecords = aqiResponse.records ?: emptyList()
                if (aqiRecords.isEmpty()) throw Exception("沒有取得 AQI 資料")

                val nearestAqi = findNearestStation(aqiRecords, location.latitude, location.longitude)
                _uiState.value = AqiUiState.Success(aqiResponse, nearestAqi, region)
                _userLat = location.latitude
                _userLng = location.longitude
                fetchWeatherForStation(location.latitude, location.longitude)
                // GPS 縣市確認後，上傳 FCM Token 給後端
                TokenManager.uploadTokenWithCounty(context, nearestAqi.county)

            } catch (e: Exception) {
                _uiState.value = AqiUiState.Error("AQI 取得失敗: ${e.localizedMessage}")
                _weatherState.value = WeatherUiState.Error("氣象資料取得失敗")
                Log.e("HomeViewModel", "fetchAirQualityByLocation failed", e)
            }
        }
    }

    fun fetchRagAdvice(context: Context) {
        viewModelScope.launch {
            _ragAdviceState.value = RagAdviceUiState.Loading

            try {
                // 1. 從 SharedPreferences 讀取健康檔案
                val healthPrefs = context.getSharedPreferences("health_profile", Context.MODE_PRIVATE)
                val ageGroupRaw   = healthPrefs.getString("health_age_group", "18-64歲") ?: "18-64歲"
                val conditionsRaw = healthPrefs.getString("health_conditions", "") ?: ""
                val conditions    = conditionsRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                val ageGroup = when {
                    ageGroupRaw.contains("18歲以下") -> "child"
                    ageGroupRaw.contains("65")    -> "elderly"
                    else                          -> "adult"
                }

                val userProfile = RagUserProfile(
                    ageGroup        = ageGroup,
                    isPregnant      = conditions.contains("懷孕中"),
                    hasAsthma       = conditions.contains("氣喘") || conditions.contains("呼吸道疾病"),
                    hasCardiovascular = conditions.contains("心血管疾病") || conditions.contains("高血壓"),
                    hasAllergy      = conditions.contains("過敏")
                )

                // 2. 取得所在縣市與 AQI（從 AQI 成功狀態取最近測站，避免重複呼叫 API）
                val county: String
                val knownAqi: Int?
                val knownPm25: Double?
                when (val aqiState = _uiState.value) {
                    is AqiUiState.Success -> {
                        county    = aqiState.nearestRecord.county.ifBlank { "台北市" }
                        knownAqi  = aqiState.nearestRecord.aqi.toIntOrNull()
                        knownPm25 = aqiState.nearestRecord.pm25.toDoubleOrNull()
                    }
                    else -> {
                        county    = "台北市"
                        knownAqi  = null
                        knownPm25 = null
                    }
                }

                // 3. 呼叫 RAG API（附上 GPS 座標供下風處判斷）
                val request = RagAdviceRequest(
                    county      = county,
                    latitude    = _userLat,
                    longitude   = _userLng,
                    aqi         = knownAqi,
                    pm25        = knownPm25,
                    userProfile = userProfile
                )
                val response = RetrofitClient.apiService.getRagAdvice(request)
                _ragAdviceState.value = RagAdviceUiState.Success(response)

            } catch (e: Exception) {
                Log.e("HomeViewModel", "fetchRagAdvice failed", e)
                _ragAdviceState.value = RagAdviceUiState.Error("AI 建議取得失敗: ${e.localizedMessage}")
            }
        }
    }

    // 找最近測站
    private fun <T> findNearestStation(records: List<T>, lat: Double, lng: Double): T {
        return records.minByOrNull {
            val rLat = when (it) {
                is AqiRecord -> it.latitude.toDoubleOrNull() ?: 0.0
                is WeatherRecord -> it.latitude?.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }
            val rLng = when (it) {
                is AqiRecord -> it.longitude.toDoubleOrNull() ?: 0.0
                is WeatherRecord -> it.longitude?.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }
            haversine(lat, lng, rLat, rLng)
        } ?: records.first()
    }

    // 中文 8 方位風向
    fun getWindDirectionString(degreesStr: String, speedStr: String): String {
        val speed = speedStr.toFloatOrNull() ?: 0f
        if (speed < 0.3f) return "無風"

        val degrees = degreesStr.toFloatOrNull() ?: return "未知"

        val directions = arrayOf(
            "北風", "東北風", "東風", "東南風",
            "南風", "西南風", "西風", "西北風"
        )

        val index = ((degrees + 22.5f) / 45f).toInt() % 8
        return directions[index]
    }

    // 計算兩點距離
    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    // 統一取得經緯度（正向地理編碼：地址 → 座標）
    private suspend fun getCoordinates(context: Context?, address: String): Triple<Double, Double, String> {
        var lat = 25.032969
        var lng = 121.516039
        var region = "臺北市中正區"

        if (address.isNotBlank()) {
            val result = googleForwardGeocode(address)
            if (result != null) {
                lat = result.first
                lng = result.second
                region = address.take(6)
            }
        }
        return Triple(lat, lng, region)
    }

    // Google Maps Geocoding API：正向地理編碼（地址 → 座標）
    private suspend fun googleForwardGeocode(address: String): Pair<Double, Double>? =
        withContext(Dispatchers.IO) {
            try {
                val encoded = java.net.URLEncoder.encode(address, "UTF-8")
                val url = URL("https://maps.googleapis.com/maps/api/geocode/json?address=$encoded&key=${BuildConfig.MAPS_API_KEY}&language=zh-TW")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                if (conn.responseCode == 200) {
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    if (json.getString("status") == "OK") {
                        val location = json.getJSONArray("results")
                            .getJSONObject(0)
                            .getJSONObject("geometry")
                            .getJSONObject("location")
                        return@withContext Pair(location.getDouble("lat"), location.getDouble("lng"))
                    }
                }
            } catch (e: Exception) { Log.e("GoogleGeocoder", e.message ?: "") }
            null
        }

    // Google Maps Geocoding API：反向地理編碼（座標 → 縣市名）
    private suspend fun googleReverseGeocodeCounty(lat: Double, lng: Double): String? =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("https://maps.googleapis.com/maps/api/geocode/json?latlng=$lat,$lng&key=${BuildConfig.MAPS_API_KEY}&language=zh-TW")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                if (conn.responseCode == 200) {
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    if (json.getString("status") == "OK") {
                        val components = json.getJSONArray("results")
                            .getJSONObject(0)
                            .getJSONArray("address_components")
                        var adminArea = ""
                        var subAdminArea = ""
                        for (i in 0 until components.length()) {
                            val comp = components.getJSONObject(i)
                            val types = comp.getJSONArray("types")
                            val typeList = (0 until types.length()).map { types.getString(it) }
                            when {
                                "administrative_area_level_1" in typeList -> adminArea = comp.getString("long_name")
                                "administrative_area_level_2" in typeList -> subAdminArea = comp.getString("long_name")
                            }
                        }
                        return@withContext (adminArea + subAdminArea).ifBlank { null }
                    }
                }
            } catch (e: Exception) { Log.e("GoogleGeocoder", e.message ?: "") }
            null
        }
}