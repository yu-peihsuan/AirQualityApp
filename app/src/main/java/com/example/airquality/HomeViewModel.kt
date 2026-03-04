package com.example.airquality

import android.content.Context
import android.location.Geocoder
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.*

// 定義畫面可能的三種狀態
sealed class AqiUiState {
    object Loading : AqiUiState()
    data class Success(val data: AirQualityResponse, val nearestRecord: AqiRecord, val displayRegion: String) : AqiUiState()
    data class Error(val message: String) : AqiUiState()
}

class HomeViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<AqiUiState>(AqiUiState.Loading)
    val uiState: StateFlow<AqiUiState> = _uiState.asStateFlow()

    fun fetchAirQuality(context: Context?, defaultAddress: String) {
        viewModelScope.launch {
            _uiState.value = AqiUiState.Loading
            try {
                // 不傳入 county，抓取全台資料
                val response = RetrofitClient.apiService.getAirQuality(null)
                val records = response.records ?: emptyList()

                if (records.isEmpty()) {
                    throw Exception("API 未回傳任何測站資料")
                }

                var targetLat = 25.032969 // 預設經緯度 (臺北凱達格蘭大道)
                var targetLng = 121.516039
                var displayRegion = "臺北市中正區"

                // 使用 Geocoder 將使用者自訂地址轉為經緯度
                if (context != null && defaultAddress.isNotBlank()) {
                    try {
                        val addresses = withContext(Dispatchers.IO) {
                            val geocoder = Geocoder(context)
                            @Suppress("DEPRECATION")
                            geocoder.getFromLocationName(defaultAddress, 1)
                        }
                        if (!addresses.isNullOrEmpty()) {
                            val addr = addresses[0]
                            targetLat = addr.latitude
                            targetLng = addr.longitude
                            
                            val adminStr = addr.adminArea ?: ""
                            val subAdminStr = addr.subAdminArea ?: addr.locality ?: ""
                            
                        displayRegion = buildString {
                                if (adminStr.isNotBlank()) append(adminStr)
                                if (subAdminStr.isNotBlank() && subAdminStr != adminStr) append(subAdminStr)
                            }.takeIf { it.isNotBlank() } ?: defaultAddress.take(6)
                        } else {
                            val fallback = getCoordinatesFromOSM(defaultAddress)
                            if (fallback != null) {
                                targetLat = fallback.first
                                targetLng = fallback.second
                                displayRegion = defaultAddress.take(6)
                            } else {
                                displayRegion = defaultAddress.take(6)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("Geocoder", "轉換地址失敗", e)
                        val fallback = getCoordinatesFromOSM(defaultAddress)
                        if (fallback != null) {
                            targetLat = fallback.first
                            targetLng = fallback.second
                            displayRegion = defaultAddress.take(6)
                        } else {
                            displayRegion = defaultAddress.take(6)
                        }
                    }
                }

                // 計算所有測站距離，找出最近的一個測站
                val nearestRecord = records.minByOrNull {
                    val rLat = it.latitude.toDoubleOrNull() ?: 0.0
                    val rLng = it.longitude.toDoubleOrNull() ?: 0.0
                    haversine(targetLat, targetLng, rLat, rLng)
                } ?: records.first()

                _uiState.value = AqiUiState.Success(response, nearestRecord, displayRegion)
            } catch (e: Exception) {
                _uiState.value = AqiUiState.Error("連線失敗: ${e.localizedMessage}")
            }
        }
    }

    private fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0 // 地球半徑公里
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    // 備用方案: 透過 OpenStreetMap 的 Nominatim API 將地址轉為經緯度
    private suspend fun getCoordinatesFromOSM(address: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        try {
            val encodedAddress = java.net.URLEncoder.encode(address, "UTF-8")
            val urlString = "https://nominatim.openstreetmap.org/search?q=\$encodedAddress&format=json&limit=1"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "AirQualityApp/1.0 (Android fallback)")
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseString = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(responseString)
                if (jsonArray.length() > 0) {
                    val locationObj = jsonArray.getJSONObject(0)
                    val lat = locationObj.getString("lat").toDoubleOrNull()
                    val lon = locationObj.getString("lon").toDoubleOrNull()
                    if (lat != null && lon != null) {
                        return@withContext Pair(lat, lon)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("OSM_Geocoder", "備用 API 例外錯誤: \${e.message}")
        }
        return@withContext null
    }
}