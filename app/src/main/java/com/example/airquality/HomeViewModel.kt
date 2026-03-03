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
                            displayRegion = defaultAddress.take(6)
                        }
                    } catch (e: Exception) {
                        Log.e("Geocoder", "轉換地址失敗", e)
                        displayRegion = defaultAddress.take(6)
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
}