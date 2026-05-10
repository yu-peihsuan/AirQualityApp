# AirQuality App — Android Frontend

台灣空氣品質即時監測 App 的 Android 前端，使用 **Kotlin + Jetpack Compose** 開發。

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

後端 API 位址在 [AirQualityApi.kt](app/src/main/java/com/example/airquality/AirQualityApi.kt)：

```kotlin
private const val BASE_URL = "http://10.0.2.2:8000/"
```

> `10.0.2.2` 是 Android 模擬器連到本機 `localhost` 的固定 IP。實體裝置需改為電腦的區域網路 IP。

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
```

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
3. 確認後端已啟動（`http://localhost:8000`）
4. 選擇模擬器（需含 **Google Play**）或實體裝置
5. 點擊 Run ▶

---

## 推播測試

1. 在模擬器上啟動 App（自動上傳 FCM Token 給後端）
2. 後端 terminal 確認印出 `✅ FCM Token 已註冊`
3. 開啟瀏覽器打 `http://localhost:8000/docs`
4. 找 `POST /api/fcm/push`，填入縣市與訊息測試推播
