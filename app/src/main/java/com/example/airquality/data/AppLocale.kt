package com.example.airquality.data

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.annotation.StringRes
import com.example.airquality.R
import java.util.Locale

/** 使用者在設定頁選的介面語言。 */
enum class AppLanguage(val tag: String, @StringRes val labelRes: Int) {
    /** 跟隨系統；資源找不到對應語系時會退回 values/（繁體中文）。 */
    SYSTEM("system", R.string.language_system),
    CHINESE("zh-TW", R.string.language_zh),
    ENGLISH("en", R.string.language_en);

    /** null 代表不覆寫，交給系統決定。 */
    val locale: Locale?
        get() = if (this == SYSTEM) null else Locale.forLanguageTag(tag)

    companion object {
        fun fromTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.tag == tag } ?: SYSTEM
    }
}

/**
 * 介面語言的持久化與套用。
 *
 * 專案沒有引入 AppCompat（畫面是 ComponentActivity + Compose），所以不能用
 * `AppCompatDelegate.setApplicationLocales`；這裡改成自己保存偏好，並在
 * [android.app.Activity.attachBaseContext] 把 Context 換成指定語系的版本，
 * 切換語言時再 `recreate()` 讓整個畫面以新語系重建。
 *
 * 這個物件不依賴 [AppContainer]：`attachBaseContext` 比
 * `Application.onCreate` 更早執行，那時 Repository 都還沒建好。
 */
object AppLocale {

    private const val PREFS = "app_locale"
    private const val KEY_LANGUAGE = "ui_language"

    fun saved(context: Context): AppLanguage =
        AppLanguage.fromTag(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE, null)
        )

    fun save(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.tag)
            .apply()
    }

    /** 回傳套用了目前語言偏好的 Context；選「跟隨系統」時原樣回傳。 */
    fun wrap(base: Context): Context {
        val locale = saved(base).locale ?: return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLocales(LocaleList(locale))
        return base.createConfigurationContext(config)
    }

    /**
     * 後端 `lang` 欄位要送的值：只取主要子標籤（zh / en），
     * 「跟隨系統」時看系統當下的語系。後端認不得的值會自己退回中文。
     */
    fun apiLanguageTag(context: Context): String {
        val locale = saved(context).locale
            ?: context.resources.configuration.locales[0]
        return if (locale.language == "en") "en" else "zh"
    }
}

/**
 * 在畫面之外取字串用的小工具（ViewModel、Repository、FCM Service）。
 *
 * 每次都依「當下存著的偏好」重新包一層 Context：語言切換後 Activity 會
 * recreate，但 applicationContext 的設定不會跟著變，直接拿它取字串會吐出
 * 切換前的語言。
 */
class Localizer(private val appContext: Context) {

    val language: AppLanguage
        get() = AppLocale.saved(appContext)

    fun setLanguage(language: AppLanguage) = AppLocale.save(appContext, language)

    /** 給 Compose 之外的地方用；Composable 裡請直接用 `stringResource`。 */
    fun string(@StringRes id: Int, vararg args: Any): String =
        AppLocale.wrap(appContext).getString(id, *args)

    fun apiLanguageTag(): String = AppLocale.apiLanguageTag(appContext)
}
