package com.example.airquality

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.airquality.data.AirQualityRepository
import com.example.airquality.data.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class NotifGroup { FIRE, REPORT, AQI, FORECAST, NEWS }

data class NotificationSection(
    val group: NotifGroup,
    val items: List<NewsRecord>
)

sealed class NotificationUiState {
    object Loading : NotificationUiState()
    data class Success(val sections: List<NotificationSection>) : NotificationUiState()
    data class Error(val message: String) : NotificationUiState()
}

class NotificationViewModel(
    private val airQuality: AirQualityRepository = AppContainer.airQuality
) : ViewModel() {
    private val _uiState = MutableStateFlow<NotificationUiState>(NotificationUiState.Loading)
    val uiState: StateFlow<NotificationUiState> = _uiState.asStateFlow()

    fun fetchNotifications(county: String?) {
        viewModelScope.launch {
            _uiState.value = NotificationUiState.Loading
            try {
                val regionParam = county?.ifBlank { null }

                val newsResponse       = airQuality.news(regionParam)
                val reportsResponse    = airQuality.userReports(regionParam)
                val aqiResponse        = airQuality.airQuality(regionParam)
                val fireAlertsResponse = try {
                    airQuality.fireAlerts(regionParam)
                } catch (e: Exception) { null }
                val forecastResponse   = try {
                    airQuality.forecast(regionParam)
                } catch (e: Exception) { null }

                // ── 火災警示 ──────────────────────────────────────────────────
                val fireAlerts = fireAlertsResponse?.records ?: emptyList()

                // ── 民眾回報 ──────────────────────────────────────────────────
                val userReports = reportsResponse.records ?: emptyList()

                // ── AQI 警報（AQI ≥ 151 才顯示）────────────────────────────────
                val aqiAlerts = mutableListOf<NewsRecord>()
                val maxAqi = (aqiResponse.records ?: emptyList())
                    .maxByOrNull { it.aqi.toIntOrNull() ?: 0 }
                val aqiValue = maxAqi?.aqi?.toIntOrNull() ?: 0
                if (aqiValue >= 151) {
                    aqiAlerts += NewsRecord(
                        source     = "空氣品質警報",
                        region     = regionParam ?: "全台",
                        title      = "AQI $aqiValue（${maxAqi?.status ?: ""}）",
                        summary    = "${maxAqi?.county ?: ""}${maxAqi?.sitename ?: ""}",
                        url        = "",
                        publishedAt = maxAqi?.publishtime ?: "",
                        timestamp  = ""
                    )
                }

                // ── 空品預報（只顯示與當前狀態不同的）────────────────────────────
                val currentStatus = maxAqi?.status ?: ""
                val forecasts = (forecastResponse?.records ?: emptyList()).filter { record ->
                    val forecastStatus = record.title.substringAfterLast("：").trim()
                    forecastStatus != currentStatus
                }

                // ── 新聞（舊到新排列）────────────────────────────────────────
                val news = (newsResponse.records ?: emptyList()).reversed()

                // 組 sections，只加有資料的
                val sections = buildList {
                    if (fireAlerts.isNotEmpty())  add(NotificationSection(NotifGroup.FIRE,     fireAlerts))
                    if (userReports.isNotEmpty()) add(NotificationSection(NotifGroup.REPORT,   userReports))
                    if (aqiAlerts.isNotEmpty())   add(NotificationSection(NotifGroup.AQI,      aqiAlerts))
                    if (forecasts.isNotEmpty())   add(NotificationSection(NotifGroup.FORECAST, forecasts))
                    if (news.isNotEmpty())        add(NotificationSection(NotifGroup.NEWS,     news))
                }

                _uiState.value = NotificationUiState.Success(sections)
            } catch (e: Exception) {
                _uiState.value = NotificationUiState.Error("通知資料取得失敗: ${e.localizedMessage}")
                Log.e("NotificationViewModel", "fetchNotifications failed", e)
            }
        }
    }
}
