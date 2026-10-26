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

    /**
     * 地址 → 座標。查不到或連線失敗時回 null。
     *
     * 先用 Places 文字搜尋，查不到才退回 Geocoding。Geocoding 只比對門牌，
     * 「台北市中正區愛國西路1號」的第一筆（partial match）會錯接到同一所大學的
     * 天母校區，首頁最近測站因此變成士林；Places 與 Google 地圖 App 的搜尋同源，
     * 會正確給出博愛校區。
     *
     * 兩者的結果都不照單全收：地址裡寫到區時，不是精確門牌的結果要反查行政區、
     * 確認落在同一區才採用；全都對不上時才退回第一筆（維持原本行為）。
     */
    open suspend fun forwardGeocode(address: String): Coordinates? {
        val district = districtOf(address)
        val places = searchPlaces(address)
        verify(places, district)?.let { return it }
        val geocoded = geocodeCandidates(address)
        verify(geocoded, district)?.let { return it }
        return (places + geocoded).firstOrNull()?.coordinates
    }

    private data class Candidate(val coordinates: Coordinates, val precise: Boolean)

    /** 依序找第一個可信的候選；地址沒寫到區時無從驗證，直接用第一筆。 */
    private suspend fun verify(candidates: List<Candidate>, district: String?): Coordinates? {
        if (district == null) return candidates.firstOrNull()?.coordinates
        for (candidate in candidates) {
            if (candidate.precise) return candidate.coordinates
            val actual = reverseGeocodeCounty(candidate.coordinates.lat, candidate.coordinates.lng)
            if (actual != null && normalize(actual).contains(district)) return candidate.coordinates
            Log.w(TAG, "地理編碼結果落在「$actual」，與地址的「$district」不符，略過")
        }
        return null
    }

    /** Places API (New) 文字搜尋。結果是地點而非門牌，一律視為需要驗證。 */
    private suspend fun searchPlaces(address: String): List<Candidate> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(PLACES_ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("X-Goog-Api-Key", BuildConfig.GEOCODING_API_KEY)
                setRequestProperty("X-Goog-FieldMask", "places.location")
            }
            val body = JSONObject()
                .put("textQuery", address)
                .put("languageCode", "zh-TW")
                .put("regionCode", "TW")
                .put("pageSize", 3)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "地點搜尋失敗：HTTP ${conn.responseCode}")
                return@withContext emptyList()
            }
            val places = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                .optJSONArray("places") ?: return@withContext emptyList()
            (0 until places.length()).map { i ->
                val location = places.getJSONObject(i).getJSONObject("location")
                Candidate(Coordinates(location.getDouble("latitude"), location.getDouble("longitude")), precise = false)
            }
        } catch (e: Exception) {
            Log.w(TAG, "地點搜尋失敗：${e.message}")
            emptyList()
        } finally {
            conn?.disconnect()
        }
    }

    /** Geocoding API 的候選；精確門牌（非 partial match 且為 ROOFTOP 等）不需再驗證。 */
    private suspend fun geocodeCandidates(address: String): List<Candidate> {
        val encoded = URLEncoder.encode(address, "UTF-8")
        val json = get("address=$encoded") ?: return emptyList()
        return try {
            val results = json.getJSONArray("results")
            (0 until results.length()).map { i ->
                val result = results.getJSONObject(i)
                val geometry = result.getJSONObject("geometry")
                val location = geometry.getJSONObject("location")
                Candidate(
                    Coordinates(location.getDouble("lat"), location.getDouble("lng")),
                    precise = !result.optBoolean("partial_match") &&
                        geometry.optString("location_type") in PRECISE_LOCATION_TYPES
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "正向地理編碼結果解析失敗：${e.message}")
            emptyList()
        }
    }

    /** 從地址取出「縣市＋區」，例如「台北市中正區」；地址沒寫到區時回 null。 */
    private fun districtOf(address: String): String? =
        DISTRICT_PATTERN.find(normalize(address))?.let { it.groupValues[1] + it.groupValues[2] }

    private fun normalize(text: String) = text.replace("臺", "台")

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
        const val PLACES_ENDPOINT = "https://places.googleapis.com/v1/places:searchText"
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 10_000
        // ROOFTOP：精確門牌；RANGE_INTERPOLATED：門牌內插。其餘只到路段中心或概略位置
        val PRECISE_LOCATION_TYPES = setOf("ROOFTOP", "RANGE_INTERPOLATED")
        // 開頭可有郵遞區號，接著「XX縣／市」與「X～XX區／鄉／鎮／市」
        val DISTRICT_PATTERN = Regex("""^\d{0,6}\s*(?:台灣)?(\S{2}[縣市])(\S{1,3}?[區鄉鎮市])""")
    }
}
