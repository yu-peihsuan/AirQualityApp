package com.example.airquality

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UserManager {

    private const val PREF_NAME = "user_session"
    private const val KEY_EMAIL = "email"

    // ── 本機 email ────────────────────────────────────────────────────────────

    fun getEmail(context: Context): String? =
        prefs(context).getString(KEY_EMAIL, null)

    fun isLoggedIn(context: Context): Boolean =
        !getEmail(context).isNullOrBlank()

    fun saveEmail(context: Context, email: String) {
        prefs(context).edit().putString(KEY_EMAIL, email.trim().lowercase()).apply()
    }

    fun logout(context: Context) {
        prefs(context).edit().remove(KEY_EMAIL).apply()
    }

    // ── 後端登入（建立帳號 or 取回 profile）──────────────────────────────────

    suspend fun loginAndSync(context: Context, email: String): Result<UserProfileData?> =
        withContext(Dispatchers.IO) {
            try {
                val resp = RetrofitClient.apiService.login(EmailLoginRequest(email))
                if (resp.status == "ok") {
                    saveEmail(context, email)
                    if (resp.profile != null) {
                        applyProfileToPrefs(context, resp.profile)
                    } else {
                        clearProfilePrefs(context)
                    }
                    Result.success(resp.profile)
                } else {
                    Result.failure(Exception("登入失敗"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ── 推送 profile 到後端 ───────────────────────────────────────────────────

    suspend fun pushProfile(context: Context) = withContext(Dispatchers.IO) {
        val email = getEmail(context) ?: return@withContext
        val prefs = context.getSharedPreferences("health_profile", Context.MODE_PRIVATE)
        val favLocations = buildFavList(prefs)
        try {
            RetrofitClient.apiService.updateProfile(
                email = email,
                request = UserProfileRequest(
                    ageGroup      = prefs.getString("health_age_group", "18-64歲") ?: "18-64歲",
                    conditions    = prefs.getString("health_conditions", "") ?: "",
                    otherNotes    = prefs.getString("health_other", "") ?: "",
                    favLocations  = favLocations
                )
            )
        } catch (_: Exception) {}
    }

    // ── 把後端 profile 套用到本機 SharedPreferences ───────────────────────────

    fun applyProfileToPrefs(context: Context, profile: UserProfileData) {
        val prefs = context.getSharedPreferences("health_profile", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString("health_age_group",  profile.ageGroup)
        editor.putString("health_conditions", profile.conditions)
        editor.putString("health_other",      profile.otherNotes)

        // 常用地點
        editor.putInt("fav_count", profile.favLocations.size)
        profile.favLocations.forEachIndexed { i, fav ->
            editor.putString("fav_${i + 1}_name",    fav["name"]    ?: "")
            editor.putString("fav_${i + 1}_address", fav["address"] ?: "")
        }
        editor.apply()
    }

    // ── 私有工具 ──────────────────────────────────────────────────────────────

    private fun clearProfilePrefs(context: Context) {
        context.getSharedPreferences("health_profile", Context.MODE_PRIVATE)
            .edit()
            .putString("health_age_group",  "18-64歲")
            .putString("health_conditions", "")
            .putString("health_other",      "")
            .putInt("fav_count", 0)
            .apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private fun buildFavList(prefs: SharedPreferences): List<Map<String, String>> {
        val count = prefs.getInt("fav_count", 0)
        return (1..count).mapNotNull { i ->
            val name    = prefs.getString("fav_${i}_name",    "") ?: ""
            val address = prefs.getString("fav_${i}_address", "") ?: ""
            if (name.isNotEmpty() || address.isNotEmpty())
                mapOf("name" to name, "address" to address)
            else null
        }
    }
}
