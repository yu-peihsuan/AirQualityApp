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
     * 是否同意把健康屬性送到伺服器，用於空品警示的分眾推播。
     *
     * 健康屬性屬於特種個人資料，預設為關閉：使用者沒有明確開啟以前，
     * [FcmTokenRepository] 送出的 conditions 一律是空字串，
     * 後端會據此清掉舊值（見後端 register_token 的空值語意說明）。
     */
    open var sensitiveAlertsEnabled: Boolean
        get() = prefs.getBoolean(KEY_SENSITIVE_ALERTS, false)
        set(value) { prefs.edit().putBoolean(KEY_SENSITIVE_ALERTS, value).apply() }

    /**
     * 是否已經看過「為什麼需要你的健康狀況」的說明彈窗。
     *
     * 那個彈窗只做說明與引導、不代表同意，所以只跳一次；使用者按「去填寫」
     * 或「稍後再說」都算看過。真正的同意是健康檔案裡的
     * [sensitiveAlertsEnabled] 開關。
     */
    open var hasSeenHealthProfileIntro: Boolean
        get() = prefs.getBoolean(KEY_INTRO_SEEN, false)
        set(value) { prefs.edit().putBoolean(KEY_INTRO_SEEN, value).apply() }

    private companion object {
        const val PREFS = "notification_settings"
        const val KEY_DAILY_ENABLED = "daily_enabled"
        const val KEY_DAILY_HOUR = "daily_hour"
        const val KEY_DAILY_MINUTE = "daily_minute"
        const val KEY_SENSITIVE_ALERTS = "sensitive_alerts_enabled"
        const val KEY_INTRO_SEEN = "health_profile_intro_seen"
    }
}
