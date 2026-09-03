package com.example.airquality

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.airquality.data.FavoriteLocation
import com.example.airquality.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    // 由首次同意流程帶進來：進場時直接展開健康檔案對話框
    openHealthProfile: Boolean = false,
    onHealthProfileOpened: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current

    // ── 通知設定 state ────────────────────────────────────────────────────
    val dailyNotificationEnabled by viewModel.dailyEnabled.collectAsState()
    val dailyHour   by viewModel.dailyHour.collectAsState()
    val dailyMinute by viewModel.dailyMinute.collectAsState()
    val sensitiveAlertsEnabled by viewModel.sensitiveAlertsEnabled.collectAsState()
    var showTimePicker by remember { mutableStateOf(false) }

    // ── 健康檔案 state（編輯中的暫存值，按下儲存才寫回）────────────────────
    val savedAgeGroup   by viewModel.ageGroup.collectAsState()
    val savedConditions by viewModel.conditions.collectAsState()
    val savedOtherNotes by viewModel.otherNotes.collectAsState()

    var selectedAgeGroup by remember(savedAgeGroup) { mutableStateOf(savedAgeGroup) }
    var otherText        by remember(savedOtherNotes) { mutableStateOf(savedOtherNotes) }
    val selectedConditions = remember(savedConditions) {
        mutableStateListOf<String>().also { it.addAll(savedConditions) }
    }
    var showHealthDialog by remember { mutableStateOf(false) }

    // 從同意彈窗導過來時自動展開健康檔案。回報一次就把旗標清掉，
    // 否則使用者關掉對話框後切回首頁再回來，它又會自己跳出來。
    LaunchedEffect(openHealthProfile) {
        if (openHealthProfile) {
            showHealthDialog = true
            onHealthProfileOpened()
        }
    }

    // ── 常用地點 state ────────────────────────────────────────────────────────
    val savedFavorites by viewModel.favorites.collectAsState()
    val favLocations = remember(savedFavorites) {
        mutableStateListOf<FavoriteLocation>().also { it.addAll(savedFavorites) }
    }
    var showLocationsDialog by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var editName     by remember { mutableStateOf("") }
    var editAddress  by remember { mutableStateOf("") }

    // ── 使用說明 ──────────────────────────────────────────────────────────
    var showGuideDialog by remember { mutableStateOf(false) }

    // ── Snackbar ──────────────────────────────────────────────────────────
    val snackbarHostState = remember { SnackbarHostState() }

    // 設定寫回後端成功或失敗都要讓使用者知道（原本失敗是靜默的）
    LaunchedEffect(Unit) {
        viewModel.message.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BgMain
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
                .background(BgMain)
        ) {
            // ── Header ────────────────────────────────────────────────────
            AppHeader(title = "設定")

            // ── 主內容（可捲動：小螢幕或字體放大時，下方項目才不會被切掉）──
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                Spacer(Modifier.height(24.dp))

                // ── 通知設定（常用，直接顯示）───────────────────────────
                SettingSection("通知設定") {
                    Text(
                        "開啟後，每天會在你指定的時間收到一次所在地區的空氣品質摘要通知",
                        color = TextGray, fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    SettingSwitchRow(
                        label = "每日空氣品質通知",
                        checked = dailyNotificationEnabled,
                        onCheckedChange = { viewModel.setDailyEnabled(it) }
                    )
                    if (dailyNotificationEnabled) {
                        HorizontalDivider(color = DividerColor, modifier = Modifier.padding(vertical = 2.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { showTimePicker = true }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("通知時間", color = TextDark, fontSize = 16.sp)
                            Text(
                                "%02d:%02d".format(dailyHour, dailyMinute),
                                color = OrangeMain, fontSize = 16.sp, fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { viewModel.sendTestNotification() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OrangeMain)
                ) {
                    Text("🔔 發送測試通知", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }

                Spacer(Modifier.height(20.dp))

                // ── 其他設定：點開才顯示詳細內容 ─────────────────────────
                SettingSection(title = null) {
                    SettingLinkRow("個人健康檔案") { showHealthDialog = true }
                    HorizontalDivider(color = DividerColor)
                    SettingLinkRow("常用地點") { showLocationsDialog = true }
                    HorizontalDivider(color = DividerColor)
                    SettingLinkRow("使用說明") { showGuideDialog = true }
                    HorizontalDivider(color = DividerColor)
                    SettingLinkRow("隱私權政策") {
                        context.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(PRIVACY_URL)
                            )
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }

    // ── 個人健康檔案 彈跳視窗 ───────────────────────────────────────────────
    if (showHealthDialog) {
        HealthProfileDialog(
            sensitiveAlertsEnabled = sensitiveAlertsEnabled,
            onSensitiveAlertsChange = { viewModel.setSensitiveAlertsEnabled(it) },
            selectedAgeGroup   = selectedAgeGroup,
            onAgeGroupChange   = { selectedAgeGroup = it },
            selectedConditions = selectedConditions,
            otherText          = otherText,
            onOtherTextChange  = { otherText = it },
            onDismiss          = { showHealthDialog = false },
            onSave = {
                viewModel.saveHealthProfile(
                    selectedAgeGroup, selectedConditions.toList(), otherText
                )
                showHealthDialog = false
            }
        )
    }

    // ── 常用地點 彈跳視窗 ─────────────────────────────────────────────────
    if (showLocationsDialog) {
        LocationsDialog(
            favLocations = favLocations,
            onDismiss = { showLocationsDialog = false },
            onAddClick = {
                editingIndex = favLocations.size
                editName    = ""
                editAddress = ""
            },
            onItemClick = { idx ->
                editingIndex = idx
                editName    = favLocations[idx].name
                editAddress = favLocations[idx].address
            }
        )
    }

    // ── 常用地點新增／編輯 彈跳視窗 ──────────────────────────────────────
    if (editingIndex != null) {
        val isNew = editingIndex == favLocations.size
        AlertDialog(
            onDismissRequest = { editingIndex = null },
            containerColor = BgMain,
            title = {
                Text(if (isNew) "新增常用地點" else "編輯地點",
                    fontWeight = FontWeight.Bold, color = TextDark)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("名稱") },
                        placeholder = { Text("例如：家、公司、健身房", color = TextGray, fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = healthTextFieldColors(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = editAddress,
                        onValueChange = { editAddress = it },
                        label = { Text("地址") },
                        placeholder = { Text("例如：台北市中正區重慶南路", color = TextGray, fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = healthTextFieldColors(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val i = editingIndex ?: return@TextButton
                    val newFav = FavoriteLocation(editName.trim(), editAddress.trim())
                    if (isNew) favLocations.add(newFav)
                    else favLocations[i] = newFav
                    viewModel.saveFavorites(favLocations.toList())
                    editingIndex = null
                }) { Text("儲存", color = OrangeMain) }
            },
            dismissButton = {
                Row {
                    if (!isNew) {
                        TextButton(onClick = {
                            val i = editingIndex ?: return@TextButton
                            favLocations.removeAt(i)
                            viewModel.saveFavorites(favLocations.toList())
                            editingIndex = null
                        }) { Text("刪除", color = RedText) }
                    }
                    TextButton(onClick = { editingIndex = null }) {
                        Text("取消", color = TextGray)
                    }
                }
            }
        )
    }

    if (showTimePicker) {
        DailyNotificationTimePickerDialog(
            initialHour = dailyHour,
            initialMinute = dailyMinute,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                viewModel.setDailyTime(hour, minute)
                showTimePicker = false
            }
        )
    }

    // ── 使用說明 彈跳視窗 ──────────────────────────────────────────────────
    if (showGuideDialog) {
        InfoDialog("使用說明", USER_GUIDE_TEXT) { showGuideDialog = false }
    }
}

// ── 可捲動說明對話框（使用說明／隱私權政策共用）──────────────────────────────────
@Composable
private fun InfoDialog(title: String, content: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgMain,
        title = { Text(title, fontWeight = FontWeight.Bold, color = TextDark) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(content, color = TextDark, fontSize = 13.sp, lineHeight = 21.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("關閉", color = OrangeMain) }
        }
    )
}

private const val USER_GUIDE_TEXT =
    "本App提供台灣即時空氣品質資訊與個人化健康防護，主要功能如下：\n\n" +
    "1. 首頁\n" +
    "- 顯示所在地區最近測站的即時 AQI 與空氣品質等級。\n" +
    "- 點右上角圖示可切換「GPS 定位」或你設定的常用地點。\n\n" +
    "2. AI 顧問\n" +
    "- 依你的健康檔案與當前空品，提供個人化健康建議。\n\n" +
    "3. 通報（中間橘色按鈕）\n" +
    "- 發現火災、異味、揚塵等空污事件，可填寫地點與描述向社群通報。\n\n" +
    "4. 通知中心\n" +
    "- 查看空品預報、警報，以及附近的民眾回報。\n" +
    "- 點右上角地圖圖示，可看官方火災警示與回報熱點地圖。\n\n" +
    "5. 設定\n" +
    "- 開關每日空氣品質通知並設定推播時間。\n" +

    "- 編輯個人健康檔案（預設僅儲存於本機），並可決定是否讓伺服器依你的健康狀況優先發送警示。\n" +
    "- 新增常用地點，方便快速切換查詢。"

// 隱私權政策完整版網頁（設定頁與首次同意彈窗都會連到這裡）
// 正式站台為 Vercel；GitHub Pages 那份路徑已失效，不要改回去
internal const val PRIVACY_URL =
    "https://air-quality-privacy-policy.vercel.app/"

// ── 個人健康檔案 Dialog ──────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun HealthProfileDialog(
    sensitiveAlertsEnabled: Boolean,
    onSensitiveAlertsChange: (Boolean) -> Unit,
    selectedAgeGroup: String,
    onAgeGroupChange: (String) -> Unit,
    selectedConditions: MutableList<String>,
    otherText: String,
    onOtherTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val ageGroups     = listOf("18歲以下", "18-64歲", "65歲以上")
    val conditionList = listOf("氣喘", "心血管疾病", "懷孕中", "過敏", "呼吸道疾病", "高血壓")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgMain,
        title = { Text("個人健康檔案", fontWeight = FontWeight.Bold, color = TextDark) },
        text = {
            // 可捲動：小螢幕或系統字體放大時，內容超出對話框高度仍可完整檢視
            Column(
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "這份資料儲存於你的裝置，用來讓 AI 顧問與首頁建議更貼近你的情況。" +
                        "使用 AI 建議時會即時傳送至伺服器生成建議，不會保存。",
                    color = TextGray, fontSize = 12.sp, lineHeight = 18.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                HealthFieldLabel("年齡層")
                SingleSelectChipRow(ageGroups, selectedAgeGroup, onAgeGroupChange)

                Spacer(Modifier.height(16.dp))

                HealthFieldLabel("生理狀態與病史")
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    conditionList.forEach { condition ->
                        FilterChip(
                            selected = selectedConditions.contains(condition),
                            onClick = {
                                if (selectedConditions.contains(condition))
                                    selectedConditions.remove(condition)
                                else
                                    selectedConditions.add(condition)
                            },
                            label = { Text(condition, fontSize = 13.sp) },
                            colors = healthChipColors()
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                HealthFieldLabel("其他說明")
                OutlinedTextField(
                    value = otherText,
                    onValueChange = onOtherTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    placeholder = { Text("如長期服用藥物、特殊病史等", color = TextGray, fontSize = 14.sp) },
                    colors = healthTextFieldColors(),
                    minLines = 2,
                    maxLines = 4
                )

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = DividerColor)
                Spacer(Modifier.height(12.dp))

                // 把健康屬性送到伺服器的明確同意就在這裡——使用者一邊看著自己
                // 填的病史、一邊決定要不要分享，比在開場的彈窗上按同意有意義。
                HealthFieldLabel("依健康狀況發送警示")
                Text(
                    "開啟後，以上資料會傳送並保存於伺服器，" +
                        "讓空氣品質變差時能優先提醒你。關閉時伺服器會一併刪除。",
                    color = TextGray, fontSize = 12.sp, lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                )
                SettingSwitchRow(
                    label = if (sensitiveAlertsEnabled) "已開啟" else "未開啟",
                    checked = sensitiveAlertsEnabled,
                    onCheckedChange = onSensitiveAlertsChange
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text("儲存", color = OrangeMain) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextGray) }
        }
    )
}

// ── 常用地點 Dialog ──────────────────────────────────────────────────────────

@Composable
private fun LocationsDialog(
    favLocations: List<FavoriteLocation>,
    onDismiss: () -> Unit,
    onAddClick: () -> Unit,
    onItemClick: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgMain,
        title = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("常用地點", fontWeight = FontWeight.Bold, color = TextDark)
                TextButton(onClick = onAddClick) {
                    Text("＋ 新增", color = OrangeMain, fontSize = 14.sp)
                }
            }
        },
        text = {
            val filled = favLocations.filter { it.name.isNotEmpty() && it.address.isNotEmpty() }
            if (filled.isEmpty()) {
                Text(
                    "尚未新增常用地點",
                    color = TextGray, fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 14.dp)
                )
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    filled.forEachIndexed { idx, fav ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onItemClick(favLocations.indexOf(fav)) }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(fav.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextDark)
                                Text(fav.address, fontSize = 12.sp, color = TextGray)
                            }
                            Text("›", fontSize = 18.sp, color = TextGray)
                        }
                        if (idx < filled.size - 1) HorizontalDivider(color = DividerColor)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("關閉", color = OrangeMain) }
        }
    )
}

