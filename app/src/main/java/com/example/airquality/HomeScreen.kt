package com.example.airquality

import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.airquality.ui.theme.*
import java.util.Calendar

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(),
    // 與設定頁共用同一個 Activity 範圍的實例，所以彈窗按下同意後，
    // 設定頁那個開關會直接是開啟狀態，不需要額外同步
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current

    // 監聽來自 ViewModel 的狀態變化
    val uiState             by viewModel.uiState.collectAsState()
    val isRaining           by viewModel.isRaining.collectAsState()
    val currentLocationName by viewModel.currentLocationName.collectAsState()
    val favorites           by viewModel.favorites.collectAsState()
    val shouldAskConsent    by settingsViewModel.shouldAskSensitiveAlertsConsent.collectAsState()
    // 同意彈窗要等系統權限對話框走完才跳，否則首次啟動會變成三個視窗疊在一起
    var permissionFlowDone  by remember { mutableStateOf(false) }
    val conditions          by viewModel.healthConditions.collectAsState()
    var showLocationDialog  by remember { mutableStateOf(false) }
    // 首頁直接新增常用地點（不必進設定頁）
    var showAddLocationDialog by remember { mutableStateOf(false) }
    var addLocName    by remember { mutableStateOf("") }
    var addLocAddress by remember { mutableStateOf("") }
    val lifecycleOwner = LocalLifecycleOwner.current

    // 管理當前顯示的日期，讓它可以在回到畫面時更新
    var currentDateString by remember { mutableStateOf(getCurrentDateString()) }

    // 權限請求 launcher：權限流程結束後，一律依「持久化的選擇」載入——
    // 手動選過地區就用該地區（與定位權限無關）；GPS 模式才看定位權限，
    // 無權限由 HomeViewModel.switchToGps 內部退預設地區。避免像過去寫死台北市而蓋掉使用者選擇。
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.loadInitialLocation()
        permissionFlowDone = true

        // 未授權定位、也沒選過任何地區 → 引導使用者必須自行選擇一個常用地點
        if (!viewModel.hasLocationPermission() && !viewModel.hasSavedChoice()) {
            // Toast 於 Android 12+ 僅顯示兩行，文字須精簡避免被截斷
            Toast.makeText(context, "未開啟定位，請選擇常用地點", Toast.LENGTH_LONG).show()
            showLocationDialog = true
        }
    }

    // 使用者在切換地點對話框主動選「GPS 定位」但尚未授權定位時使用：
    // 允許 → 切換至 GPS；拒絕 → 維持原本地區並提示
    val gpsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (viewModel.hasLocationPermission()) {
            viewModel.switchToGps()
        } else {
            // Toast 於 Android 12+ 僅顯示兩行，文字須精簡避免被截斷
            Toast.makeText(context, "未取得定位權限，請至系統設定開啟", Toast.LENGTH_LONG).show()
        }
    }

    // 第一次進入 App：一次要求尚未授權的定位與通知權限；
    // 若全部已授權則直接載入（單一初始載入決策點，避免兩條路互相覆蓋）
    LaunchedEffect(Unit) {
        val perms = mutableListOf<String>()
        if (!viewModel.hasLocationPermission()) {
            perms += Manifest.permission.ACCESS_FINE_LOCATION
            perms += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        // Android 13（TIRAMISU）以上，通知需執行時授權，否則所有推播（含每日通知）會被系統丟棄
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) {
            perms += Manifest.permission.POST_NOTIFICATIONS
        }
        if (perms.isNotEmpty()) {
            permissionLauncher.launch(perms.toTypedArray())   // 載入交由上方回呼處理
        } else {
            viewModel.loadInitialLocation()
            permissionFlowDone = true
        }
    }

    // 收集 ViewModel 的一次性提示（如地址搜尋失敗），以 Toast 顯示
    LaunchedEffect(Unit) {
        viewModel.userMessage.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    // 當 lifecycle 狀態改變（回到前景 ON_RESUME）時更新日期並重新抓取空氣品質資料。
    // 初次載入由 viewModel.loadInitialLocation() 依「持久化的選擇」處理；addObserver 會立即補發
    // 一次 ON_RESUME，若不跳過會以預設 GPS 模式搶先執行、蓋掉持久化選擇，故跳過第一次。
    var skipFirstResume by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                currentDateString = getCurrentDateString()
                if (skipFirstResume) {
                    skipFirstResume = false
                } else {
                    viewModel.refreshCurrentLocation()
                }
                // 使用者可能剛在設定頁改過健康檔案或常用地點
                viewModel.reloadHealthProfile()
                viewModel.reloadFavorites()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        // 初始載入由上方 LaunchedEffect／權限回呼統一處理（單一決策點）
        currentDateString = getCurrentDateString()

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgMain)
    ) {
        val locationText = if (uiState is AqiUiState.Success) {
            val nearest = (uiState as AqiUiState.Success).nearestRecord
            "鄰近測站: ${nearest.sitename}"
        } else {
            "鄰近測站: 搜尋中..."
        }

        HomeAppHeader(
            location = locationText,
            date = currentDateString,
            onLocationSwitchClick = { showLocationDialog = true }
        )

        // ── 首次啟動：健康資料用於推播分眾的同意詢問 ────────────────────
        // 未取得同意前，FcmTokenRepository 送出的 conditions 一律是空字串，
        // 沒有任何健康資料會離開裝置（見隱私權政策第二節）。
        if (permissionFlowDone && shouldAskConsent && !showLocationDialog) {
            SensitiveAlertsConsentDialog(
                onDecision = { settingsViewModel.onSensitiveAlertsConsent(it) }
            )
        }

        // ── 地點切換 Dialog ──────────────────────────────────────────────
        if (showLocationDialog) {
            val favLocations = favorites.filter {
                it.name.isNotEmpty() && it.address.isNotEmpty()
            }

            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showLocationDialog = false },
                containerColor = BgMain,
                title = {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("切換地點", fontWeight = FontWeight.Bold, color = TextDark)
                        androidx.compose.material3.TextButton(onClick = {
                            addLocName = ""
                            addLocAddress = ""
                            showAddLocationDialog = true
                        }) {
                            Text("＋ 新增", color = OrangeMain, fontSize = 14.sp)
                        }
                    }
                },
                text = {
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
                    ) {
                        LocationOption(
                            "GPS 定位", "自動偵測目前位置",
                            selected = currentLocationName == "GPS 定位"
                        ) {
                            showLocationDialog = false
                            if (viewModel.hasLocationPermission()) {
                                viewModel.switchToGps()
                            } else {
                                // 尚未授權定位 → 跳出系統權限詢問（結果由 gpsPermissionLauncher 處理）
                                gpsPermissionLauncher.launch(arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ))
                            }
                        }
                        favLocations.forEach { (name, address) ->
                            LocationOption(
                                name, address,
                                selected = currentLocationName == name
                            ) {
                                showLocationDialog = false
                                viewModel.switchToSavedLocation(name, address)
                            }
                        }
                        if (favLocations.isEmpty()) {
                            Text("尚未設定常用地點，點右上「＋ 新增」即可加入。",
                                color = TextGray, fontSize = 13.sp,
                                modifier = androidx.compose.ui.Modifier.padding(top = 8.dp))
                        }
                    }
                },
                confirmButton = {}
            )
        }

        // ── 新增常用地點 Dialog（首頁直接新增，儲存後立即切換過去）──────
        if (showAddLocationDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showAddLocationDialog = false },
                containerColor = BgMain,
                title = { Text("新增常用地點", fontWeight = FontWeight.Bold, color = TextDark) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.OutlinedTextField(
                            value = addLocName,
                            onValueChange = { addLocName = it },
                            label = { Text("名稱") },
                            placeholder = { Text("例如：家、公司", color = TextGray, fontSize = 13.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                        androidx.compose.material3.OutlinedTextField(
                            value = addLocAddress,
                            onValueChange = { addLocAddress = it },
                            label = { Text("地址") },
                            placeholder = { Text("例如：台北市中正區重慶南路一段122號", color = TextGray, fontSize = 13.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        val n = addLocName.trim()
                        val a = addLocAddress.trim()
                        if (n.isNotEmpty() && a.isNotEmpty()) {
                            // 常用地點與設定頁共用同一份儲存（附加到清單尾端）
                            showAddLocationDialog = false
                            showLocationDialog = false
                            viewModel.addFavoriteAndSwitch(n, a)
                        } else {
                            Toast.makeText(context, "請填寫名稱與地址", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("儲存並切換", color = OrangeMain) }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showAddLocationDialog = false }) {
                        Text("取消", color = TextGray)
                    }
                }
            )
        }

        // ── 主內容 ─────────────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f) // 讓主內容佔據剩餘的高度
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center, // 內容靠中間集中
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 根據 API 連線狀態顯示不同畫面
            when (uiState) {
                is AqiUiState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = OrangeMain)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("正在取得空氣品質資料...", color = TextGray, textAlign = TextAlign.Center)
                    }
                }
                
                is AqiUiState.Error -> {
                    Text(
                        text = (uiState as AqiUiState.Error).message, 
                        color = Color.Red,
                        textAlign = TextAlign.Center
                    )
                }
                
                is AqiUiState.Success -> {
                    val successState = uiState as AqiUiState.Success
                    val nearestRecord = successState.nearestRecord

                    // 記錄最新 AQI 對應圖示；實際切換延到 App 退背景時（見 MainActivity.onStop），
                    // 避免前景切換 launcher alias 把使用者踢回桌面
                    LaunchedEffect(nearestRecord.aqi) {
                        AppIconManager.recordAqi(context, nearestRecord.aqi.toIntOrNull())
                    }

                    if (successState.isFromCache) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFFF3CD), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "⚠ 網路連線異常，顯示暫存資料",
                                fontSize = 12.sp,
                                color = Color(0xFF856404)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    val aqiValue = nearestRecord.aqi
                    val aqiStatus = nearestRecord.status
                    val aqiColor = getAqiColor(aqiStatus)
                    val displayStatus = when (aqiStatus) {
                        "對敏感族群不健康", "對所有族群不健康" -> "不健康"
                        else -> aqiStatus
                    }
                    val pm25 = nearestRecord.pm25
                    val sitename = nearestRecord.sitename
                    
                    // ── 空氣品質標題 ──────────────────────────────────
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            buildAnnotatedString {
                                append("空氣")
                                withStyle(SpanStyle(color = aqiColor)) { append(displayStatus) }
                            },
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                        // 顯示更新時間
                        val publishtime = nearestRecord.publishtime ?: ""
                        val updateTimeStr = if (publishtime.contains(" ")) {
                            val timePart = publishtime.substringAfter(" ")
                            val timeParts = timePart.split(":")
                            if (timeParts.size >= 2) "${timeParts[0]}:${timeParts[1]}" else "12:00"
                        } else {
                            "12:00"
                        }
                        Text("更新時間 $updateTimeStr", color = TextGray, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp))
                    }

                    Spacer(Modifier.height(40.dp)) // 增加文字與臉的間距

                    // ── 臉 + AQI 標籤 ───────────────────────────────
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        val aqiInt = aqiValue.toIntOrNull() ?: 0
                        AqiFace(aqiInt)

                        Spacer(Modifier.height(18.dp))

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(aqiColor.copy(alpha = 0.15f))
                                .border(1.dp, aqiColor.copy(alpha = 0.5f), RoundedCornerShape(50))
                                .padding(horizontal = 30.dp, vertical = 14.dp) // AQI標籤加大
                        ) {
                            Text("AQI $aqiValue $displayStatus", color = aqiColor, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) // 文字再加大
                        }
                    }

                    Spacer(Modifier.height(50.dp)) // 增加臉與下方按鈕的間距

                    // ── 行動按鈕（依 AQI 等級與健康檔案隨機抽 3 個）────
                    val currentAqiInt = aqiValue.toIntOrNull() ?: 0
                    val hasAsthma        = "氣喘" in conditions
                    val hasCardiovascular = "心血管疾病" in conditions
                    val aqiBand = when {
                        currentAqiInt <= 50  -> 0
                        currentAqiInt <= 100 -> 1
                        currentAqiInt <= 150 -> 2
                        else                 -> 3
                    }
                    val chips = remember(aqiBand, hasAsthma, hasCardiovascular, isRaining) {
                        getActionChips(aqiBand, hasAsthma, hasCardiovascular, isRaining)
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        chips.forEach { (icon, label) ->
                            ActionChip(icon, label, aqiColor, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}


// ── 臉 (Canvas 繪製) 部分完整修改 ──────────────────────────────────────────

@Composable
fun AqiFace(aqiValue: Int) {
    Box(
        modifier = Modifier
            .size(180.dp)
            .clip(CircleShape)
            .background(FaceBg),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 將 AQI 數值傳遞給繪製函式
            drawDynamicFace(aqiValue)
        }
    }
}

fun DrawScope.drawDynamicFace(aqiValue: Int) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    // 臉部主要半徑基準
    val r  = minOf(cx, cy) * 0.82f
    
    // 顏色定義
    val browColor   = Color(0xFF5C3A1E)
    val eyeColor    = Color(0xFF3D2210)
    val maskColor   = Color(0xFFF8F8F8)
    val stripeColor = Color(0xFFDDDDDD)
    
    // 筆觸寬度基準
    val bw = r * 0.065f

    // ── 計算特徵坐標 (根據 R 基準) ──
    
    // 眼睛坐標
    val eyeY = cy - r * 0.08f
    val lEyeCx = cx - r * 0.33f
    val rEyeCx = cx + r * 0.33f
    val eyeSize = r * 0.085f

    // 眉毛坐標基準
    val browYBase = cy - r * 0.36f // 眉毛的平均高度
    val browHalfW = r * 0.23f      // 眉毛單邊長度的一半
    val lBrowCx = cx - r * 0.35f  // 左眉中心 X
    val rBrowCx = cx + r * 0.35f  // 右眉中心 X
    val browOffset = r * 0.08f // 眉毛高低差

    // ── 1. 畫眉毛 (根據 AQI 改變形狀) ──
    when {
        // 良好/普通 (<=100): 平眉 (Flat)
        aqiValue <= 100 -> {
            drawLine(browColor, Offset(lBrowCx - browHalfW, browYBase), Offset(lBrowCx + browHalfW, browYBase), bw, StrokeCap.Round)
            drawLine(browColor, Offset(rBrowCx - browHalfW, browYBase), Offset(rBrowCx + browHalfW, browYBase), bw, StrokeCap.Round)
        }
        // 敏感不佳 (101-150): 八字眉/憂慮 (Worried: 外高內低)
        aqiValue <= 150 -> {
            drawLine(browColor, Offset(lBrowCx - browHalfW, browYBase - browOffset), Offset(lBrowCx + browHalfW, browYBase + browOffset), bw, StrokeCap.Round)
            drawLine(browColor, Offset(rBrowCx - browHalfW, browYBase + browOffset), Offset(rBrowCx + browHalfW, browYBase - browOffset), bw, StrokeCap.Round)
        }
        // 不良以上 (>150): 皺眉/生氣 (Frown: 內高外低 - 原本的樣子)
        else -> {
            drawLine(browColor, Offset(lBrowCx - browHalfW, browYBase + browOffset), Offset(lBrowCx + browHalfW, browYBase - browOffset), bw, StrokeCap.Round)
            drawLine(browColor, Offset(rBrowCx - browHalfW, browYBase - browOffset), Offset(rBrowCx + browHalfW, browYBase + browOffset), bw, StrokeCap.Round)
        }
    }

    // ── 2. 畫眼睛 (根據 AQI 改變形狀) ──
    if (aqiValue > 300) {
        // 有害 (>300): X X 眼 (使用四條線組成)
        val xSize = eyeSize * 1.2f // X 比圓眼稍微大一點
        // Left X
        drawLine(eyeColor, Offset(lEyeCx - xSize, eyeY - xSize), Offset(lEyeCx + xSize, eyeY + xSize), bw * 0.8f, StrokeCap.Round)
        drawLine(eyeColor, Offset(lEyeCx - xSize, eyeY + xSize), Offset(lEyeCx + xSize, eyeY - xSize), bw * 0.8f, StrokeCap.Round)
        // Right X
        drawLine(eyeColor, Offset(rEyeCx - xSize, eyeY - xSize), Offset(rEyeCx + xSize, eyeY + xSize), bw * 0.8f, StrokeCap.Round)
        drawLine(eyeColor, Offset(rEyeCx - xSize, eyeY + xSize), Offset(rEyeCx + xSize, eyeY - xSize), bw * 0.8f, StrokeCap.Round)
    } else {
        // 其他 (<=300): 圓眼 (原本的樣子)
        drawCircle(eyeColor, eyeSize, Offset(lEyeCx, eyeY))
        drawCircle(eyeColor, eyeSize, Offset(rEyeCx, eyeY))
    }

    // ── 3. 畫口罩 或 嘴巴 (根據 AQI 決定) ──
    if (aqiValue > 100) {
        // 空氣不佳 (>100): 戴口罩 (原本的繪製邏輯)
        
        val maskLeft = cx - r * 0.72f
        val maskTop  = cy + r * 0.08f
        val maskW    = r * 1.44f
        val maskH    = r * 0.70f
        drawRoundRect(maskColor, Offset(maskLeft, maskTop), Size(maskW, maskH), CornerRadius(r * 0.13f))

        // 口罩橫線
        val sx = maskLeft + maskW * 0.08f
        val sw = maskW * 0.84f
        val st = r * 0.028f
        drawLine(stripeColor, Offset(sx, maskTop + maskH * 0.38f), Offset(sx + sw, maskTop + maskH * 0.38f), st)
        drawLine(stripeColor, Offset(sx + maskW * 0.05f, maskTop + maskH * 0.68f), Offset(sx + sw - maskW * 0.05f, maskTop + maskH * 0.68f), st)
        
    } else {
        // 空氣尚可 (<=100): 不戴口罩，畫嘴巴
        
        val mouthY = cy + r * 0.3f
        val mouthW = r * 0.35f
        
        if (aqiValue <= 50) {
            // 良好 (0-50): 開心微笑 (使用圓弧)
            drawArc(
                color = eyeColor,
                startAngle = 0f,    // 從 3 點鐘方向開始
                sweepAngle = 180f,  // 掃掠 180 度 (下半圓)
                useCenter = false,
                topLeft = Offset(cx - mouthW, mouthY - mouthW * 0.5f),
                size = Size(mouthW * 2f, mouthW), // 扁平的圓弧
                style = Stroke(width = bw, cap = StrokeCap.Round)
            )
        } else {
            // 普通 (51-100): 平淡 (一條直線)
            val lineMouthW = mouthW * 0.8f
            drawLine(
                color = eyeColor,
                start = Offset(cx - lineMouthW, mouthY + r*0.1f),
                end = Offset(cx + lineMouthW, mouthY + r*0.1f),
                strokeWidth = bw,
                cap = StrokeCap.Round
            )
        }
    }
}

