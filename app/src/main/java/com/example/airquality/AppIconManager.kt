package com.example.airquality

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * 動態桌面圖示管理：依 AQI 等級啟用對應的 activity-alias，讓雲寶隨空品變臉。
 * 只在「等級改變」時才切換，避免桌面圖示頻繁閃動。
 */
object AppIconManager {

    private const val ALIAS_PACKAGE = "com.example.airquality"
    private const val PREFS_NAME = "app_icon"
    private const val KEY_CURRENT = "current_alias"
    private const val DEFAULT_ALIAS = "IconDefault"

    private val ALL_ALIASES = listOf(
        "IconDefault", "IconAqi0", "IconAqi51", "IconAqi101",
        "IconAqi151", "IconAqi201", "IconAqi301",
    )

    /** AQI 數值 → 對應的 alias 名稱（等級依環境部 AQI 分級） */
    fun aliasForAqi(aqi: Int?): String = when {
        aqi == null  -> DEFAULT_ALIAS
        aqi <= 50    -> "IconAqi0"
        aqi <= 100   -> "IconAqi51"
        aqi <= 150   -> "IconAqi101"
        aqi <= 200   -> "IconAqi151"
        aqi <= 300   -> "IconAqi201"
        else         -> "IconAqi301"
    }

    /** 依最新 AQI 更新桌面圖示；等級沒變則不動作。 */
    fun update(context: Context, aqi: Int?) {
        val target = aliasForAqi(aqi)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_CURRENT, DEFAULT_ALIAS) == target) return

        val pm = context.packageManager
        try {
            ALL_ALIASES.forEach { alias ->
                val state = if (alias == target) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
                pm.setComponentEnabledSetting(
                    ComponentName(context.packageName, "$ALIAS_PACKAGE.$alias"),
                    state,
                    PackageManager.DONT_KILL_APP,
                )
            }
            prefs.edit().putString(KEY_CURRENT, target).apply()
            Log.i("AppIconManager", "桌面圖示已切換 → $target (AQI=$aqi)")
        } catch (e: Exception) {
            Log.e("AppIconManager", "圖示切換失敗", e)
        }
    }
}
