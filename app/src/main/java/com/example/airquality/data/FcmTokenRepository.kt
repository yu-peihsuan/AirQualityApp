package com.example.airquality.data

import android.content.Context
import android.util.Log
import com.example.airquality.AirQualityApiService
import com.example.airquality.DailyNotificationRequest
import com.example.airquality.DailyNotificationTestRequest
import com.example.airquality.FcmTokenRequest
import com.example.airquality.RetrofitClient
import com.google.firebase.messaging.FirebaseMessaging

/**
 * FCM 推播識別碼的保管與上傳。
 *
 * 注意：token 本身等同於「可以對這台裝置發推播」的憑證，任何情況下都不寫進
 * log。Release build 目前沒有開啟 minify／混淆，log 內容會原封不動留在裝置上，
 * 可能被同機的其他程式或使用者匯出的 bug report 一併帶走。
 */
open class FcmTokenRepository(
    context: Context,
    private val healthProfile: HealthProfileRepository,
    private val notificationSettings: NotificationSettingsRepository,
    private val api: AirQualityApiService = RetrofitClient.apiService
) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    open fun token(): String? = prefs.getString(KEY_TOKEN, null)

    open fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
        Log.d(TAG, "已更新推播識別碼")
    }

    /** App 啟動時呼叫：向 Firebase 要目前的 token 並存起來。 */
    open fun refreshToken() {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { saveToken(it) }
            .addOnFailureListener { Log.w(TAG, "取得推播識別碼失敗：${it.message}") }
    }

    /** 最近一次成功上傳的縣市，供 token 輪替時沿用（見 [uploadRegistration]）。 */
    open fun lastKnownCounty(): String = prefs.getString(KEY_LAST_COUNTY, "") ?: ""

    private fun lastKnownCoordinates(): Coordinates? {
        if (!prefs.contains(KEY_LAST_LAT) || !prefs.contains(KEY_LAST_LNG)) return null
        return Coordinates(
            java.lang.Double.longBitsToDouble(prefs.getLong(KEY_LAST_LAT, 0)),
            java.lang.Double.longBitsToDouble(prefs.getLong(KEY_LAST_LNG, 0))
        )
    }

    /**
     * 把 token 與所在地區送到後端，後端據此決定推播對象。
     *
     * [county] 留空代表「這次沒有新的縣市資訊」（例如 token 輪替時觸發的重新註冊），
     * 會改用上次記住的縣市；兩者都沒有時才真的送空字串。後端雖然也會忽略空縣市，
     * 但那份保護只存在於後端，App 這邊不該依賴它。
     *
     * conditions 只有在使用者於設定頁明確開啟「敏感族群警示推播」時才帶真實值；
     * 未開啟一律送空字串，讓後端清掉先前可能留下的健康屬性。
     */
    open suspend fun uploadRegistration(
        county: String = "",
        coordinates: Coordinates? = null
    ): SyncResult {
        val token = token() ?: return SyncResult.Failure("尚未取得推播識別碼")
        val effectiveCounty = county.ifBlank { lastKnownCounty() }
        val effectiveCoords = coordinates ?: lastKnownCoordinates()
        val conditions =
            if (notificationSettings.sensitiveAlertsEnabled) healthProfile.pushTargetingConditions()
            else ""

        return try {
            val response = api.registerFcmToken(
                FcmTokenRequest(
                    token = token,
                    county = effectiveCounty,
                    lat = effectiveCoords?.lat,
                    lng = effectiveCoords?.lng,
                    conditions = conditions
                )
            )
            if (response.status != "success") {
                Log.w(TAG, "推播註冊遭拒：${response.message}")
                return SyncResult.Failure(response.message)
            }
            rememberLocation(effectiveCounty, effectiveCoords)
            SyncResult.Success
        } catch (e: Exception) {
            Log.w(TAG, "推播註冊失敗：${e.message}")
            SyncResult.Failure(e.localizedMessage ?: "連線失敗")
        }
    }

    /** 設定或取消每日空氣品質摘要推播。 */
    open suspend fun setDailyNotification(enabled: Boolean, hour: Int, minute: Int): SyncResult {
        val token = token() ?: return SyncResult.Failure("尚未取得推播識別碼，請稍後再試")
        return try {
            val response = api.setDailyNotification(
                DailyNotificationRequest(token = token, enabled = enabled, hour = hour, minute = minute)
            )
            if (response.status == "success") SyncResult.Success
            else SyncResult.Failure(response.message ?: "後端未接受這次設定")
        } catch (e: Exception) {
            SyncResult.Failure(e.localizedMessage ?: "連線失敗")
        }
    }

    /** 立刻推一則測試通知；成功時回傳給使用者看的描述。 */
    open suspend fun sendTestNotification(): Result<String> {
        val token = token()
            ?: return Result.failure(IllegalStateException("尚未取得推播識別碼，請稍後再試"))
        return try {
            val response = api.testDailyNotification(DailyNotificationTestRequest(token = token))
            if (response.status == "success") {
                Result.success("✅ 測試通知已發送（${response.county} AQI ${response.aqi}）")
            } else {
                Result.failure(IllegalStateException(response.message ?: "發送失敗"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun rememberLocation(county: String, coordinates: Coordinates?) {
        prefs.edit().apply {
            if (county.isNotBlank()) putString(KEY_LAST_COUNTY, county)
            if (coordinates != null) {
                putLong(KEY_LAST_LAT, java.lang.Double.doubleToRawLongBits(coordinates.lat))
                putLong(KEY_LAST_LNG, java.lang.Double.doubleToRawLongBits(coordinates.lng))
            }
        }.apply()
    }

    private companion object {
        const val TAG = "FCM"
        const val PREFS = "fcm"
        const val KEY_TOKEN = "token"
        const val KEY_LAST_COUNTY = "last_county"
        const val KEY_LAST_LAT = "last_lat"
        const val KEY_LAST_LNG = "last_lng"
    }
}
