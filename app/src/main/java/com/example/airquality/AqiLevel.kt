package com.example.airquality

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.example.airquality.ui.theme.AqiGreen
import com.example.airquality.ui.theme.AqiMaroon
import com.example.airquality.ui.theme.AqiOrange
import com.example.airquality.ui.theme.AqiPurple
import com.example.airquality.ui.theme.AqiRed
import com.example.airquality.ui.theme.AqiYellow

/**
 * AQI 等級。
 *
 * 環境部 API 與後端回傳的 `status`／`aqi_level` 都是中文字串，那是資料不是文案，
 * 所以不直接顯示：先收斂成這個列舉，再由畫面取對應語言的字串資源。
 * 用 AQI 數值判斷（[fromAqi]）比解析字串可靠，字串版只在拿不到數值時使用。
 */
enum class AqiLevel(
    @StringRes val labelRes: Int,
    /** 首頁大標題用的簡短版：兩種「不健康」在這裡都收斂成同一個詞。 */
    @StringRes val shortLabelRes: Int,
    val color: Color,
) {
    GOOD(R.string.aqi_level_good, R.string.aqi_level_good, AqiGreen),
    MODERATE(R.string.aqi_level_moderate, R.string.aqi_level_moderate, AqiYellow),
    UNHEALTHY_SENSITIVE(
        R.string.aqi_level_unhealthy_sensitive,
        R.string.aqi_level_unhealthy_short,
        AqiOrange,
    ),
    UNHEALTHY(R.string.aqi_level_unhealthy, R.string.aqi_level_unhealthy_short, AqiRed),
    VERY_UNHEALTHY(R.string.aqi_level_very_unhealthy, R.string.aqi_level_very_unhealthy, AqiPurple),
    HAZARDOUS(R.string.aqi_level_hazardous, R.string.aqi_level_hazardous, AqiMaroon);

    companion object {

        fun fromAqi(aqi: Int): AqiLevel = when {
            aqi <= 50  -> GOOD
            aqi <= 100 -> MODERATE
            aqi <= 150 -> UNHEALTHY_SENSITIVE
            aqi <= 200 -> UNHEALTHY
            aqi <= 300 -> VERY_UNHEALTHY
            else       -> HAZARDOUS
        }

        /** 後端／環境部的中文等級字串；認不得就回 null。 */
        fun fromStatusOrNull(status: String): AqiLevel? = when {
            status.contains("良好")             -> GOOD
            status.contains("普通")             -> MODERATE
            status.contains("對敏感族群不健康") -> UNHEALTHY_SENSITIVE
            status.contains("對所有族群不健康") -> UNHEALTHY
            status.contains("非常不健康")       -> VERY_UNHEALTHY
            status.contains("危害")             -> HAZARDOUS
            else                                -> null
        }

        /** 認不得時退回 [GOOD]，與改版前 `getAqiColor` 的行為一致。 */
        fun fromStatus(status: String): AqiLevel = fromStatusOrNull(status) ?: GOOD

        /**
         * 等級字串優先（維持與改版前完全相同的判定），認不得才用數值補。
         * 兩者都沒有就當作 [GOOD]，同樣沿用原本的預設。
         */
        fun of(status: String, aqi: Int?): AqiLevel =
            fromStatusOrNull(status) ?: aqi?.let(::fromAqi) ?: GOOD
    }
}
