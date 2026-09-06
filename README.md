# AirQuality App — Android

台灣空氣品質即時監測 App 的 Android 前端，以 **Kotlin + Jetpack Compose** 開發，
提供即時空品、污染熱點地圖、民眾回報與 RAG AI 個人化健康建議。

後端（FastAPI）：[yu-peihsuan/AirQuality_backend](https://github.com/yu-peihsuan/AirQuality_backend)

- **applicationId**：`com.peihsuan.airquality`（程式碼 namespace 為 `com.example.airquality`，兩者不同屬正常設定）
- **後端**：已部署於 Google Cloud Run，App 開箱即用，**不需啟動任何本機服務**
- **特色**：**動態桌面圖示** — 圖示依即時 AQI 等級自動變臉（六種表情）並更換顏色

---

## 技術棧

- **UI**：Jetpack Compose + Material 3
- **架構**：MVVM + Repository（`data/` 層，`AppContainer` 手動注入）
- **網路**：Retrofit 2 + Gson + OkHttp（`AuthInterceptor` / `TokenAuthenticator`）
- **定位**：Google Play Services Location
- **地圖**：Google Maps SDK + Maps Compose
- **推播**：Firebase Cloud Messaging
- **本機儲存**：SharedPreferences（健康檔案、定位偏好、通知設定）

## 系統需求

| 項目 | 版本 |
|------|------|
| Android Studio | Hedgehog 以上 |
| AGP | 8.13.2 |
| Kotlin | 2.0.21 |
| Compose BOM | 2024.09.00 |
| minSdk | 24（Android 7.0） |
| targetSdk / compileSdk | 36 |
| Java | 11 |

---

## 專案設定（第一次執行必做）

### 1. `local.properties`

在專案根目錄建立 `local.properties`（已列入 `.gitignore`，不會上傳）：

```properties
# 地圖顯示（Maps SDK）：Android 應用程式限制的金鑰
MAPS_API_KEY=你的_Maps_API_Key

# 地址搜尋（Geocoding HTTP 呼叫）：僅限 Geocoding API 的伺服器金鑰
GEOCODING_API_KEY=你的_Geocoding_API_Key
```

兩把金鑰刻意分開：Maps SDK 的金鑰有 Android 應用程式限制、只進 Manifest；
Geocoding 走 HTTP 呼叫，需要另一把不受應用程式限制的金鑰。

> 若要建置 **release** 版，另需在 `local.properties` 補上簽章資訊
> （`KEYSTORE_FILE`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`）；
> keystore 檔存放在專案外層的 `keystore/` 目錄，不入版控。

### 2. Firebase 設定檔

`app/google-services.json` **已移出版本控管**，需自行取得後放到 `app/` 目錄：

Firebase Console → 專案 `airquality-4d1b6` → 專案設定 → 你的應用程式 →
下載 `google-services.json`。

沒有這個檔案 Gradle 會建置失敗（`com.google.gms.google-services` plugin 找不到設定）。

### 3. 後端連線

API 位址定義在
[AirQualityApi.kt](app/src/main/java/com/example/airquality/AirQualityApi.kt)，
**預設連向雲端正式後端**：

```kotlin
private const val BASE_URL = "https://airquality-api-968727437042.asia-east1.run.app/"
```

> 需要連本機後端測試時，暫時改成 `http://10.0.2.2:8000/`（模擬器連本機的固定 IP），
> 並在 `AndroidManifest.xml` 的 `<application>` 暫時加回
> `android:usesCleartextTraffic="true"`。**測完記得改回來，不要 commit。**

### 4. Android Studio Run 設定（重要）

本 App 以 activity-alias 實作動態圖示，Run 按鈕需指定啟動 Activity 才不會報錯：

**Run → Edit Configurations → app → Launch Options →
Launch = Specified Activity → `com.example.airquality.MainActivity`**

### 5. 建置執行

1. 用 Android Studio 開啟 `AirQualityApp/` 資料夾
2. 確認上述 `local.properties` 與 `google-services.json` 已就位
3. 選擇模擬器（需含 **Google Play**）或實體裝置
4. 點擊 Run ▶

---

## 功能頁面

| 頁面 | 檔案 | 說明 |
|------|------|------|
| 首頁 | `HomeScreen.kt` | 當前 AQI、空氣品質臉、行動建議按鈕 |
| 通知中心 | `NotificationScreen.kt` | 火災警示、民眾回報、空品警報、預報異動、近期新聞 |
| 回報 | `ReportScreen.kt` | 提交污染回報、查看歷史回報 |
| AI 健康顧問 | `AiHealthScreen.kt` | RAG 個人化健康建議，整合天氣與預報資訊 |
| 熱點地圖 | `MapScreen.kt` | GIS 污染熱點、民生示警火災圖層 |
| 設定 | `SettingsScreen.kt` | 健康檔案（年齡、氣喘、心血管等）、通知設定 |

### 通知中心分類

| 類型 | 顏色 | 來源 |
|------|------|------|
| 🔥 火災警示 | 深紅 | 民生示警平台（NCDR） |
| 👤 民眾回報 | 橘色 | 使用者回報 |
| 🔴 空氣品質警報 | 紅色 | 環境部 AQI（≥ 151 才顯示） |
| 📅 空品預報警示 | 藍色 | 環境部 AQF_P_01（僅顯示與當前狀態不同的預報） |
| 📰 近期新聞 | 灰色 | 爬蟲新聞（24 小時內） |

---

## 架構

```
View（Composable）        *Screen.kt
      ↕ StateFlow
ViewModel                 HomeViewModel / NotificationViewModel
                          ReportViewModel / SettingsViewModel
      ↕
Repository（data/）       AirQualityRepository・GeocodingRepository
                          LocationRepository・HealthProfileRepository
                          NotificationSettingsRepository・FcmTokenRepository
      ↕
網路層                    AirQualityApi.kt（Retrofit + Gson）
                          AuthInterceptor：自動附加 Bearer token
                          TokenAuthenticator：401 時自動續期
```

`AppContainer.kt` 集中建立並持有 Repository 實例（於
`AirQualityApplication.kt` 初始化），畫面層不直接碰 Retrofit 或 SharedPreferences。

**認證對畫面層是透明的**：App 首次啟動時 `AuthManager` 以裝置識別碼向
`/api/auth/device` 換取 JWT，之後由 OkHttp 攔截器自動附加與續期。
設計細節見後端的 [docs/auth.md](https://github.com/yu-peihsuan/AirQuality_backend/blob/main/docs/auth.md)。

---

## 動態桌面圖示

首頁取得 AQI 後，`AppIconManager` 依環境部六級指標啟用對應的 activity-alias，
桌面圖示自動切換為對應表情。僅於**等級改變時**切換，避免圖示頻繁閃動。

```
AppIconManager.kt        AQI 等級 → activity-alias 切換邏輯
AndroidManifest.xml      7 個 activity-alias（預設 + 六等級表情）
design/icons/            圖示母檔與產生腳本（見該目錄 README）
```

改圖示設計請見 [design/icons/README.md](design/icons/README.md)。

---

## 專案結構

```
AirQualityApp/
├── app/
│   ├── build.gradle.kts             金鑰注入、release 簽章、R8 設定
│   ├── google-services.json         Firebase 設定（需自行放入，不入版控）
│   └── src/main/
│       ├── AndroidManifest.xml      權限、7 個 activity-alias
│       ├── java/com/example/airquality/
│       │   ├── MainActivity.kt              進入點、導覽
│       │   ├── AirQualityApplication.kt     Application、AppContainer 初始化
│       │   ├── AirQualityApi.kt             Retrofit 介面、BASE_URL、資料模型
│       │   ├── AuthManager.kt               裝置憑證取得與續期
│       │   ├── AppIconManager.kt            動態桌面圖示切換
│       │   ├── AppHeader.kt                 共用頁首
│       │   ├── MyFirebaseMessagingService.kt  FCM 接收與通知顯示
│       │   ├── HomeScreen.kt / HomeViewModel.kt
│       │   ├── NotificationScreen.kt / NotificationViewModel.kt
│       │   ├── ReportScreen.kt / ReportViewModel.kt
│       │   ├── SettingsScreen.kt / SettingsViewModel.kt
│       │   ├── AiHealthScreen.kt
│       │   ├── MapScreen.kt
│       │   ├── HealthProfileIntroDialog.kt  首次啟動說明健康資訊用途
│       │   └── data/
│       │       ├── AppContainer.kt                 Repository 容器
│       │       ├── AirQualityRepository.kt         空品／新聞／回報／RAG
│       │       ├── GeocodingRepository.kt          地址 → 座標
│       │       ├── LocationRepository.kt           GPS 定位
│       │       ├── LocationPreferenceRepository.kt 使用者選定的縣市
│       │       ├── HealthProfileRepository.kt      健康檔案（本機）
│       │       ├── NotificationSettingsRepository.kt 通知偏好
│       │       ├── FcmTokenRepository.kt           FCM Token 上傳
│       │       ├── Coordinates.kt / SyncResult.kt  共用型別
│       └── res/
│           ├── mipmap-*/                    桌面圖示（含六等級表情）
│           ├── drawable/ · values/ · xml/
├── design/icons/                    圖示母檔與 Node 產生腳本
├── gradle/libs.versions.toml        版本目錄
├── local.properties                 API 金鑰與簽章設定（不入版控）
└── build.gradle.kts / settings.gradle.kts
```

---

## 推播測試

1. 啟動 App（自動上傳 FCM Token 給雲端後端）
2. 開啟 **https://airquality-api-968727437042.asia-east1.run.app/docs**
3. 找 `POST /api/fcm/push`，填入 `X-Admin-Token` 與縣市／訊息即可測試

---

## 資料與隱私

- 健康檔案（年齡層、氣喘、心血管等）**僅儲存於裝置本機**（SharedPreferences），
  不上傳伺服器；並已排除於系統備份之外
- App 無帳號系統，以**裝置匿名憑證**識別；伺服器不儲存原始 `ANDROID_ID`
- 民眾回報在通知中心顯示【已證實】／【未證實】標籤
  （後端 LLM 審核 + 多源佐證），並附免責聲明
- 隱私權政策：[AirQuality-privacy-policy](https://github.com/yu-peihsuan/AirQuality-privacy-policy)
