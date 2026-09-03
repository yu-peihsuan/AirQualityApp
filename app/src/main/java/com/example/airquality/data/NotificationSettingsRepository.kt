package com.example.airquality.data

import android.content.Context

/** 使用者的推播偏好設定（本機保存，並在變更時同步到後端）。 */
open class NotificationSettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    open var dailyEnabled: Boolean
        get() = prefs.getBoolean(KEY_DAILY_ENABLED, false)
        set(value) { prefs.edit().putBoolean(KEY_DAILY_ENABLED, value).apply() }

    open var dailyHour: Int
        get() = prefs.getInt(KEY_DAILY_HOUR, 8)
        set(value) { prefs.edit().putInt(KEY_DAILY_HOUR, value).apply() }

    open var dailyMinute: Int
        get() = prefs.getInt(KEY_DAILY_MINUTE, 0)
        set(value) { prefs.edit().putInt(KEY_DAILY_MINUTE, value).apply() }

    /**
     * 是否同意把健康屬性送到伺服器，用於敏感族群的空品警示推播。
     *
     * 健康屬性屬於特種個人資料，預設為關閉：使用者沒有明確開啟以前，
     * [FcmTokenRepository] 送出的 conditions 一律是空字串，
     * 後端會據此清掉舊值（見後端 register_token 的空值語意說明）。
     */
    open var sensitiveAlertsEnabled: Boolean
        get() = prefs.getBoolean(KEY_SENSITIVE_ALERTS, false)
        set(value) { prefs.edit().putBoolean(KEY_SENSITIVE_ALERTS, value).apply() }

    /**
     * 是否已經問過使用者要不要開啟敏感族群警示。
     *
     * 首次啟動的同意彈窗只跳一次：不論使用者按同意或先不要，都記成「已問過」，
     * 之後改由設定頁的開關管理，不再打擾。
     */
    open var hasAskedSensitiveAlertsConsent: Boolean
        get() = prefs.getBoolean(KEY_CONSENT_ASKED, false)
        set(value) { prefs.edit().putBoolean(KEY_CONSENT_ASKED, value).apply() }

    private companion object {
        const val PREFS = "notification_settings"
        const val KEY_DAILY_ENABLED = "daily_enabled"
        const val KEY_DAILY_HOUR = "daily_hour"
        const val KEY_DAILY_MINUTE = "daily_minute"
        const val KEY_SENSITIVE_ALERTS = "sensitive_alerts_enabled"
        const val KEY_CONSENT_ASKED = "sensitive_alerts_consent_asked"
    }
}
