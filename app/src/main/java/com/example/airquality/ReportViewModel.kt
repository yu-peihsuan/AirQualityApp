package com.example.airquality

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
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

    // 快取 GPS 定位結果，用於判斷使用者是否修改過地址
    private var cachedGpsCoords: Pair<Double, Double>? = null
    private var cachedGpsAddress: String? = null

    /** 使用者點「定位」按鈕 → 取得 GPS 並 Reverse Geocoding 轉為地址。 */
    fun fetchAddressFromGps(context: Context) {
        viewModelScope.launch {
            _locationFetchState.value = LocationFetchState.Loading
            val coords = getCurrentLocation(context)
            if (coords == null) {
                _locationFetchState.value = LocationFetchState.Error("無法取得定位，請確認已開啟定位權限")
                return@launch
            }
            val address = reverseGeocode(coords.first, coords.second)
            if (address != null) {
                cachedGpsCoords = coords
                cachedGpsAddress = address
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
                // 地址與 GPS 快取相符 → 直接用 GPS 座標；否則正向地理編碼取得座標
                val coords = if (location.trim() == cachedGpsAddress?.trim()) {
                    cachedGpsCoords
                } else {
                    forwardGeocode(location)
                }
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

    /** Google Maps Geocoding API：正向地理編碼（地址文字 → 座標）。 */
    private suspend fun forwardGeocode(address: String): Pair<Double, Double>? =
        withContext(Dispatchers.IO) {
            try {
                val encoded = java.net.URLEncoder.encode(address, "UTF-8")
                val url = URL("https://maps.googleapis.com/maps/api/geocode/json?address=$encoded&key=${BuildConfig.MAPS_API_KEY}&language=zh-TW")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                if (conn.responseCode == 200) {
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    if (json.getString("status") == "OK") {
                        val loc = json.getJSONArray("results")
                            .getJSONObject(0)
                            .getJSONObject("geometry")
                            .getJSONObject("location")
                        return@withContext Pair(loc.getDouble("lat"), loc.getDouble("lng"))
                    }
                }
            } catch (e: Exception) { Log.e("GoogleGeocoder", e.message ?: "") }
            null
        }

    /** Google Maps Geocoding API：反向地理編碼（座標 → 可讀地址）。 */
    private suspend fun reverseGeocode(lat: Double, lng: Double): String? =
        withContext(Dispatchers.IO) {
            try {
                val url = URL("https://maps.googleapis.com/maps/api/geocode/json?latlng=$lat,$lng&key=${BuildConfig.MAPS_API_KEY}&language=zh-TW")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                if (conn.responseCode == 200) {
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    if (json.getString("status") == "OK") {
                        json.getJSONArray("results")
                            .getJSONObject(0)
                            .getString("formatted_address")
                    } else null
                } else null
            } catch (e: Exception) {
                Log.e("GoogleGeocoder", e.message ?: "")
                null
            }
        }

    fun resetState() {
        _uiState.value = ReportUiState.Idle
    }
}
