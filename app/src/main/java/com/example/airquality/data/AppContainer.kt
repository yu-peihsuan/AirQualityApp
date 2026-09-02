package com.example.airquality.data

import android.content.Context

/**
 * App 層級的相依性容器。
 *
 * 這個專案沒有導入 Hilt／Koin，但 ViewModel 仍然不該自己去拿 Context 或直接
 * 呼叫 Retrofit。折衷做法是：由 [com.example.airquality.AirQualityApplication]
 * 在啟動時建好所有 Repository（唯一持有 applicationContext 的地方），
 * ViewModel 以建構子預設值取用，測試時再傳入假的實作覆蓋。
 *
 * 只保存 applicationContext，不會持有 Activity／Composable 的 Context，
 * 因此不會造成畫面被回收後仍被參照的記憶體洩漏。
 */
object AppContainer {

    lateinit var airQuality: AirQualityRepository
        private set
    lateinit var geocoding: GeocodingRepository
        private set
    lateinit var location: LocationRepository
        private set
    lateinit var healthProfile: HealthProfileRepository
        private set
    lateinit var notificationSettings: NotificationSettingsRepository
        private set
    lateinit var fcmToken: FcmTokenRepository
        private set
    lateinit var locationPreference: LocationPreferenceRepository
        private set

    fun init(context: Context) {
        val app = context.applicationContext
        airQuality           = AirQualityRepository()
        geocoding            = GeocodingRepository()
        location             = LocationRepository(app)
        healthProfile        = HealthProfileRepository(app)
        notificationSettings = NotificationSettingsRepository(app)
        fcmToken             = FcmTokenRepository(app, healthProfile, notificationSettings)
        locationPreference   = LocationPreferenceRepository(app)
    }
}
