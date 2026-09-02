package com.example.airquality.data

import android.content.Context

/** 使用者的常用地點。 */
data class FavoriteLocation(val name: String, val address: String)

/** 上次選擇的地點來源。 */
sealed class LocationChoice {
    object Gps : LocationChoice()
    data class Saved(val name: String, val address: String) : LocationChoice()
}

/**
 * 記住使用者的地點選擇與常用地點清單。
 *
 * 常用地點沿用既有的 "health_profile" prefs 檔與 fav_N_* 鍵名，
 * 讓已安裝舊版的使用者升級後資料不會消失。
 */
open class LocationPreferenceRepository(context: Context) {

    private val choicePrefs = context.getSharedPreferences(CHOICE_PREFS, Context.MODE_PRIVATE)
    private val favPrefs = context.getSharedPreferences(FAV_PREFS, Context.MODE_PRIVATE)

    open fun currentChoice(): LocationChoice {
        val mode = choicePrefs.getString(KEY_MODE, MODE_GPS) ?: MODE_GPS
        val address = choicePrefs.getString(KEY_ADDRESS, "") ?: ""
        return if (mode == MODE_SAVED && address.isNotBlank()) {
            LocationChoice.Saved(choicePrefs.getString(KEY_NAME, "") ?: "", address)
        } else {
            LocationChoice.Gps
        }
    }

    /** 是否曾經手動選過一個地區。 */
    open fun hasSavedChoice(): Boolean = currentChoice() is LocationChoice.Saved

    open fun saveChoice(choice: LocationChoice) {
        val editor = choicePrefs.edit()
        when (choice) {
            is LocationChoice.Gps -> editor
                .putString(KEY_MODE, MODE_GPS)
                .putString(KEY_NAME, "")
                .putString(KEY_ADDRESS, "")
            is LocationChoice.Saved -> editor
                .putString(KEY_MODE, MODE_SAVED)
                .putString(KEY_NAME, choice.name)
                .putString(KEY_ADDRESS, choice.address)
        }
        editor.apply()
    }

    open fun favorites(): List<FavoriteLocation> {
        val count = favPrefs.getInt(KEY_FAV_COUNT, 0)
        return (1..count).mapNotNull { i ->
            val name = favPrefs.getString("fav_${i}_name", "")?.trim() ?: ""
            val address = favPrefs.getString("fav_${i}_address", "")?.trim() ?: ""
            if (name.isNotEmpty() || address.isNotEmpty()) FavoriteLocation(name, address) else null
        }
    }

    open fun saveFavorites(favorites: List<FavoriteLocation>) {
        val editor = favPrefs.edit()
        favorites.forEachIndexed { idx, f ->
            editor.putString("fav_${idx + 1}_name", f.name)
            editor.putString("fav_${idx + 1}_address", f.address)
        }
        // 清掉縮短後多出來的殘留項目，避免 fav_count 之後變大時又讀回舊值
        val previousCount = favPrefs.getInt(KEY_FAV_COUNT, 0)
        for (i in favorites.size + 1..previousCount) {
            editor.remove("fav_${i}_name").remove("fav_${i}_address")
        }
        editor.putInt(KEY_FAV_COUNT, favorites.size).apply()
    }

    /** 附加一個常用地點到清單尾端。 */
    open fun addFavorite(favorite: FavoriteLocation) {
        saveFavorites(favorites() + favorite)
    }

    private companion object {
        const val CHOICE_PREFS = "location_pref"
        const val FAV_PREFS = "health_profile"
        const val KEY_MODE = "location_mode"
        const val KEY_NAME = "location_name"
        const val KEY_ADDRESS = "location_address"
        const val KEY_FAV_COUNT = "fav_count"
        const val MODE_GPS = "gps"
        const val MODE_SAVED = "saved"
    }
}