// ── 行動按鈕卡片 ─────────────────────────────────────────────────────────

@Composable
fun ActionChip(iconRes: Int, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        // 寬度由父層 weight 均分、高度設最小值——不同螢幕寬度與系統字體
        // 放大時能自動適應，文字過長會換行而不是被截掉
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.15f)) // 背景依 AQI 顏色透明化
            .clickable {}
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(20.dp)) // 外框依 AQI 顏色
            .defaultMinSize(minHeight = 130.dp)
            .padding(horizontal = 4.dp, vertical = 10.dp)
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = iconRes),
            contentDescription = label,
            modifier = Modifier.size(42.dp),
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.Black) // 圖片固定用黑色
        )
        Spacer(Modifier.height(12.dp))
        Text(label, color = Color(0xFF666666), fontSize = 18.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    }
}

// ── 地點選項 ──────────────────────────────────────────────────────────────────

@Composable
private fun LocationOption(
    name: String,
    subtitle: String,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) OrangeLight else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                color = if (selected) OrangeMain else TextDark)
            Text(
                subtitle, fontSize = 12.sp, color = TextGray,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        if (selected) {
            Spacer(Modifier.width(6.dp))
            Text("✓ 目前", fontSize = 12.sp, color = OrangeMain,
                fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

// ── 行動建議資料類別 ──────────────────────────────────────────────────────────

private data class ChipItem(val icon: Int, val label: String, val category: String = label)

private fun getActionChips(
    aqiBand: Int,
    hasAsthma: Boolean,
    hasCardiovascular: Boolean,
    isRaining: Boolean = false
): List<ChipItem> {
    val pool = mutableListOf<ChipItem>()
    when (aqiBand) {
        0 -> {
            if (isRaining) {
                pool += listOf(
                    ChipItem(R.drawable.excercise_inside, "室內運動",  "indoor_exercise"),
                    ChipItem(R.drawable.yoga,             "瑜珈伸展",  "indoor_exercise"),
                    ChipItem(R.drawable.open_window,      "開窗通風",  "ventilation"),
                )
            } else {
                pool += listOf(
                    ChipItem(R.drawable.jogging,         "戶外跑步",  "outdoor_exercise"),
                    ChipItem(R.drawable.walking_outside, "戶外散步",  "outdoor_exercise"),
                    ChipItem(R.drawable.bicycle,         "騎腳踏車",  "cycling"),
                    ChipItem(R.drawable.open_window,     "開窗通風",  "ventilation"),
                )
            }
        }
        1 -> {
            if (isRaining) {
                pool += listOf(
                    ChipItem(R.drawable.excercise_inside, "室內運動",  "indoor_exercise"),
                    ChipItem(R.drawable.yoga,             "瑜珈伸展",  "indoor_exercise"),
                    ChipItem(R.drawable.drink_water,      "補充水分",  "hydration"),
                    ChipItem(R.drawable.air_purifier,     "空氣清淨機","air_purifier"),
                )
            } else {
                pool += listOf(
                    ChipItem(R.drawable.walking_outside, "戶外散步",  "outdoor_exercise"),
                    ChipItem(R.drawable.open_window,     "開窗通風",  "ventilation"),
                    ChipItem(R.drawable.drink_water,     "補充水分",  "hydration"),
                    ChipItem(R.drawable.air_purifier,    "空氣清淨機","air_purifier"),
                )
            }
        }
        2 -> {
            pool += listOf(
                ChipItem(R.drawable.mask,             "戴口罩",    "mask"),
                ChipItem(R.drawable.window,           "關閉門窗",  "close_window"),
                ChipItem(R.drawable.stay_at_home,     "減少外出",  "stay_inside"),
                ChipItem(R.drawable.drink_water,      "補充水分",  "hydration"),
                ChipItem(R.drawable.excercise_inside, "室內運動",  "indoor_exercise"),
                ChipItem(R.drawable.yoga,             "瑜珈伸展",  "indoor_exercise"),
                ChipItem(R.drawable.public_transport, "搭大眾運輸","transport"),
                ChipItem(R.drawable.air_purifier,     "空氣清淨機","air_purifier"),
            )
            if (hasAsthma)         pool += ChipItem(R.drawable.inhaler, "備妥吸入器", "inhaler")
            if (hasCardiovascular) pool += ChipItem(R.drawable.medicine, "備妥藥物",  "medicine")
        }
        else -> {
            pool += listOf(
                ChipItem(R.drawable.mask,             "戴口罩",    "mask"),
                ChipItem(R.drawable.window,           "關閉門窗",  "close_window"),
                ChipItem(R.drawable.air_purifier,     "空氣清淨機","air_purifier"),
                ChipItem(R.drawable.home,             "盡量待室內","stay_inside"),
                ChipItem(R.drawable.drink_water,      "多補充水分","hydration"),
                ChipItem(R.drawable.public_transport, "搭大眾運輸","transport"),
            )
            if (hasAsthma)         pool += ChipItem(R.drawable.inhaler, "備妥吸入器", "inhaler")
            if (hasCardiovascular) pool += ChipItem(R.drawable.medicine, "備妥藥物",  "medicine")
        }
    }
    return pool.shuffled().distinctBy { it.category }.take(3)
}

//取得當下時間
fun getCurrentDateString(): String {
    val calendar = Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Taipei"))
    val month = calendar.get(Calendar.MONTH) + 1
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    val dayOfWeek = when (calendar.get(Calendar.DAY_OF_WEEK)) {
        Calendar.SUNDAY -> "週日"
        Calendar.MONDAY -> "週一"
        Calendar.TUESDAY -> "週二"
        Calendar.WEDNESDAY -> "週三"
        Calendar.THURSDAY -> "週四"
        Calendar.FRIDAY -> "週五"
        Calendar.SATURDAY -> "週六"
        else -> ""
    }
    return "${month}月${day}日 $dayOfWeek"
}
