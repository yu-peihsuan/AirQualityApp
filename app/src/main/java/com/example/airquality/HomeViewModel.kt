package com.example.airquality

import android.content.Context
import android.location.Geocoder
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
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

class HomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<AqiUiState>(AqiUiState.Loading)
    val uiState: StateFlow<AqiUiState> = _uiState.asStateFlow()

    private val _weatherState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val weatherState: StateFlow<WeatherUiState> = _weatherState.asStateFlow()

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

    // 統一取得經緯度
    private suspend fun getCoordinates(context: Context?, address: String): Triple<Double, Double, String> {
        var lat = 25.032969
        var lng = 121.516039
        var region = "臺北市中正區"

        if (context != null && address.isNotBlank()) {
            val result = try { geocode(context, address) } catch (e: Exception) { null }
            val fallback = result ?: getCoordinatesFromOSM(address)

            if (fallback != null) {
                lat = fallback.first
                lng = fallback.second
                region = address.take(6)
            }
        }
        return Triple(lat, lng, region)
    }

    private suspend fun geocode(context: Context, address: String): Pair<Double, Double>? =
        withContext(Dispatchers.IO) {
            val geocoder = Geocoder(context)
            val addresses = @Suppress("DEPRECATION") geocoder.getFromLocationName(address, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                return@withContext Pair(addr.latitude, addr.longitude)
            }
            null
        }

    private suspend fun getCoordinatesFromOSM(address: String): Pair<Double, Double>? =
        withContext(Dispatchers.IO) {
            try {
                val encoded = java.net.URLEncoder.encode(address, "UTF-8")
                val url = URL("https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=1")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "AirQualityApp/1.0")
                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val arr = JSONArray(response)
                    if (arr.length() > 0) {
                        val obj = arr.getJSONObject(0)
                        val lat = obj.getString("lat").toDoubleOrNull()
                        val lon = obj.getString("lon").toDoubleOrNull()
                        if (lat != null && lon != null) return@withContext Pair(lat, lon)
                    }
                }
            } catch (e: Exception) { Log.e("OSM_Geocoder", e.message ?: "") }
            null
        }
}