# AirQuality App — Android Frontend

台灣空氣品質即時監測 App 的 Android 前端，使用 **Kotlin + Jetpack Compose** 開發。

- 套件名稱（applicationId）：`com.peihsuan.airquality`（程式碼 namespace 為 `com.example.airquality`，兩者不同屬正常設定）
- 後端：已部署於 Google Cloud Run（Firebase 專案 `airquality-4d1b6`），App 開箱即用，無需本機伺服器
- 特色功能：**動態桌面圖示** — 雲寶吉祥物依即時 AQI 等級自動變臉（六種表情）

---

## 系統需求

| 項目 | 版本 |
|------|------|
| Android Studio | Hedgehog 以上 |
| Kotlin | 1.9+ |
| minSdk | 24（Android 7.0）|
| targetSdk | 36 |
| compileSdk | 36 |

---

## 專案設定（第一次執行必做）

### 1. 設定 Google Maps API Key

在專案根目錄新增 `local.properties`（已被 `.gitignore` 排除，不會上傳）：

```properties
MAPS_API_KEY=你的_Google_Maps_API_Key
```

### 2. 設定 Firebase

`app/google-services.json` 已包含在專案中，Firebase Cloud Messaging 推播功能直接可用。

### 3. 後端連線設定

後端 API 位址在 [AirQualityApi.kt](app/src/main/java/com/example/airquality/AirQualityApi.kt)，**預設連向雲端正式後端**：

```kotlin
private const val BASE_URL = "https://airquality-api-968727437042.asia-east1.run.app/"
```

> 需要連本機後端開發測試時，暫時改回 `http://10.0.2.2:8000/`（模擬器連本機的固定 IP），並在 `AndroidManifest.xml` 的 `<application>` 暫時加回 `android:usesCleartextTraffic="true"`。**測完記得改回來，不要 commit。**

### 4. Android Studio Run 設定（重要）

本 App 使用 activity-alias 實作動態圖示，Run 按鈕需指定啟動 Activity 才不會報錯：
**Run → Edit Configurations → app → Launch Options → Launch = Specified Activity → `com.example.airquality.MainActivity`**

---

## 功能頁面

| 頁面 | 檔案 | 說明 |
|------|------|------|
| 首頁 | `HomeScreen.kt` | 顯示當前 AQI、空氣品質臉、行動建議按鈕 |
| 通知中心 | `NotificationScreen.kt` | 火災警示、民眾回報、空品警報、預報異動、近期新聞 |
| 回報 | `ReportScreen.kt` | 民眾提交污染回報、查看歷史回報 |
| AI 健康顧問 | `AiHealthScreen.kt` | RAG 個人化健康建議，整合天氣與預報資訊 |
| 熱點地圖 | `MapScreen.kt` | GIS 污染熱點、民生示警火災圖層 |
| 設定 | `SettingsScreen.kt` | 健康檔案設定（年齡、氣喘、心血管等） |

---

## 架構

```
MVVM 架構
├── View       → Composable UI（*Screen.kt）
├── ViewModel  → 狀態管理（*ViewModel.kt）
└── Model      → API 資料層（AirQualityApi.kt）

網路層
└── Retrofit + Gson（AirQualityApi.kt / RetrofitClient）

推播
└── Firebase Cloud Messaging（MyFirebaseMessagingService.kt）

動態桌面圖示
├── AppIconManager.kt          → AQI 等級 → activity-alias 切換邏輯
├── AndroidManifest.xml        → 7 個 activity-alias（預設 + 六等級表情）
└── design/icons/              → 雲寶圖示母檔與產生腳本（見該目錄 README）
```

---

## 動態桌面圖示（雲寶變臉）

首頁取得 AQI 後，`AppIconManager` 依環境部六級指標啟用對應的 activity-alias，
桌面圖示自動切換為對應表情（良好=笑臉、不健康=戴口罩、危害=昏倒…）。
僅於「等級改變」時切換，避免圖示頻繁閃動。改圖示設計請見 `design/icons/README.md`。

---

## 主要套件

| 套件 | 用途 |
|------|------|
| Jetpack Compose | UI 框架 |
| Retrofit 2 + Gson | HTTP API 呼叫與 JSON 解析 |
| ViewModel Compose | 頁面狀態管理 |
| Google Play Services Location | GPS 定位 |
| Firebase Messaging | FCM 推播通知 |
| Google Maps + Maps Compose | 熱點地圖顯示 |

---

## 通知中心分類

| 類型 | 顏色 | 來源 |
|------|------|------|
| 🔥 火災警示 | 深紅 | 民生示警平台（NCDR）|
| 👤 民眾回報 | 橘色 | 使用者回報 |
| 🔴 空氣品質警報 | 紅色 | 環境部 AQI（≥ 151 才顯示）|
| 📅 空品預報警示 | 藍色 | 環境部 AQF_P_01（僅顯示與當前狀態不同的預報）|
| 📰 近期新聞 | 灰色 | 爬蟲新聞（24 小時內）|

---

## 建置與執行

1. 用 Android Studio 開啟 `AirQualityApp/` 資料夾
2. 確認 `local.properties` 已設定 `MAPS_API_KEY`
3. 選擇模擬器（需含 **Google Play**）或實體裝置
4. 點擊 Run ▶（後端在雲端 24 小時運行，不需啟動任何本機服務）

---

## 推播測試

1. 啟動 App（自動上傳 FCM Token 給雲端後端）
2. 開啟瀏覽器打 **https://airquality-api-968727437042.asia-east1.run.app/docs**
3. 找 `POST /api/fcm/push`，填入縣市與訊息測試推播

---

## 資料與隱私

- 健康檔案（年齡層、氣喘、心血管等）**僅儲存於裝置本機**（SharedPreferences），不上傳伺服器
- 民眾回報在通知中心顯示【已證實】/【未證實】標籤（後端 LLM 審核 + 多源佐證），並附免責聲明
