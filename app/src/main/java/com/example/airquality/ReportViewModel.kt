package com.example.airquality

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

sealed class ReportUiState {
    object Idle : ReportUiState()
    object Loading : ReportUiState()
    data class Success(val message: String, val isConfirmed: Boolean) : ReportUiState()
    data class Error(val message: String) : ReportUiState()
}

sealed class LocationFetchState {
    object Idle : LocationFetchState()
    object Loading : LocationFetchState()
    data class Success(val address: String) : LocationFetchState()
    data class Error(val message: String) : LocationFetchState()
}

class ReportViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<ReportUiState>(ReportUiState.Idle)
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    private val _locationFetchState = MutableStateFlow<LocationFetchState>(LocationFetchState.Idle)
    val locationFetchState: StateFlow<LocationFetchState> = _locationFetchState.asStateFlow()

    /** 使用者點「定位」按鈕 → 取得 GPS 並 Reverse Geocoding 轉為地址。 */
    fun fetchAddressFromGps(context: Context) {
        viewModelScope.launch {
            _locationFetchState.value = LocationFetchState.Loading
            val coords = getCurrentLocation(context)
            if (coords == null) {
                _locationFetchState.value = LocationFetchState.Error("無法取得定位，請確認已開啟定位權限")
                return@launch
            }
            val address = reverseGeocode(context, coords.first, coords.second)
            if (address != null) {
                _locationFetchState.value = LocationFetchState.Success(address)
            } else {
                _locationFetchState.value = LocationFetchState.Error("無法解析地址，請手動輸入")
            }
        }
    }

    fun resetLocationFetchState() {
        _locationFetchState.value = LocationFetchState.Idle
    }

    fun submitReport(context: Context, location: String, category: String, description: String) {
        if (location.isBlank() || category.isBlank() || description.isBlank()) {
            _uiState.value = ReportUiState.Error("請填寫所有欄位")
            return
        }
        viewModelScope.launch {
            _uiState.value = ReportUiState.Loading
            try {
                val coords = getCurrentLocation(context)
                val response = RetrofitClient.apiService.submitReport(
                    ReportRequest(
                        location = location,
                        category = category,
                        description = description,
                        latitude = coords?.first,
                        longitude = coords?.second
                    )
                )
                val msg = if (response.isConfirmed) "已確認為污染事件，感謝您的通報！" else "回報已送出，感謝您的通報。"
                _uiState.value = ReportUiState.Success(msg, response.isConfirmed)
            } catch (e: Exception) {
                _uiState.value = ReportUiState.Error("送出失敗：${e.localizedMessage}")
            }
        }
    }

    /** 取得當前 GPS 座標，回傳 (lat, lng) 或 null。 */
    @SuppressLint("MissingPermission")
    private suspend fun getCurrentLocation(context: Context): Pair<Double, Double>? =
        suspendCancellableCoroutine { cont ->
            val client = LocationServices.getFusedLocationProviderClient(context)
            val cts = CancellationTokenSource()
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    cont.resume(if (loc != null) Pair(loc.latitude, loc.longitude) else null)
                }
                .addOnFailureListener {
                    cont.resume(null)
                }
            cont.invokeOnCancellation { cts.cancel() }
        }

    /** 將座標轉為可讀地址（Reverse Geocoding）。 */
    private fun reverseGeocode(context: Context, lat: Double, lng: Double): String? {
        return try {
            val geocoder = Geocoder(context, Locale.TRADITIONAL_CHINESE)
            @Suppress("DEPRECATION")
            val results = geocoder.getFromLocation(lat, lng, 1)
            if (!results.isNullOrEmpty()) {
                val addr = results[0]
                // 組合：縣市 + 鄉鎮區 + 路段
                listOfNotNull(
                    addr.adminArea,
                    addr.subAdminArea,
                    addr.locality,
                    addr.thoroughfare,
                    addr.subThoroughfare
                ).joinToString("").ifBlank { addr.getAddressLine(0) }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun resetState() {
        _uiState.value = ReportUiState.Idle
    }
}
