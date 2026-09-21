package com.example.airquality.data

import androidx.annotation.StringRes
import com.example.airquality.R

/**
 * 健康檔案的年齡層與病史選項。
 *
 * [value] 是存進 SharedPreferences、也會送到後端作為推播分眾依據的正規值，
 * 一律維持中文——它是資料不是文案，翻譯它會讓既有使用者的設定失效、也會
 * 讓後端的分眾條件比對不到（見 [HealthProfileRepository.toRagUserProfile]）。
 * 介面上顯示的是 [labelRes]。
 */
data class HealthOption(val value: String, @StringRes val labelRes: Int)

object HealthOptions {

    const val AGE_UNDER_18 = "18歲以下"
    const val AGE_18_64    = "18-64歲"
    const val AGE_65_PLUS  = "65歲以上"

    const val ASTHMA         = "氣喘"
    const val CARDIOVASCULAR = "心血管疾病"
    const val PREGNANT       = "懷孕中"
    const val ALLERGY        = "過敏"
    const val RESPIRATORY    = "呼吸道疾病"
    const val HYPERTENSION   = "高血壓"

    val ageGroups = listOf(
        HealthOption(AGE_UNDER_18, R.string.age_under_18),
        HealthOption(AGE_18_64,    R.string.age_18_64),
        HealthOption(AGE_65_PLUS,  R.string.age_65_plus),
    )

    val conditions = listOf(
        HealthOption(ASTHMA,         R.string.condition_asthma),
        HealthOption(CARDIOVASCULAR, R.string.condition_cardiovascular),
        HealthOption(PREGNANT,       R.string.condition_pregnant),
        HealthOption(ALLERGY,        R.string.condition_allergy),
        HealthOption(RESPIRATORY,    R.string.condition_respiratory),
        HealthOption(HYPERTENSION,   R.string.condition_hypertension),
    )
}
