# AirQuality App｜空氣品質即時監測 Android 前端

台灣空氣品質即時監測 App 的 Android 前端，以 Kotlin + Jetpack Compose 開發，
提供即時空品、污染熱點地圖、民眾回報與 RAG AI 個人化健康建議。

後端（FastAPI）：[yu-peihsuan/AirQuality_backend](https://github.com/yu-peihsuan/AirQuality_backend)

## 技術棧

- **語言**：Kotlin
- **UI**：Jetpack Compose + Material 3
- **架構**：MVVM + Repository（`data/` 層，由 `AppContainer` 注入）
- **網路**：Retrofit 2 + Gson + OkHttp（攔截器自動附加與續期裝置憑證）
- **定位**：Google Play Services Location
- **地圖**：Google Maps SDK + Maps Compose
- **推播**：Firebase Cloud Messaging
- **本機儲存**：SharedPreferences（健康檔案、定位偏好、通知設定）
- **建置**：Gradle（AGP 8.13.2、Kotlin 2.0.21、minSdk 24、targetSdk 36）

## 功能頁面

| 頁面 | 檔案 | 說明 |
|------|------|------|
| 首頁 | `HomeScreen.kt` | 當前 AQI、空氣品質臉、行動建議按鈕 |
| 通知中心 | `NotificationScreen.kt` | 火災警示、民眾回報、空品警報、預報異動、近期新聞 |
| 回報 | `ReportScreen.kt` | 提交污染回報、查看歷史回報 |
| AI 健康顧問 | `AiHealthScreen.kt` | RAG 個人化健康建議，整合天氣與預報資訊 |
| 熱點地圖 | `MapScreen.kt` | GIS 污染熱點、民生示警火災圖層 |
| 設定 | `SettingsScreen.kt` | 健康檔案（年齡、氣喘、心血管等）、通知設定 |

## 通知中心分類

| 類型 | 顏色 | 來源 |
|------|------|------|
| 火災警示 | 深紅 | NCDR 民生示警平台 |
| 民眾回報 | 橘色 | 使用者回報 |
| 空氣品質警報 | 紅色 | 環境部 AQI（≥ 151 才顯示） |
| 空品預報警示 | 藍色 | 環境部 AQF_P_01（僅顯示與當前狀態不同的預報） |
| 近期新聞 | 灰色 | 爬蟲新聞（24 小時內） |

## 動態桌面圖示

首頁取得 AQI 後，`AppIconManager` 依環境部六級指標啟用對應的 activity-alias，
桌面圖示自動切換為對應表情與顏色。僅於等級改變時切換，避免圖示頻繁閃動。

| 檔案 | 用途 |
|------|------|
| `AppIconManager.kt` | AQI 等級對應 activity-alias 的切換邏輯 |
| `AndroidManifest.xml` | 7 個 activity-alias（預設 + 六等級表情） |
| `design/icons/` | 圖示母檔與產生腳本，見 [design/icons/README.md](design/icons/README.md) |

## 專案結構

```
AirQualityApp/
├── app/
│   ├── build.gradle.kts                 # 金鑰注入、release 簽章、R8 設定
│   └── src/main/
│       ├── AndroidManifest.xml          # 權限、7 個 activity-alias
│       ├── java/com/example/airquality/
│       │   ├── MainActivity.kt          # 進入點、導覽
│       │   ├── AirQualityApplication.kt # Application、AppContainer 初始化
│       │   ├── AirQualityApi.kt         # Retrofit 介面、BASE_URL、資料模型
│       │   ├── AuthManager.kt           # 裝置憑證取得與自動續期
│       │   ├── AppIconManager.kt        # 動態桌面圖示切換
│       │   ├── AppHeader.kt             # 共用頁首
│       │   ├── MyFirebaseMessagingService.kt  # FCM 接收與通知顯示
│       │   ├── HealthProfileIntroDialog.kt    # 首次啟動說明健康資訊用途
│       │   ├── HomeScreen.kt / HomeViewModel.kt
│       │   ├── NotificationScreen.kt / NotificationViewModel.kt
│       │   ├── ReportScreen.kt / ReportViewModel.kt
│       │   ├── SettingsScreen.kt / SettingsViewModel.kt
│       │   ├── AiHealthScreen.kt
│       │   ├── MapScreen.kt
│       │   └── data/                    # Repository 層
│       │       ├── AppContainer.kt      # Repository 容器
│       │       ├── AirQualityRepository.kt          # 空品、新聞、回報、RAG
│       │       ├── GeocodingRepository.kt           # 地址轉座標
│       │       ├── LocationRepository.kt            # GPS 定位
│       │       ├── LocationPreferenceRepository.kt  # 使用者選定的縣市
│       │       ├── HealthProfileRepository.kt       # 健康檔案（本機）
│       │       ├── NotificationSettingsRepository.kt # 通知偏好
│       │       ├── FcmTokenRepository.kt            # FCM token 上傳
│       │       └── Coordinates.kt / SyncResult.kt   # 共用型別
│       └── res/
│           ├── mipmap-*/                # 桌面圖示（含六等級表情）
│           └── drawable/ · values/ · xml/
├── design/icons/                        # 圖示母檔與產生腳本
├── gradle/libs.versions.toml            # 版本目錄
└── build.gradle.kts / settings.gradle.kts
```
