package com.example.airquality.data

import com.example.airquality.AirQualityResponse
import com.example.airquality.AirQualityApiService
import com.example.airquality.AqiRecord
import com.example.airquality.EstimateResponse
import com.example.airquality.HotspotResponse
import com.example.airquality.NewsResponse
import com.example.airquality.RagAdviceRequest
import com.example.airquality.RagAdviceResponse
import com.example.airquality.ReportRequest
import com.example.airquality.ReportResponse
import com.example.airquality.RetrofitClient
import com.example.airquality.WeatherResponse

/**
 * 後端 API 的唯一進入點。
 *
 * ViewModel 一律透過這一層取資料，不直接碰 [RetrofitClient]，
 * 這樣換掉網路實作或在測試裡塞假資料時，畫面與 ViewModel 都不用改。
 */
open class AirQualityRepository(
    private val api: AirQualityApiService = RetrofitClient.apiService
) {

    open suspend fun airQuality(county: String? = null): AirQualityResponse =
        api.getAirQuality(county)

    open suspend fun weather(county: String?, lat: Double?, lng: Double?): WeatherResponse =
        api.getWeather(county, lat, lng)

    open suspend fun news(region: String? = null): NewsResponse = api.getNews(region)

    open suspend fun userReports(region: String? = null): NewsResponse = api.getUserReports(region)

    open suspend fun fireAlerts(region: String? = null): NewsResponse = api.getFireAlerts(region)

    open suspend fun forecast(county: String? = null): NewsResponse = api.getForecast(county)

    open suspend fun submitReport(request: ReportRequest): ReportResponse = api.submitReport(request)

    open suspend fun ragAdvice(request: RagAdviceRequest): RagAdviceResponse = api.getRagAdvice(request)

    open suspend fun hotspots(): HotspotResponse = api.getHotspots()

    open suspend fun aqiEstimate(lat: Double, lng: Double): EstimateResponse =
        api.getAqiEstimate(lat, lng)

    /** 從測站清單中找出離 [lat]／[lng] 最近的一站。 */
    fun nearestStation(records: List<AqiRecord>, lat: Double, lng: Double): AqiRecord? =
        records.minByOrNull {
            haversineKm(
                lat, lng,
                it.latitude.toDoubleOrNull() ?: 0.0,
                it.longitude.toDoubleOrNull() ?: 0.0
            )
        }
}
