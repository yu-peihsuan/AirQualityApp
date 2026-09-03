package com.example.airquality

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.airquality.data.AppContainer
import com.example.airquality.data.FavoriteLocation
import com.example.airquality.data.FcmTokenRepository
import com.example.airquality.data.HealthProfileRepository
import com.example.airquality.data.LocationPreferenceRepository
import com.example.airquality.data.NotificationSettingsRepository
import com.example.airquality.data.SyncResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 設定頁的狀態與寫入邏輯。
 *
 * 原本這些都寫在 Composable 裡直接讀寫 SharedPreferences 與呼叫 Retrofit，
 * 失敗時沒有任何回饋（例如推播識別碼還沒拿到就 return）。改成 ViewModel 後，
 * 每個會打後端的設定都會回報結果，失敗就把開關切回原狀並告訴使用者。
 */
class SettingsViewModel(
    private val notificationSettings: NotificationSettingsRepository = AppContainer.notificationSettings,
    private val healthProfile: HealthProfileRepository = AppContainer.healthProfile,
    private val locationPreference: LocationPreferenceRepository = AppContainer.locationPreference,
    private val fcmToken: FcmTokenRepository = AppContainer.fcmToken
) : ViewModel() {

    private val _dailyEnabled = MutableStateFlow(notificationSettings.dailyEnabled)
    val dailyEnabled: StateFlow<Boolean> = _dailyEnabled.asStateFlow()

    private val _dailyHour = MutableStateFlow(notificationSettings.dailyHour)
    val dailyHour: StateFlow<Int> = _dailyHour.asStateFlow()

    private val _dailyMinute = MutableStateFlow(notificationSettings.dailyMinute)
    val dailyMinute: StateFlow<Int> = _dailyMinute.asStateFlow()

    private val _sensitiveAlertsEnabled = MutableStateFlow(notificationSettings.sensitiveAlertsEnabled)
    val sensitiveAlertsEnabled: StateFlow<Boolean> = _sensitiveAlertsEnabled.asStateFlow()

    private val _ageGroup = MutableStateFlow(healthProfile.ageGroup)
    val ageGroup: StateFlow<String> = _ageGroup.asStateFlow()

    private val _conditions = MutableStateFlow(healthProfile.conditions)
    val conditions: StateFlow<List<String>> = _conditions.asStateFlow()

    private val _otherNotes = MutableStateFlow(healthProfile.otherNotes)
    val otherNotes: StateFlow<String> = _otherNotes.asStateFlow()

    private val _favorites = MutableStateFlow(locationPreference.favorites())
    val favorites: StateFlow<List<FavoriteLocation>> = _favorites.asStateFlow()

    /** 要顯示給使用者的一次性訊息（Snackbar）。 */
    private val _message = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val message: SharedFlow<String> = _message.asSharedFlow()

    // ── 每日通知 ──────────────────────────────────────────────────────────

    fun setDailyEnabled(enabled: Boolean) {
        val previous = _dailyEnabled.value
        _dailyEnabled.value = enabled
        notificationSettings.dailyEnabled = enabled
        syncDailyNotification(enabled, _dailyHour.value, _dailyMinute.value) {
            // 後端沒收下就切回原狀，避免畫面顯示「已開啟」但其實不會收到通知
            _dailyEnabled.value = previous
            notificationSettings.dailyEnabled = previous
        }
    }

    fun setDailyTime(hour: Int, minute: Int) {
        val previousHour = _dailyHour.value
        val previousMinute = _dailyMinute.value
        _dailyHour.value = hour
        _dailyMinute.value = minute
        notificationSettings.dailyHour = hour
        notificationSettings.dailyMinute = minute
        // 設定時間本身就代表要啟用每日通知
        _dailyEnabled.value = true
        notificationSettings.dailyEnabled = true
        syncDailyNotification(true, hour, minute) {
            _dailyHour.value = previousHour
            _dailyMinute.value = previousMinute
            notificationSettings.dailyHour = previousHour
            notificationSettings.dailyMinute = previousMinute
        }
    }

    private fun syncDailyNotification(enabled: Boolean, hour: Int, minute: Int, onFailure: () -> Unit) {
        viewModelScope.launch {
            when (val result = fcmToken.setDailyNotification(enabled, hour, minute)) {
                is SyncResult.Success -> Unit
                is SyncResult.Failure -> {
                    onFailure()
                    _message.tryEmit("通知設定未能同步：${result.message}")
                }
            }
        }
    }

    fun sendTestNotification() {
        viewModelScope.launch {
            fcmToken.sendTestNotification()
                .onSuccess { _message.tryEmit(it) }
                .onFailure { _message.tryEmit("⚠️ 發送失敗：${it.message}") }
        }
    }

    // ── 敏感族群警示推播（健康屬性的明確同意）────────────────────────────

    /**
     * 開啟後才會把健康屬性送到伺服器作為推播分眾依據；關閉時送出空值，
     * 讓伺服器清掉先前留下的健康屬性。詳見隱私權政策第二節。
     */
    fun setSensitiveAlertsEnabled(enabled: Boolean) {
        val previous = _sensitiveAlertsEnabled.value
        _sensitiveAlertsEnabled.value = enabled
        notificationSettings.sensitiveAlertsEnabled = enabled
        viewModelScope.launch {
            when (val result = fcmToken.uploadRegistration()) {
                is SyncResult.Success ->
                    _message.tryEmit(
                        if (enabled) "已開啟敏感族群警示，健康狀況將用於推播分眾"
                        else "已關閉，伺服器上的健康狀況已一併清除"
                    )
                is SyncResult.Failure -> {
                    _sensitiveAlertsEnabled.value = previous
                    notificationSettings.sensitiveAlertsEnabled = previous
                    _message.tryEmit("設定未能同步：${result.message}")
                }
            }
        }
    }

    // ── 健康檔案 ──────────────────────────────────────────────────────────

    fun saveHealthProfile(ageGroup: String, conditions: List<String>, otherNotes: String) {
        healthProfile.save(ageGroup, conditions, otherNotes)
        _ageGroup.value = ageGroup
        _conditions.value = conditions
        _otherNotes.value = otherNotes
        // 已同意分眾推播的使用者，改完健康檔案要讓伺服器那份跟著更新；
        // 同步失敗要講出來，否則使用者會以為警示分眾已經跟著改了
        if (!_sensitiveAlertsEnabled.value) {
            _message.tryEmit("✅ 健康檔案已儲存於本機")
            return
        }
        viewModelScope.launch {
            when (val result = fcmToken.uploadRegistration()) {
                is SyncResult.Success -> _message.tryEmit("✅ 健康檔案已儲存，警示分眾已同步更新")
                is SyncResult.Failure ->
                    _message.tryEmit("✅ 健康檔案已存於本機，但警示分眾未能同步：${result.message}")
            }
        }
    }

    // ── 常用地點 ──────────────────────────────────────────────────────────

    fun saveFavorites(favorites: List<FavoriteLocation>) {
        locationPreference.saveFavorites(favorites)
        _favorites.value = locationPreference.favorites()
    }
}
