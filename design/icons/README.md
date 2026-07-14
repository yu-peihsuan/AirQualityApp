# 雲寶 App 圖示設計檔

App 的動態桌面圖示（隨 AQI 等級變臉）設計母檔與產生腳本。

## 資料夾內容

- `masters/` — 7 張 512×512 母檔 PNG
  - `default_icon.png`：預設／品牌圖示（Play 商店 512 圖示直接用這張）
  - `AQI0` ~ `AQI301`：六個 AQI 等級的雲寶表情
- `build_icons.js` — 由 SVG 定義重新產生 `masters/` 的母檔（改設計時編輯此檔）
- `gen_android_res.js` — 把母檔轉成 `app/src/main/res/` 的全部 Android 資源
  （每組：5 密度方形圖 + 5 密度自適應前景 + 漸層背景 XML + adaptive-icon XML）

## 重新產生圖示

```bash
cd design/icons
npm install sharp   # 第一次需要
node build_icons.js      # 1. 重生母檔
node gen_android_res.js  # 2. 重生 Android 資源
```

## 相關程式

- 圖示切換邏輯：`app/src/main/java/com/example/airquality/AppIconManager.kt`
- alias 宣告：`app/src/main/AndroidManifest.xml`（7 個 activity-alias）
- 觸發點：`HomeScreen.kt` 取得 AQI 後呼叫 `AppIconManager.update()`
