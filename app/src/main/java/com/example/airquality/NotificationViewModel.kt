package com.example.airquality

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class NotificationUiState {
    object Loading : NotificationUiState()
    data class Success(val notifications: List<NewsRecord>) : NotificationUiState()
    data class Error(val message: String) : NotificationUiState()
}

class NotificationViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<NotificationUiState>(NotificationUiState.Loading)
    val uiState: StateFlow<NotificationUiState> = _uiState.asStateFlow()

    fun fetchNotifications(context: Context?) {
        viewModelScope.launch {
            _uiState.value = NotificationUiState.Loading
            try {
                // 取得使用者地址
                var address = ""
                if (context != null) {
                    val sharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                    address = sharedPreferences.getString("default_address", "") ?: ""
                }

                val regionParam = if (address.isNotBlank()) address else null

                // 同時取得新聞與 AQI 資料
                val newsResponse = RetrofitClient.apiService.getNews(regionParam)
                val aqiResponse = RetrofitClient.apiService.getAirQuality(regionParam)

                val newsRecords = (newsResponse.records ?: emptyList()).reversed().toMutableList()

                // 若 AQI >= 151 (紅色警戒)，在最上方插入警報卡片
                val aqiRecords = aqiResponse.records ?: emptyList()
                if (aqiRecords.isNotEmpty()) {
                    val maxAqiRecord = aqiRecords.maxByOrNull { it.aqi.toIntOrNull() ?: 0 }
                    val aqiValue = maxAqiRecord?.aqi?.toIntOrNull() ?: 0
                    if (aqiValue >= 151) {
                        val alertRecord = NewsRecord(
                            source = "空氣品質警報",
                            region = regionParam ?: "全台",
                            title = "空氣品質紅色警戒：${maxAqiRecord?.county ?: ""}${maxAqiRecord?.sitename ?: ""} AQI $aqiValue（${maxAqiRecord?.status ?: ""}）",
                            summary = "",
                            url = "",
                            publishedAt = maxAqiRecord?.publishtime ?: "",
                            timestamp = ""
                        )
                        newsRecords.add(0, alertRecord)
                    }
                }

                _uiState.value = NotificationUiState.Success(newsRecords)
            } catch (e: Exception) {
                _uiState.value = NotificationUiState.Error("通知資料取得失敗: ${e.localizedMessage}")
                Log.e("NotificationViewModel", "fetchNotifications failed", e)
            }
        }
    }

}
