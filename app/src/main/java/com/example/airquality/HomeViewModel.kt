package com.example.airquality

import android.content.Context
import android.location.Location
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

class HomeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<AqiUiState>(AqiUiState.Loading)
    val uiState: StateFlow<AqiUiState> = _uiState.asStateFlow()

    private val _ragAdviceState = MutableStateFlow<RagAdviceUiState>(RagAdviceUiState.Idle)
    val ragAdviceState: StateFlow<RagAdviceUiState> = _ragAdviceState.asStateFlow()

    private val _isRaining = MutableStateFlow(false)
    val isRaining: StateFlow<Boolean> = _isRaining.asStateFlow()

    private val _currentLocationName = MutableStateFlow("GPS 定位")
    val currentLocationName: StateFlow<String> = _currentLocationName.asStateFlow()

    // 一次性使用者提示（如地址搜尋失敗），由 HomeScreen 收集後以 Toast 顯示
    private val _userMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    // IDW 空間插值：使用者定位點的空品估計（含參與測站）。
    // 目前不在 UI 顯示（後端 /api/air_quality/estimate 為研究驗證用），
    // 若未來要顯示，於各定位成功處呼叫 refreshEstimate() 即可。
    private val _estimateInfo = MutableStateFlow<EstimateResponse?>(null)
    val estimateInfo: StateFlow<EstimateResponse?> = _estimateInfo.asStateFlow()

    @Suppress("unused")
    private fun refreshEstimate(lat: Double, lng: Double) {
        viewModelScope.launch {
            _estimateInfo.value = try {
                RetrofitClient.apiService.getAqiEstimate(lat, lng)
            } catch (e: Exception) {
                Log.w("HomeViewModel", "插值估計取得失敗: ${e.localizedMessage}")
                null
            }
        }
    }

    // 記憶體快取：最後一次成功的 AQI 狀態，供 API 失敗時備援
    private var _lastSuccessState: AqiUiState.Success? = null

    // 記住手動選的地點，供 ON_RESUME 刷新時使用
    private var _savedLocationName: String = ""
    private var _savedLocationAddress: String = ""

    val isGpsMode: Boolean
        get() = _currentLocationName.value == "GPS 定位"

    fun refreshSavedLocation() {
        if (_savedLocationAddress.isNotEmpty()) {
            switchToSavedLocation(_savedLocationName, _savedLocationAddress)
        }
    }

    fun switchToSavedLocation(name: String, address: String) {
        _savedLocationName = name
        _savedLocationAddress = address
        viewModelScope.launch {
            _uiState.value = AqiUiState.Loading
            _currentLocationName.value = name
            try {
                val coords = googleForwardGeocode(address)
                if (coords == null) {
                    _userMessage.tryEmit("找不到「$name」的位置（地址搜尋暫時無法使用），暫以台北市資料顯示")
                }
                val lat = coords?.first ?: 25.032969
                val lng = coords?.second ?: 121.516039
                val aqiResponse = RetrofitClient.apiService.getAirQuality(null)
                val aqiRecords  = aqiResponse.records ?: emptyList()
                if (aqiRecords.isEmpty()) throw Exception("沒有取得 AQI 資料")
                val nearestAqi = findNearestStation(aqiRecords, lat, lng)
                val isCache = aqiResponse.status == "cached"
                val successState = AqiUiState.Success(aqiResponse, nearestAqi, address, isCache)
                _lastSuccessState = successState
                _uiState.value = successState
                _userLat = lat
                _userLng = lng
                AppLocationState.update(lat, lng)
                try {
                    val weather = RetrofitClient.apiService.getWeather(
                        county = nearestAqi.county,
                        lat    = lat,
                        lng    = lng
                    )
                    _isRaining.value = weather.isRaining
                } catch (e: Exception) { _isRaining.value = false }
            } catch (e: Exception) {
                val cached = _lastSuccessState
                if (cached != null) {
                    Log.w("HomeViewModel", "switchToSavedLocation 失敗，使用快取: ${e.localizedMessage}")
                    _uiState.value = cached.copy(isFromCache = true)
                } else {
                    _uiState.value = AqiUiState.Error("地點切換失敗: ${e.localizedMessage}")
                }
            }
        }
    }

    fun switchToGps(context: android.content.Context) {
        _currentLocationName.value = "GPS 定位"
        AppLocationState.resetToGps()
        fetchWithGps(context, this)
    }

    // GPS 座標（由 fetchAirQualityByLocation 設定，供下風處判斷使用）
    private var _userLat: Double? = null
    private var _userLng: Double? = null

    fun fetchAirQuality(context: Context?, address: String) {
        viewModelScope.launch {
            _uiState.value = AqiUiState.Loading
            try {
                val aqiResponse = RetrofitClient.apiService.getAirQuality(null)
                val aqiRecords = aqiResponse.records ?: emptyList()
                if (aqiRecords.isEmpty()) throw Exception("沒有取得 AQI 資料")

                val (lat, lng, displayRegion) = getCoordinates(context, address)
                val nearestAqi = findNearestStation(aqiRecords, lat, lng)
                val isCache = aqiResponse.status == "cached"
                val successState = AqiUiState.Success(aqiResponse, nearestAqi, displayRegion, isCache)
                _lastSuccessState = successState
                _uiState.value = successState

            } catch (e: Exception) {
                Log.e("HomeViewModel", "fetchAirQuality failed", e)
                val cached = _lastSuccessState
                if (cached != null) {
                    Log.w("HomeViewModel", "使用 AQI 快取資料")
                    _uiState.value = cached.copy(isFromCache = true)
                } else {
                    _uiState.value = AqiUiState.Error("AQI 取得失敗: ${e.localizedMessage}")
                }
            }
        }
    }

    fun fetchAirQualityByLocation(context: Context, location: Location, fallbackAddress: String) {
        viewModelScope.launch {
            _uiState.value = AqiUiState.Loading
            try {
                // 使用 Google Maps Geocoding API 反向地理編碼，取得縣市名
                val region = googleReverseGeocodeCounty(location.latitude, location.longitude)
                    ?: fallbackAddress.take(6).ifBlank { "目前位置" }

                val aqiResponse = RetrofitClient.apiService.getAirQuality(null)
                val aqiRecords = aqiResponse.records ?: emptyList()
                if (aqiRecords.isEmpty()) throw Exception("沒有取得 AQI 資料")

                val nearestAqi = findNearestStation(aqiRecords, location.latitude, location.longitude)
                val isCache = aqiResponse.status == "cached"
                val successState = AqiUiState.Success(aqiResponse, nearestAqi, region, isCache)
                _lastSuccessState = successState
                _uiState.value = successState
                _userLat = location.latitude
                _userLng = location.longitude
                // GPS 縣市確認後，上傳 FCM Token（含座標與健康狀況）給後端
                TokenManager.uploadTokenWithCounty(context, nearestAqi.county, location.latitude, location.longitude)
                // 抓即時天氣（判斷是否下雨，供首頁圖示使用）
                try {
                    val weather = RetrofitClient.apiService.getWeather(
                        county = nearestAqi.county,
                        lat    = location.latitude,
                        lng    = location.longitude
                    )
                    _isRaining.value = weather.isRaining
                } catch (e: Exception) {
                    _isRaining.value = false
                }

            } catch (e: Exception) {
                Log.e("HomeViewModel", "fetchAirQualityByLocation failed", e)
                val cached = _lastSuccessState
                if (cached != null) {
                    Log.w("HomeViewModel", "使用 AQI 快取資料（GPS 定位失敗）")
                    _uiState.value = cached.copy(isFromCache = true)
                } else {
                    _uiState.value = AqiUiState.Error("AQI 取得失敗: ${e.localizedMessage}")
                }
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

                val otherNotes = healthPrefs.getString("health_other", "")?.ifBlank { null }

                val userProfile = RagUserProfile(
                    ageGroup          = ageGroup,
                    isPregnant        = conditions.contains("懷孕中"),
                    hasAsthma         = conditions.contains("氣喘") || conditions.contains("呼吸道疾病"),
                    hasCardiovascular = conditions.contains("心血管疾病") || conditions.contains("高血壓"),
                    hasAllergy        = conditions.contains("過敏"),
                    otherNotes        = otherNotes
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
            val rLat = if (it is AqiRecord) it.latitude.toDoubleOrNull() ?: 0.0 else 0.0
            val rLng = if (it is AqiRecord) it.longitude.toDoubleOrNull() ?: 0.0 else 0.0
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
            } else if (address != "台北市") {
                // 預設 fallback 地址本來就是台北市，查不到才需要提示使用者
                _userMessage.tryEmit("找不到「$address」的位置（地址搜尋暫時無法使用），暫以台北市資料顯示")
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