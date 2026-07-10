package com.example.airquality

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.airquality.ui.theme.*
import kotlinx.coroutines.launch

private data class FavLocation(val name: String, val address: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("health_profile", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    // ── 通知設定 state ────────────────────────────────────────────────────
    val notifPrefs = remember { context.getSharedPreferences("notification_settings", Context.MODE_PRIVATE) }
    var dailyNotificationEnabled by remember {
        mutableStateOf(notifPrefs.getBoolean("daily_enabled", false))
    }
    var dailyHour   by remember { mutableIntStateOf(notifPrefs.getInt("daily_hour", 8)) }
    var dailyMinute by remember { mutableIntStateOf(notifPrefs.getInt("daily_minute", 0)) }
    var showTimePicker by remember { mutableStateOf(false) }

    fun syncDailyNotification(enabled: Boolean, hour: Int, minute: Int) {
        val token = TokenManager.getToken(context) ?: return
        scope.launch {
            try {
                RetrofitClient.apiService.setDailyNotification(
                    DailyNotificationRequest(token = token, enabled = enabled, hour = hour, minute = minute)
                )
            } catch (_: Exception) {}
        }
    }

    // ── 健康檔案 state（從 SharedPreferences 載入）────────────────────────
    var selectedAgeGroup  by remember { mutableStateOf(prefs.getString("health_age_group", "18-64歲") ?: "18-64歲") }
    var otherText         by remember { mutableStateOf(prefs.getString("health_other",     "") ?: "") }

    val savedConditions = prefs.getString("health_conditions", "") ?: ""
    val selectedConditions = remember {
        mutableStateListOf<String>().also { list ->
            if (savedConditions.isNotEmpty()) list.addAll(savedConditions.split(","))
        }
    }
    var showHealthDialog by remember { mutableStateOf(false) }

    // ── 常用地點 state ────────────────────────────────────────────────────────
    val favLocations = remember {
        mutableStateListOf<FavLocation>().also { list ->
            val count = prefs.getInt("fav_count", 0)
            (1..count).forEach { i ->
                val name    = prefs.getString("fav_${i}_name",    "") ?: ""
                val address = prefs.getString("fav_${i}_address", "") ?: ""
                if (name.isNotEmpty() || address.isNotEmpty())
                    list.add(FavLocation(name, address))
            }
        }
    }
    var showLocationsDialog by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var editName     by remember { mutableStateOf("") }
    var editAddress  by remember { mutableStateOf("") }

    // ── Snackbar ──────────────────────────────────────────────────────────
    val snackbarHostState = remember { SnackbarHostState() }

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

            // ── 主內容（不捲動，僅精簡列表 + 彈跳視窗）───────────────────
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
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
                        onCheckedChange = { checked ->
                            dailyNotificationEnabled = checked
                            notifPrefs.edit().putBoolean("daily_enabled", checked).apply()
                            syncDailyNotification(checked, dailyHour, dailyMinute)
                        }
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
                    onClick = {
                        val token = TokenManager.getToken(context)
                        if (token == null) {
                            scope.launch { snackbarHostState.showSnackbar("尚未取得裝置 Token，請稍後再試") }
                        } else {
                            scope.launch {
                                val message = try {
                                    val resp = RetrofitClient.apiService.testDailyNotification(
                                        DailyNotificationTestRequest(token = token)
                                    )
                                    if (resp.status == "success")
                                        "✅ 測試通知已發送（${resp.county} AQI ${resp.aqi}）"
                                    else
                                        "⚠️ ${resp.message ?: "發送失敗"}"
                                } catch (e: Exception) {
                                    "⚠️ 發送失敗：${e.message}"
                                }
                                snackbarHostState.showSnackbar(message)
                            }
                        }
                    },
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
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }

    // ── 個人健康檔案 彈跳視窗 ───────────────────────────────────────────────
    if (showHealthDialog) {
        HealthProfileDialog(
            selectedAgeGroup   = selectedAgeGroup,
            onAgeGroupChange   = { selectedAgeGroup = it },
            selectedConditions = selectedConditions,
            otherText          = otherText,
            onOtherTextChange  = { otherText = it },
            onDismiss          = { showHealthDialog = false },
            onSave = {
                prefs.edit()
                    .putString("health_age_group",  selectedAgeGroup)
                    .putString("health_conditions", selectedConditions.joinToString(","))
                    .putString("health_other",      otherText)
                    .apply()
                showHealthDialog = false
                scope.launch { snackbarHostState.showSnackbar("✅ 健康檔案已儲存於本機") }
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
                    val newFav = FavLocation(editName.trim(), editAddress.trim())
                    if (isNew) favLocations.add(newFav)
                    else favLocations[i] = newFav
                    // 重寫所有地點到 prefs
                    val editor = prefs.edit()
                    favLocations.forEachIndexed { idx, f ->
                        editor.putString("fav_${idx + 1}_name",    f.name)
                        editor.putString("fav_${idx + 1}_address", f.address)
                    }
                    editor.putInt("fav_count", favLocations.size).apply()
                    editingIndex = null
                }) { Text("儲存", color = OrangeMain) }
            },
            dismissButton = {
                Row {
                    if (!isNew) {
                        TextButton(onClick = {
                            val i = editingIndex ?: return@TextButton
                            favLocations.removeAt(i)
                            val editor = prefs.edit()
                            favLocations.forEachIndexed { idx, f ->
                                editor.putString("fav_${idx + 1}_name",    f.name)
                                editor.putString("fav_${idx + 1}_address", f.address)
                            }
                            editor.putInt("fav_count", favLocations.size).apply()
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
                dailyHour = hour
                dailyMinute = minute
                notifPrefs.edit()
                    .putInt("daily_hour", hour)
                    .putInt("daily_minute", minute)
                    .apply()
                syncDailyNotification(true, hour, minute)
                showTimePicker = false
            }
        )
    }
}

// ── 個人健康檔案 Dialog ──────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun HealthProfileDialog(
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
            Column {
                Text(
                    "此資料將用於 AI (RAG) 分析，提供個人化健康建議",
                    color = TextGray, fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
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
    favLocations: List<FavLocation>,
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
                Column {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SingleSelectChipRow(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
