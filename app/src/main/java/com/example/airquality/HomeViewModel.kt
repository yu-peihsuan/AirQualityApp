package com.example.airquality

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.airquality.data.AirQualityRepository
import com.example.airquality.data.AppContainer
import com.example.airquality.data.Coordinates
import com.example.airquality.data.FavoriteLocation
import com.example.airquality.data.FcmTokenRepository
import com.example.airquality.data.GeocodingRepository
import com.example.airquality.data.HealthProfileRepository
import com.example.airquality.data.LocationChoice
import com.example.airquality.data.LocationPreferenceRepository
import com.example.airquality.data.LocationRepository
import com.example.airquality.data.LocationResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AqiUiState {
    object Loading : AqiUiState()
    data class Success(
        val data: AirQualityResponse,
        val nearestRecord: AqiRecord,
        val displayRegion: String,
        val isFromCache: Boolean = false
    ) : AqiUiState()
    data class Error(val message: String) : AqiUiState()
}

sealed class RagAdviceUiState {
    object Idle : RagAdviceUiState()
    object Loading : RagAdviceUiState()
    data class Success(val response: RagAdviceResponse) : RagAdviceUiState()
    data class Error(val message: String) : RagAdviceUiState()
}

// 跨 ViewModel 共用的位置狀態：手動切換或 GPS 後統一更新
object AppLocationState {
    private val _selectedLatLng = MutableStateFlow<Pair<Double, Double>?>(null)
    val selectedLatLng: StateFlow<Pair<Double, Double>?> = _selectedLatLng.asStateFlow()

    fun update(lat: Double, lng: Double) { _selectedLatLng.value = Pair(lat, lng) }
    fun resetToGps() { _selectedLatLng.value = null }
}

/**
 * 首頁與 AI 顧問頁共用的 ViewModel。
 *
 * 所有 Context 相依（SharedPreferences、定位、網路）都收在 Repository 後面，
 * 這裡不再接收任何 Context 參數——ViewModel 的生命週期比 Activity 長，
 * 存下 Activity 的 Context 會讓整個畫面在旋轉或返回後仍無法被回收。
 */
