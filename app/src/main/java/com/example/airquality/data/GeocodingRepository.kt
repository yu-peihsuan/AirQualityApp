package com.example.airquality.data

import android.util.Log
import com.example.airquality.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Google Maps Geocoding API。首頁與通報頁原本各有一份幾乎相同的實作，這裡合併為一處。
 *
 * 逾時必須自己設定：[HttpURLConnection] 預設的 connectTimeout／readTimeout 都是 0
 * （代表無限等待）。網路存在但對方不回應時（例如連到會吃掉封包的 captive portal），
 * 呼叫端的 coroutine 會一直卡在 Loading，畫面永遠轉圈。
 */
open class GeocodingRepository {

    /** 地址 → 座標。查不到或連線失敗時回 null。 */
    open suspend fun forwardGeocode(address: String): Coordinates? {
        val encoded = URLEncoder.encode(address, "UTF-8")
        val json = get("address=$encoded") ?: return null
        return try {
            val location = json.getJSONArray("results")
                .getJSONObject(0)
                .getJSONObject("geometry")
                .getJSONObject("location")
            Coordinates(location.getDouble("lat"), location.getDouble("lng"))
        } catch (e: Exception) {
            Log.w(TAG, "正向地理編碼結果解析失敗：${e.message}")
            null
        }
    }

    /** 座標 → 可讀地址（formatted_address）。 */
    open suspend fun reverseGeocodeAddress(lat: Double, lng: Double): String? {
        val json = get("latlng=$lat,$lng") ?: return null
        return try {
            json.getJSONArray("results").getJSONObject(0).getString("formatted_address")
        } catch (e: Exception) {
            Log.w(TAG, "反向地理編碼結果解析失敗：${e.message}")
            null
        }
    }

    /** 座標 → 縣市（+ 鄉鎮市區）名稱，例如「新北市板橋區」。 */
    open suspend fun reverseGeocodeCounty(lat: Double, lng: Double): String? {
        val json = get("latlng=$lat,$lng") ?: return null
        return try {
            val components = json.getJSONArray("results")
                .getJSONObject(0)
                .getJSONArray("address_components")
            var adminArea = ""
            var subAdminArea = ""
            for (i in 0 until components.length()) {
                val comp = components.getJSONObject(i)
                val types = comp.getJSONArray("types")
                val typeList = (0 until types.length()).map { types.getString(it) }
                when {
                    "administrative_area_level_1" in typeList -> adminArea = comp.getString("long_name")
                    "administrative_area_level_2" in typeList -> subAdminArea = comp.getString("long_name")
                }
            }
            (adminArea + subAdminArea).ifBlank { null }
        } catch (e: Exception) {
            Log.w(TAG, "縣市解析失敗：${e.message}")
            null
        }
    }

    /** 送出查詢並回傳 status 為 OK 的結果；任何失敗都回 null。 */
    private suspend fun get(query: String): JSONObject? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("$ENDPOINT?$query&key=${BuildConfig.GEOCODING_API_KEY}&language=zh-TW")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            if (json.optString("status") == "OK") json else null
        } catch (e: Exception) {
            // 逾時（SocketTimeoutException）也會走到這裡，呼叫端因此拿得到 null 而不是無限等待
            Log.w(TAG, "地理編碼請求失敗：${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    private companion object {
        const val TAG = "Geocoding"
        const val ENDPOINT = "https://maps.googleapis.com/maps/api/geocode/json"
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 10_000
    }
}
