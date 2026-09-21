package com.example.airquality

import androidx.annotation.StringRes

/**
 * 污染事件類型的顯示字串。
 *
 * 後端（民眾回報的 LLM 結構化結果、熱點分析的 dominant_type）一律回英文代碼，
 * 認不得的代碼交給呼叫端指定 [fallback]：下風處警告要的是「污染源」這種
 * 名詞，通知卡片要的則是「污染回報」。
 */
@StringRes
fun eventTypeLabelRes(
    type: String?,
    @StringRes fallback: Int = R.string.event_unknown_report,
): Int = when (type) {
    "fire"                -> R.string.event_fire
    "chemical"            -> R.string.event_chemical
    "dust"                -> R.string.event_dust
    "odor"                -> R.string.event_odor
    "vehicle"             -> R.string.event_vehicle
    "factory"             -> R.string.event_factory
    "general_air_quality" -> R.string.event_general
    else                  -> fallback
}