class HomeViewModel(
    private val airQuality: AirQualityRepository = AppContainer.airQuality,
    private val geocoding: GeocodingRepository = AppContainer.geocoding,
    private val location: LocationRepository = AppContainer.location,
    private val healthProfile: HealthProfileRepository = AppContainer.healthProfile,
    private val fcmToken: FcmTokenRepository = AppContainer.fcmToken,
    private val locationPreference: LocationPreferenceRepository = AppContainer.locationPreference
) : ViewModel() {

    private val _uiState = MutableStateFlow<AqiUiState>(AqiUiState.Loading)
    val uiState: StateFlow<AqiUiState> = _uiState.asStateFlow()

    private val _ragAdviceState = MutableStateFlow<RagAdviceUiState>(RagAdviceUiState.Idle)
    val ragAdviceState: StateFlow<RagAdviceUiState> = _ragAdviceState.asStateFlow()

    private val _isRaining = MutableStateFlow(false)
    val isRaining: StateFlow<Boolean> = _isRaining.asStateFlow()

    private val _currentLocationName = MutableStateFlow(GPS_MODE_NAME)
    val currentLocationName: StateFlow<String> = _currentLocationName.asStateFlow()

    // 常用地點清單，供「切換地點」對話框顯示（畫面不再自己讀 SharedPreferences）
    private val _favorites = MutableStateFlow(locationPreference.favorites())
    val favorites: StateFlow<List<FavoriteLocation>> = _favorites.asStateFlow()

    // 健康狀況（首頁的行動建議依此挑選），畫面不再自己讀 SharedPreferences
    private val _healthConditions = MutableStateFlow(healthProfile.conditions)
    val healthConditions: StateFlow<List<String>> = _healthConditions.asStateFlow()

    // 一次性使用者提示（如地址搜尋失敗），由 HomeScreen 收集後以 Toast 顯示
    private val _userMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    // IDW 空間插值：使用者定位點的空品估計（含參與測站）。
    // 目前不在 UI 顯示（後端 /api/air_quality/estimate 為研究驗證用），
    // 若未來要顯示，於各定位成功處呼叫 refreshEstimate() 即可。
    private val _estimateInfo = MutableStateFlow<EstimateResponse?>(null)
    val estimateInfo: StateFlow<EstimateResponse?> = _estimateInfo.asStateFlow()

    // 記憶體快取：最後一次成功的 AQI 狀態，供 API 失敗時備援
    private var lastSuccessState: AqiUiState.Success? = null

    // 記住手動選的地點，供 ON_RESUME 刷新時使用
    private var savedLocationName: String = ""
    private var savedLocationAddress: String = ""

    // 目前定位點座標（供下風處判斷使用）
    private var userCoordinates: Coordinates? = null

    val isGpsMode: Boolean
        get() = _currentLocationName.value == GPS_MODE_NAME

    /** 是否已授權定位。畫面據此決定要不要跳出系統權限詢問。 */
    fun hasLocationPermission(): Boolean = location.hasPermission()

    /** 是否曾經手動選過一個地區。 */
    fun hasSavedChoice(): Boolean = locationPreference.hasSavedChoice()

    // ── 地點載入 ──────────────────────────────────────────────────────────

    /** 依上次持久化的選擇載入：手動地區則沿用該地區，否則走 GPS 自動定位。 */
    fun loadInitialLocation() {
        when (val choice = locationPreference.currentChoice()) {
            is LocationChoice.Saved -> switchToSavedLocation(choice.name, choice.address, persist = false)
            is LocationChoice.Gps   -> switchToGps(persist = false)
        }
    }

    /** 回到前景時重新整理目前選定的地點。 */
    fun refreshCurrentLocation() {
        if (isGpsMode) switchToGps(persist = false)
        else if (savedLocationAddress.isNotEmpty()) {
            switchToSavedLocation(savedLocationName, savedLocationAddress, persist = false)
        }
    }

    fun switchToGps(persist: Boolean = true) {
        _currentLocationName.value = GPS_MODE_NAME
        AppLocationState.resetToGps()
        if (persist) locationPreference.saveChoice(LocationChoice.Gps)
        viewModelScope.launch {
            when (val result = location.currentLocation(highAccuracy = true)) {
                is LocationResult.Available -> loadByCoordinates(result.coordinates)
                // 未授權或這次抓不到座標：仍要有資料可看，退回預設地區
                LocationResult.PermissionDenied,
                LocationResult.Unavailable -> loadDefaultRegion()
            }
        }
    }

    fun switchToSavedLocation(name: String, address: String, persist: Boolean = true) {
        savedLocationName = name
        savedLocationAddress = address
        _currentLocationName.value = name   // 同步設定，讓 isGpsMode 立即反映手動選擇
        if (persist) locationPreference.saveChoice(LocationChoice.Saved(name, address))
        viewModelScope.launch {
            _uiState.value = AqiUiState.Loading
            try {
                val coords = geocoding.forwardGeocode(address)
                if (coords == null) {
                    _userMessage.tryEmit("找不到「$name」的位置（地址搜尋暫時無法使用），暫以台北市資料顯示")
                }
                val resolved = coords ?: DEFAULT_COORDINATES
                val nearest = loadAqi(resolved, displayRegion = address)
                userCoordinates = resolved
                AppLocationState.update(resolved.lat, resolved.lng)
                // 手動切換地區也更新 FCM Token 的縣市與座標，
                // 讓推播（每日摘要、警報、附近回報）依「當下選擇的地區」發送，
                // 不需定位權限也能收到所選地區的通知
                fcmToken.uploadRegistration(nearest.county, resolved)
                refreshWeather(nearest.county, resolved)
            } catch (e: Exception) {
                fallbackToCache("switchToSavedLocation 失敗", e, "地點切換失敗")
            }
        }
    }

    /** 新增一個常用地點並立即切換過去。 */
    fun addFavoriteAndSwitch(name: String, address: String) {
        locationPreference.addFavorite(FavoriteLocation(name, address))
        _favorites.value = locationPreference.favorites()
        switchToSavedLocation(name, address)
    }

    /** 重新從本機載入常用地點清單（設定頁改過之後呼叫）。 */
    fun reloadFavorites() {
        _favorites.value = locationPreference.favorites()
    }

    /** 重新從本機載入健康狀況（設定頁改過之後呼叫）。 */
    fun reloadHealthProfile() {
        _healthConditions.value = healthProfile.conditions
    }

    // ── 內部載入流程 ──────────────────────────────────────────────────────

    /** GPS 取得座標後：反查縣市 → 抓 AQI → 上傳推播註冊 → 抓天氣。 */
    private suspend fun loadByCoordinates(coords: Coordinates) {
        _uiState.value = AqiUiState.Loading
        try {
            val region = geocoding.reverseGeocodeCounty(coords.lat, coords.lng) ?: "目前位置"
            val nearest = loadAqi(coords, displayRegion = region)
            userCoordinates = coords
            fcmToken.uploadRegistration(nearest.county, coords)
            refreshWeather(nearest.county, coords)
        } catch (e: Exception) {
            fallbackToCache("GPS 定位載入失敗", e, "AQI 取得失敗")
        }
    }

    /** 沒有定位也沒有選過地區時的備援：以台北市顯示。 */
    private suspend fun loadDefaultRegion() {
        _uiState.value = AqiUiState.Loading
        try {
            loadAqi(DEFAULT_COORDINATES, displayRegion = DEFAULT_REGION)
        } catch (e: Exception) {
            fallbackToCache("預設地區載入失敗", e, "AQI 取得失敗")
        }
    }

    /** 抓全台測站、挑最近一站並更新畫面狀態；回傳選中的測站。 */
    private suspend fun loadAqi(coords: Coordinates, displayRegion: String): AqiRecord {
        val response = airQuality.airQuality()
        val nearest = airQuality.nearestStation(response.records, coords.lat, coords.lng)
            ?: throw IllegalStateException("沒有取得 AQI 資料")
        val successState = AqiUiState.Success(
            data = response,
            nearestRecord = nearest,
            displayRegion = displayRegion,
            isFromCache = response.status == "cached"
        )
        lastSuccessState = successState
        _uiState.value = successState
        return nearest
    }

    /** 天氣只影響首頁圖示，抓不到就當作沒下雨，不影響主要流程。 */
    private suspend fun refreshWeather(county: String, coords: Coordinates) {
        _isRaining.value = try {
            airQuality.weather(county, coords.lat, coords.lng).isRaining
        } catch (e: Exception) {
            Log.w(TAG, "天氣取得失敗：${e.localizedMessage}")
            false
        }
    }

    private fun fallbackToCache(logMessage: String, cause: Exception, userFacing: String) {
        val cached = lastSuccessState
        if (cached != null) {
            Log.w(TAG, "$logMessage，改用快取資料：${cause.localizedMessage}")
            _uiState.value = cached.copy(isFromCache = true)
        } else {
            Log.e(TAG, logMessage, cause)
            _uiState.value = AqiUiState.Error("$userFacing: ${cause.localizedMessage}")
        }
    }

    // ── AI 建議 ───────────────────────────────────────────────────────────

    fun fetchRagAdvice() {
        viewModelScope.launch {
            _ragAdviceState.value = RagAdviceUiState.Loading
            try {
                // 健康屬性僅在使用者主動使用 AI 建議時送出（見隱私權政策第二節）
                val userProfile = healthProfile.toRagUserProfile()

                // 所在縣市與 AQI 直接取自目前的成功狀態，避免重複呼叫 API
                val success = _uiState.value as? AqiUiState.Success
                val request = RagAdviceRequest(
                    county = success?.nearestRecord?.county?.ifBlank { DEFAULT_REGION } ?: DEFAULT_REGION,
                    latitude = userCoordinates?.lat,
                    longitude = userCoordinates?.lng,
                    aqi = success?.nearestRecord?.aqi?.toIntOrNull(),
                    pm25 = success?.nearestRecord?.pm25?.toDoubleOrNull(),
                    userProfile = userProfile
                )
                _ragAdviceState.value = RagAdviceUiState.Success(airQuality.ragAdvice(request))
            } catch (e: Exception) {
                Log.e(TAG, "fetchRagAdvice failed", e)
                _ragAdviceState.value = RagAdviceUiState.Error("AI 建議取得失敗: ${e.localizedMessage}")
            }
        }
    }

    @Suppress("unused")
    private fun refreshEstimate(coords: Coordinates) {
        viewModelScope.launch {
            _estimateInfo.value = try {
                airQuality.aqiEstimate(coords.lat, coords.lng)
            } catch (e: Exception) {
                Log.w(TAG, "插值估計取得失敗: ${e.localizedMessage}")
                null
            }
        }
    }

    // ── 顯示用工具 ────────────────────────────────────────────────────────

    /** 中文 8 方位風向。 */
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

    private companion object {
        const val TAG = "HomeViewModel"
        const val GPS_MODE_NAME = "GPS 定位"
        const val DEFAULT_REGION = "台北市"
        // 預設位置：台北市中心（北緯 25°05'14"、東經 121°33'20"）
        val DEFAULT_COORDINATES = Coordinates(25.087222, 121.555556)
    }
}
