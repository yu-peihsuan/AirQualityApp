package com.example.airquality

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * 動態桌面圖示管理：依 AQI 等級啟用對應的 activity-alias，讓雲寶隨空品變臉。
 *
 * 重要：切換 LAUNCHER activity-alias 若在 App 前景執行，即使帶 DONT_KILL_APP，
 * 系統仍會重載桌面、把當前 App 丟到背景（使用者被踢回桌面）。
 * 因此採「記錄 → 套用」兩段式：前景只用 [recordAqi] 記下目標，
 * 待 App 退到背景時（MainActivity.onStop）才由 [applyPending] 實際切換。
 */
object AppIconManager {

    private const val ALIAS_PACKAGE = "com.example.airquality"
    private const val PREFS_NAME = "app_icon"
    private const val KEY_CURRENT = "current_alias"
    private const val KEY_PENDING = "pending_alias"
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

    /** 前景呼叫：只記錄最新 AQI 對應的目標圖示，不切換（避免把使用者踢回桌面）。 */
    fun recordAqi(context: Context, aqi: Int?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_PENDING, aliasForAqi(aqi)).apply()
    }

    /** App 退到背景時呼叫：把桌面圖示切換到先前記錄的目標；等級沒變則不動作。 */
    fun applyPending(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val target = prefs.getString(KEY_PENDING, DEFAULT_ALIAS) ?: DEFAULT_ALIAS
        if (prefs.getString(KEY_CURRENT, DEFAULT_ALIAS) == target) return

        val pm = context.packageManager
        try {
            // 順序很重要：先啟用新圖示、再停用其他。
            // 若先停用舊的，會出現「沒有任何啟用中的桌面入口」的空窗，
            // 部分廠牌桌面在該瞬間會退回顯示 manifest 預設圖示且不再更新（圖示卡死）。
            pm.setComponentEnabledSetting(
                ComponentName(context.packageName, "$ALIAS_PACKAGE.$target"),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            ALL_ALIASES.filter { it != target }.forEach { alias ->
                pm.setComponentEnabledSetting(
                    ComponentName(context.packageName, "$ALIAS_PACKAGE.$alias"),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
            prefs.edit().putString(KEY_CURRENT, target).apply()
            Log.i("AppIconManager", "桌面圖示已切換 → $target")
        } catch (e: Exception) {
            Log.e("AppIconManager", "圖示切換失敗", e)
        }
    }
}
