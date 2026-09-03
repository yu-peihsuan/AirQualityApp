package com.example.airquality.data

import android.content.Context
import com.example.airquality.RagUserProfile

/**
 * 使用者填寫的健康屬性。依隱私權政策，這份資料以裝置本機為主要儲存位置。
 *
 * [toRagUserProfile] 供 AI 建議使用（使用者主動觸發才會送出）；
 * [pushTargetingConditions] 供推播分眾使用，且只在使用者於設定頁
 * 明確開啟「敏感族群警示推播」時才會被 [FcmTokenRepository] 送到後端。
 */
open class HealthProfileRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    open var ageGroup: String
        get() = prefs.getString(KEY_AGE_GROUP, DEFAULT_AGE_GROUP) ?: DEFAULT_AGE_GROUP
        set(value) { prefs.edit().putString(KEY_AGE_GROUP, value).apply() }

    open var conditions: List<String>
        get() = (prefs.getString(KEY_CONDITIONS, "") ?: "")
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }
        set(value) { prefs.edit().putString(KEY_CONDITIONS, value.joinToString(",")).apply() }

    open var otherNotes: String
        get() = prefs.getString(KEY_OTHER, "") ?: ""
        set(value) { prefs.edit().putString(KEY_OTHER, value).apply() }

    /**
     * 是否已同意將健康屬性送出以生成 AI 建議。
     *
     * Google Play 的 Prominent Disclosure & Consent 要求：把個人敏感資料
     * （健康資訊）交給第三方前，必須在 App 內、於一般使用流程中揭露，並取得
     * 明確的肯定操作（點擊接受），不能只寫在設定頁或隱私權政策裡。
     * AI 建議會把這份資料轉交 OpenRouter，因此第一次產生前必須由使用者按下按鈕。
     */
    open var hasConsentedToAiSharing: Boolean
        get() = prefs.getBoolean(KEY_AI_CONSENT, false)
        set(value) { prefs.edit().putBoolean(KEY_AI_CONSENT, value).apply() }

    open fun save(ageGroup: String, conditions: List<String>, otherNotes: String) {
        prefs.edit()
            .putString(KEY_AGE_GROUP, ageGroup)
            .putString(KEY_CONDITIONS, conditions.joinToString(","))
            .putString(KEY_OTHER, otherNotes)
            .apply()
    }

    /** 轉為 RAG API 需要的格式。 */
    open fun toRagUserProfile(): RagUserProfile {
        val conds = conditions
        val label = ageGroup
        return RagUserProfile(
            ageGroup = when {
                label.contains("18歲以下") -> "child"
                label.contains("65")      -> "elderly"
                else                      -> "adult"
            },
            isPregnant        = conds.contains("懷孕中"),
            hasAsthma         = conds.contains("氣喘") || conds.contains("呼吸道疾病"),
            hasCardiovascular = conds.contains("心血管疾病") || conds.contains("高血壓"),
            hasAllergy        = conds.contains("過敏"),
            otherNotes        = otherNotes.ifBlank { null }
        )
    }

    /**
     * 推播分眾用的健康狀況字串（逗號分隔），後端據此決定誰要收到敏感族群警示。
     * 年齡層也一併轉成後端認得的關鍵字。
     */
    open fun pushTargetingConditions(): String {
        val label = ageGroup
        return buildString {
            conditions.forEach { append(",").append(it) }
            if (label.contains("18歲以下")) append(",18歲以下")
            if (label.contains("65"))      append(",65歲以上")
        }.trimStart(',')
    }

    private companion object {
        const val PREFS = "health_profile"
        const val KEY_AGE_GROUP = "health_age_group"
        const val KEY_CONDITIONS = "health_conditions"
        const val KEY_OTHER = "health_other"
        const val KEY_AI_CONSENT = "ai_sharing_consented"
        const val DEFAULT_AGE_GROUP = "18-64歲"
    }
}
