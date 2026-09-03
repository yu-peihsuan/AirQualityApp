package com.example.airquality

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.airquality.data.AirQualityRepository
import com.example.airquality.data.AppContainer
import com.example.airquality.data.Coordinates
import com.example.airquality.data.GeocodingRepository
import com.example.airquality.data.LocationRepository
import com.example.airquality.data.LocationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    /** 尚未授權定位，畫面應該去要權限而不是顯示錯誤。 */
    object PermissionRequired : LocationFetchState()
}

class ReportViewModel(
    private val airQuality: AirQualityRepository = AppContainer.airQuality,
    private val geocoding: GeocodingRepository = AppContainer.geocoding,
    private val location: LocationRepository = AppContainer.location
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReportUiState>(ReportUiState.Idle)
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    private val _locationFetchState = MutableStateFlow<LocationFetchState>(LocationFetchState.Idle)
    val locationFetchState: StateFlow<LocationFetchState> = _locationFetchState.asStateFlow()

    // 快取 GPS 定位結果，用於判斷使用者是否修改過地址
    private var cachedGpsCoords: Coordinates? = null
    private var cachedGpsAddress: String? = null

    /** 手動切換地點後，用已知座標反向地理編碼填入地址欄。 */
    fun fetchAddressFromCoords(lat: Double, lng: Double) {
        viewModelScope.launch {
            _locationFetchState.value = LocationFetchState.Loading
            val address = geocoding.reverseGeocodeAddress(lat, lng)
            _locationFetchState.value =
                if (address != null) LocationFetchState.Success(address)
                else LocationFetchState.Error("無法解析地址，請手動輸入")
        }
    }

    /** 使用者點「定位」按鈕 → 取得 GPS 並 Reverse Geocoding 轉為地址。 */
    fun fetchAddressFromGps() {
        viewModelScope.launch {
            _locationFetchState.value = LocationFetchState.Loading
            val coords = when (val result = location.currentLocation()) {
                is LocationResult.Available -> result.coordinates
                LocationResult.PermissionDenied -> {
                    _locationFetchState.value = LocationFetchState.PermissionRequired
                    return@launch
                }
                LocationResult.Unavailable -> {
                    _locationFetchState.value =
                        LocationFetchState.Error("目前無法取得定位，請稍後再試或手動輸入地址")
                    return@launch
                }
            }
            val address = geocoding.reverseGeocodeAddress(coords.lat, coords.lng)
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

    fun submitReport(location: String, category: String, description: String) {
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
                    geocoding.forwardGeocode(location)
                }
                // 裝置身分改由 access token 提供（見 AuthManager），
                // 這裡不再讀取或送出 ANDROID_ID。
                val response = airQuality.submitReport(
                    ReportRequest(
                        location = location,
                        category = category,
                        description = description,
                        latitude = coords?.lat,
                        longitude = coords?.lng
                    )
                )
                if (response.status != "success") {
                    // 頻率限制或重複回報：顯示後端回傳的原因
                    _uiState.value = ReportUiState.Error(response.message)
                    return@launch
                }
                val msg = if (response.isConfirmed) "已確認為污染事件，感謝您的通報！" else "回報已送出，感謝您的通報。"
                _uiState.value = ReportUiState.Success(msg, response.isConfirmed)
            } catch (e: Exception) {
                _uiState.value = ReportUiState.Error("送出失敗：${e.localizedMessage}")
            }
        }
    }

    fun resetState() {
        _uiState.value = ReportUiState.Idle
    }
}