// ── 輔助 Composable ────────────────────────────────────────────────────────

@Composable
private fun HealthFieldLabel(text: String) {
    Text(
        text,
        color = TextDark,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SingleSelectChipRow(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    // FlowRow：小螢幕或字體放大時放不下就換行，避免 chip 被壓成直排
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { opt ->
            FilterChip(
                selected = selected == opt,
                onClick  = { onSelect(opt) },
                label    = { Text(opt, fontSize = 13.sp) },
                colors   = healthChipColors()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun healthChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = OrangeMain.copy(alpha = 0.18f),
    selectedLabelColor     = OrangeMain
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DailyNotificationTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgMain,
        title = { Text("選擇通知時間", fontWeight = FontWeight.Bold, color = TextDark) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text("確定", color = OrangeMain)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = TextGray) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun healthTextFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = BgMain,
    focusedContainerColor   = BgMain,
    unfocusedBorderColor    = DividerColor,
    focusedBorderColor      = OrangeMain,
)

// ── 通用區塊元件 ───────────────────────────────────────────────────────────

@Composable
fun SettingSection(title: String?, content: @Composable ColumnScope.() -> Unit) {
    if (title != null) {
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextMid)
        Spacer(Modifier.height(8.dp))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardWhite)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        content = content
    )
}

@Composable
fun SettingLinkRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextDark, fontSize = 17.sp)
        Text("›", color = TextGray, fontSize = 23.sp, fontWeight = FontWeight.Light)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextDark, fontSize = 16.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = White,
                checkedTrackColor = OrangeMain,
            )
        )
    }
}
